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
    private lateinit var painelFrequencia: TextView
    private var janelaFrequencia = 100
    private var frequenciaExpandida = true
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
        val topo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val controles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controles.addView(botao("▶ OCR", Color.rgb(15, 125, 115)) { solicitarCaptura() },
            LinearLayout.LayoutParams(dp(78), dp(25)).apply { marginEnd = dp(3) })
        controles.addView(botao("■ PARAR", Color.rgb(95, 35, 55)) {
            stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
            status.text = "Captura parada"
        }, LinearLayout.LayoutParams(dp(78), dp(25)).apply { marginStart = dp(3) })
        topo.addView(controles, LinearLayout.LayoutParams(0, dp(26), 1f))

        relogio = TextClock(this).apply {
            format12Hour = "HH:mm:ss"
            format24Hour = "HH:mm:ss"
            timeZone = java.util.TimeZone.getDefault().id
            textSize = 14f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        topo.addView(relogio, LinearLayout.LayoutParams(dp(72), dp(32)).apply { marginStart = dp(5) })
        tela.addView(topo)

        status = TextView(this).apply {
            text = "Aguardando captura OCR..."
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.LTGRAY)
            setPadding(2, dp(2), 2, dp(2))
            maxLines = 1
        }
        tela.addView(status, LinearLayout.LayoutParams(-1, dp(22)))

        contadorCasas = TextView(this).apply {
            text = "Casas após 10x: --"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 105, 190))
            setTypeface(null, Typeface.BOLD)
            setPadding(1, dp(1), 1, dp(1))
            background = cartao()
        }
        tela.addView(contadorCasas, LinearLayout.LayoutParams(-1, dp(23)).apply { topMargin = dp(2) })

        val seletorJanela = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf(100, 300, 500, 2000).forEach { quantidade ->
            val b = botao(quantidade.toString(), Color.rgb(35, 45, 70)) {
                janelaFrequencia = quantidade
                atualizarFrequencia()
            }
            seletorJanela.addView(b, LinearLayout.LayoutParams(0, dp(27), 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }
        tela.addView(seletorJanela, LinearLayout.LayoutParams(-1, dp(27)).apply { topMargin = dp(3) })

        val botaoFrequencia = botao("LEITOR DE FREQUÊNCIA  ▼", Color.rgb(35, 45, 70)) {
            frequenciaExpandida = !frequenciaExpandida
            painelFrequencia.visibility = if (frequenciaExpandida) android.view.View.VISIBLE else android.view.View.GONE
        }
        tela.addView(botaoFrequencia, LinearLayout.LayoutParams(-1, dp(27)).apply { topMargin = dp(2) })

        painelFrequencia = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(dp(6), dp(5), dp(6), dp(5))
            typeface = Typeface.MONOSPACE
            background = cartao()
        }
        tela.addView(painelFrequencia, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        setContentView(tela)
        atualizarContador()
        atualizarFrequencia()
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
            atualizarFrequencia()
        }
    }

    private fun atualizarFrequencia() {
        if (!::painelFrequencia.isInitialized) return
        val amostra = resultados.take(janelaFrequencia)
        val faixas = listOf(
            "1–1,99x" to { v: Double -> v >= 1.0 && v < 2.0 },
            "2–2,99x" to { v: Double -> v >= 2.0 && v < 3.0 },
            "3–4,99x" to { v: Double -> v >= 3.0 && v < 5.0 },
            "5–9,99x" to { v: Double -> v >= 5.0 && v < 10.0 },
            "10x+" to { v: Double -> v >= 10.0 }
        )
        val cronologico = amostra.asReversed()
        val totalGeral = (cronologico.size - 1).coerceAtLeast(0)
        val metas = doubleArrayOf(2.0, 3.0, 5.0, 10.0)
        val geral = IntArray(4)
        for (i in 0 until totalGeral) metas.forEachIndexed { j, meta ->
            if (cronologico[i + 1] >= meta) geral[j]++
        }
        fun pct(n: Int, total: Int): String =
            if (total == 0) "—" else String.format(Locale.US, "%.0f", n * 100.0 / total)
        val linhas = mutableListOf<String>()
        linhas += "FAIXA       QTD   2x   3x   5x  10x"
        linhas += "MÉDIA GERAL       ${pct(geral[0], totalGeral)}%  ${pct(geral[1], totalGeral)}%  ${pct(geral[2], totalGeral)}%  ${pct(geral[3], totalGeral)}%"
        faixas.forEach { (nome, pertence) ->
            var total = 0
            val acertos = IntArray(4)
            for (i in 0 until totalGeral) if (pertence(cronologico[i])) {
                total++
                metas.forEachIndexed { j, meta -> if (cronologico[i + 1] >= meta) acertos[j]++ }
            }
            linhas += "${nome.padEnd(10)} ${total.toString().padStart(3)}   " +
                metas.indices.joinToString("   ") { j -> if (total == 0) "—" else "${pct(acertos[j], total)}%" }
        }
        painelFrequencia.text =
            "ÚLTIMAS $janelaFrequencia • ${amostra.size} resultados\n" +
            linhas.joinToString("\n") +
            "\n\nToque nos detalhes? Percentuais históricos, não previsões."
    }

    private fun atualizarContador() {
        val indice = resultados.indexOfFirst { it >= 10.0 }
        contadorCasas.text = if (indice < 0) "CASAS APÓS 10x: --"
            else "CASAS APÓS 10x: $indice"
    }
}
