
package br.com.topgunvip

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
            .setContentTitle("Top Gun VIP Analyzer")
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

        worker = HandlerThread("Top GunOCR").also { it.start() }
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
            // Recorta apenas a área central do Top Gun e amplia 3x.
            // Isso dá mais pixels ao separador decimal e exclui a faixa de histórico.
            val roiLeft = (largura * 0.20f).toInt()
            val roiTop = (altura * 0.28f).toInt()
            val roiWidth = (largura * 0.60f).toInt().coerceAtLeast(1)
            val roiHeight = (altura * 0.20f).toInt().coerceAtLeast(1)
            val roi = Bitmap.createBitmap(recorte, roiLeft, roiTop, roiWidth, roiHeight)
            val ampliado = Bitmap.createScaledBitmap(roi, roi.width * 3, roi.height * 3, true)
            roi.recycle()

            reconhecedor.process(InputImage.fromBitmap(ampliado, 0))
                .addOnSuccessListener { resultado ->
                    val candidatos = resultado.textBlocks
                        .flatMap { it.lines }
                        .mapNotNull { linha ->
                            val caixa = linha.boundingBox ?: return@mapNotNull null
                            val centroX = (caixa.left + caixa.right) / 2f
                            val centroY = (caixa.top + caixa.bottom) / 2f
                            val larguraLinha = caixa.width().toFloat()
                            val alturaLinha = caixa.height().toFloat()

                            val noCentroHorizontal =
                                centroX >= ampliado.width * 0.10f && centroX <= ampliado.width * 0.90f
                            val noPainelDoJogo =
                                centroY >= ampliado.height * 0.10f && centroY <= ampliado.height * 0.90f
                            val linhaIsolada =
                                larguraLinha <= ampliado.width * 0.90f &&
                                alturaLinha >= ampliado.height * 0.06f

                            if (!noCentroHorizontal || !noPainelDoJogo || !linhaIsolada) {
                                return@mapNotNull null
                            }

                            // Exige separador decimal: não converte "178x" em 178.00x.
                            val regexCentral = Regex(
                                """(\d{1,4}[.,]\d{1,2})\s*x""",
                                RegexOption.IGNORE_CASE
                            )
                            val numero = regexCentral.find(linha.text)?.groupValues?.get(1)
                                ?: return@mapNotNull null
                            Triple(numero, alturaLinha, centroY)
                        }.maxByOrNull { it.second }

                    if (candidatos != null) {
                        enviar("ROUND_RESULT:" + candidatos.first)
                    } else {
                        enviar("NO_ROUND_END")
                    }
                }
                .addOnFailureListener { enviar("Erro na leitura OCR.") }
                .addOnCompleteListener {
                    ampliado.recycle()
                    recorte.recycle()
                }

        }, handler)

        projection!!.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() { parar() }
            }, handler
        )

        display = projection!!.createVirtualDisplay(
            "Top GunOCR", metricas.widthPixels, metricas.heightPixels,
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
        const val ACTION_START = "TOPGUN_START"
        const val ACTION_STOP = "TOPGUN_STOP"
        const val ACTION_TEXT = "TOPGUN_TEXT"
        const val EXTRA_CODE = "codigo"
        const val EXTRA_DATA = "dados"
        const val EXTRA_TEXT = "texto"
        private const val CANAL = "topgun_ocr"
    }
}
