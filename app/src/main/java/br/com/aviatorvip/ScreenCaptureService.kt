
package br.com.aviatorvip

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var ultimaLeitura = 0L

    private val reconhecedor by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            parar()
        } else if (intent?.action == ACTION_START) {
            iniciar(intent)
        }
        return START_NOT_STICKY
    }

    private fun iniciar(intent: Intent) {
        criarCanal()

        val notificacao = NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Aviator VIP Analyzer")
            .setContentText("Captura OCR ativa")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(701, notificacao,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(701, notificacao)
        }

        val codigo = intent.getIntExtra(EXTRA_CODE, 0)
        val dados = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }

        if (dados == null) {
            enviar("Autorização inválida.")
            parar()
            return
        }

        val gerente = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager
        projection = gerente.getMediaProjection(codigo, dados)

        val metricas = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metricas)

        worker = HandlerThread("AviatorOCR").also { it.start() }
        val handler = Handler(worker!!.looper)

        reader = ImageReader.newInstance(
            metricas.widthPixels, metricas.heightPixels,
            PixelFormat.RGBA_8888, 2
        )

        reader!!.setOnImageAvailableListener({ fonte ->
            val agora = System.currentTimeMillis()
            if (agora - ultimaLeitura < 1200L) {
                fonte.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            val imagem = fonte.acquireLatestImage()
                ?: return@setOnImageAvailableListener
            ultimaLeitura = agora

            val plano = imagem.planes[0]
            val largura = imagem.width
            val altura = imagem.height
            val pixel = plano.pixelStride
            val margem = plano.rowStride - pixel * largura
            val larguraExtra = largura + margem / pixel

            val bitmap = Bitmap.createBitmap(
                larguraExtra, altura, Bitmap.Config.ARGB_8888
            )
            plano.buffer.rewind()
            bitmap.copyPixelsFromBuffer(plano.buffer)
            imagem.close()

       
val recorte = Bitmap.createBitmap(bitmap, 0, 0, largura, altura)
bitmap.recycle()
            // Realça caracteres claros e separadores decimais pequenos antes do OCR.
            // Mantém a tela inteira para não cortar o resultado nem confundir regiões.
            val ampliada = Bitmap.createBitmap(
                (recorte.width * 2).coerceAtLeast(1),
                (recorte.height * 2).coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val matriz = ColorMatrix(floatArrayOf(
                1.65f, 0f, 0f, 0f, 18f,
                0f, 1.65f, 0f, 0f, 18f,
                0f, 0f, 1.65f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f
            ))
            val tinta = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matriz)
            }
            Canvas(ampliada).drawBitmap(
                recorte,
                null,
                android.graphics.Rect(0, 0, ampliada.width, ampliada.height),
                tinta
            )
            reconhecedor.process(InputImage.fromBitmap(ampliada, 0))
                .addOnSuccessListener { resultado ->
                    val blocos = resultado.textBlocks.sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
                    val marcador = blocos.firstOrNull {
                        it.text.contains("VOOU", ignoreCase = true) ||
                        it.text.contains("PARA LONGE", ignoreCase = true)
                    }

                    val regexMultiplicador = Regex(
                        """(?<!\d)(\d{1,4}[.,]\d{1,2})\s*x\b""",
                        RegexOption.IGNORE_CASE
                    )

                    // AVIATOR: usa o aviso de fim de rodada e lê o resultado junto
                    // dele ou nos blocos logo abaixo.
                    val textoAviator = if (marcador != null) {
                        val topoMarcador = marcador.boundingBox?.top ?: 0
                        blocos.filter { bloco ->
                            val topo = bloco.boundingBox?.top ?: topoMarcador
                            topo >= topoMarcador - 10 &&
                                topo - topoMarcador <= (ampliada.height * 0.30f).toInt()
                        }.joinToString(" ") { it.text }
                    } else ""

                    val numeroAviator = regexMultiplicador.find(textoAviator)?.groupValues?.get(1)

                    if (numeroAviator != null) {
                        enviar("ROUND_RESULT:" + numeroAviator.replace(",", "."))
                    } else {
                        // TOP GUN (e jogos sem a frase do Aviator): procura somente
                        // um multiplicador decimal grande no centro da área do jogo.
                        // Os pequenos resultados do histórico ficam fora desses limites.
                        val candidatoCentral = blocos.mapNotNull { bloco ->
                            val caixa = bloco.boundingBox ?: return@mapNotNull null
                            val centroX = (caixa.left + caixa.right) / 2f
                            val centroY = (caixa.top + caixa.bottom) / 2f
                            val alturaTexto = caixa.height().toFloat()
                            val centralizado = centroX in (ampliada.width * 0.28f)..(ampliada.width * 0.72f)
                            val faixaVertical = centroY in (ampliada.height * 0.18f)..(ampliada.height * 0.58f)
                            val textoGrande = alturaTexto >= ampliada.height * 0.018f
                            if (!centralizado || !faixaVertical || !textoGrande) return@mapNotNull null
                            val numero = regexMultiplicador.find(bloco.text)?.groupValues?.get(1)
                                ?: return@mapNotNull null
                            Triple(numero, alturaTexto, centroY)
                        }.maxByOrNull { it.second }

                        if (candidatoCentral != null) {
                            enviar("ROUND_RESULT:" + candidatoCentral.first.replace(",", "."))
                        } else {
                            enviar("NO_ROUND_END")
                        }
                    }
                }
                .addOnFailureListener { enviar("Erro na leitura OCR.") }
                .addOnCompleteListener { ampliada.recycle(); recorte.recycle() }

        }, handler)

        projection!!.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() { parar() }
            }, handler
        )

        display = projection!!.createVirtualDisplay(
            "AviatorOCR", metricas.widthPixels, metricas.heightPixels,
            metricas.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )

        enviar("Captura iniciada. Volte ao Chrome.")
    }

    private fun enviar(texto: String) {
        sendBroadcast(Intent(ACTION_TEXT)
            .setPackage(packageName)
            .putExtra(EXTRA_TEXT, texto))
    }

    private fun criarCanal() {
        if (Build.VERSION.SDK_INT >= 26) {
            val canal = NotificationChannel(
                CANAL, "Captura OCR", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(canal)
        }
    }

    private fun parar() {
        val antiga = projection
        projection = null
        display?.release()
        display = null
        reader?.close()
        reader = null
        antiga?.stop()
        worker?.quitSafely()
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        parar()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "AVIATOR_START"
        const val ACTION_STOP = "AVIATOR_STOP"
        const val ACTION_TEXT = "AVIATOR_TEXT"
        const val EXTRA_CODE = "codigo"
        const val EXTRA_DATA = "dados"
        const val EXTRA_TEXT = "texto"
        private const val CANAL = "aviator_ocr"
    }
}
