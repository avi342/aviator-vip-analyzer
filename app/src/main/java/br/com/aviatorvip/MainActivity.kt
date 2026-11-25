package br.com.aviatorvip

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TextClock
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var contadorCasas: TextView
    private lateinit var relogio: TextClock
    private val resultados = mutableListOf<Double>()
    private var candidatoOCR: Double? = null
    private var leiturasDoCandidato = 0
    private var rodadaRegistrada = false

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            processarOCR(intent?.getStringExtra(ScreenCaptureService.EXTRA_TEXT).orEmpty())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSharedPreferences("aviator", MODE_PRIVATE).getString("resultados", "").orEmpty()
            .split(",").filter { it.isNotBlank() }
            .forEach { it.toDoubleOrNull()?.let(resultados::add) }

        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.BLACK)
        }
        val controles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controles.addView(botao("▶ OCR", Color.rgb(15, 125, 115)) { solicitarCaptura() },
            LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) })
        controles.addView(botao("■ PARAR", Color.rgb(95, 35, 55)) {
            stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
            status.text = "Captura parada"
        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(4) })
        tela.addView(controles)

        status = TextView(this).apply {
            text = "Aguardando captura OCR..."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(2, dp(8), 2, dp(8))
        }
        tela.addView(status, LinearLayout.LayoutParams(-1, -2))

        relogio = TextClock(this).apply {
            format12Hour = "HH:mm:ss"
            format24Hour = "HH:mm:ss"
            timeZone = java.util.TimeZone.getDefault().id
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        tela.addView(relogio, LinearLayout.LayoutParams(-1, dp(58)))

        contadorCasas = TextView(this).apply {
            text = "CASAS APÓS 10x: --"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 105, 190))
            setTypeface(null, Typeface.BOLD)
            setPadding(4, dp(14), 4, dp(14))
            background = cartao()
        }
        tela.addView(contadorCasas, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(8) })
        setContentView(tela)
        atualizarContador()
    }

    private fun botao(rotulo: String, cor: Int, acao: () -> Unit) = Button(this).apply {
        text = rotulo
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setOnClickListener { acao() }
        background = GradientDrawable().apply { setColor(cor); cornerRadius = dp(7).toFloat() }
    }

    private fun cartao() = GradientDrawable().apply {
        setColor(Color.rgb(22, 25, 38))
        setStroke(dp(1), Color.rgb(90, 55, 90))
        cornerRadius = dp(10).toFloat()
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    private fun solicitarCaptura() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), 701)
    }

    @Deprecated("Compatibilidade com autorização de captura")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 701) return
        if (resultCode == RESULT_OK && data != null) {
            val servico = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(servico) else startService(servico)
            status.text = "Captura ativa • aguardando resultado"
        } else status.text = "Permissão de captura não concedida"
    }

    override fun onStart() {
        super.onStart()
        val filtro = IntentFilter(ScreenCaptureService.ACTION_TEXT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receptor, filtro, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receptor, filtro)
    }

    override fun onStop() {
        try { unregisterReceiver(receptor) } catch (_: Exception) { }
        super.onStop()
    }

    private fun processarOCR(texto: String) {
        when (texto) {
            "NO_ROUND_END" -> {
                candidatoOCR = null
                leiturasDoCandidato = 0
                rodadaRegistrada = false
                status.text = "Aguardando fim da rodada..."
                return
            }
            "ROUND_WAIT" -> { status.text = "Lendo multiplicador..."; return }
        }
        if (!texto.startsWith("ROUND_RESULT:") || rodadaRegistrada) return
        val valor = texto.substringAfter("ROUND_RESULT:").replace(",", ".").toDoubleOrNull() ?: return
        if (valor < 1.0 || valor > 100000.0) return
        if (candidatoOCR == valor) leiturasDoCandidato++ else {
            candidatoOCR = valor
            leiturasDoCandidato = 1
        }
        status.text = "Confirmando: " + String.format(Locale.US, "%.2fx", valor)
        if (leiturasDoCandidato >= 2) {
            resultados.add(0, valor)
            if (resultados.size > 2000) resultados.removeAt(resultados.lastIndex)
            getSharedPreferences("aviator", MODE_PRIVATE).edit()
                .putString("resultados", resultados.joinToString(",")).apply()
            rodadaRegistrada = true
            status.text = "Registrado: " + String.format(Locale.US, "%.2fx", valor)
            atualizarContador()
        }
    }

    private fun atualizarContador() {
        val indice = resultados.indexOfFirst { it >= 10.0 }
        contadorCasas.text = if (indice < 0) "CASAS APÓS 10x: --"
            else "CASAS APÓS 10x: $indice"
    }
}
