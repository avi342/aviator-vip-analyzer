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
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var contadorCasas: TextView
    private lateinit var relogio: TextClock
    private lateinit var painelFrequencia: TextView
    private lateinit var painelReacoes: TextView
    private var reacoesExpandida = false
    private data class Reacao(val alvo: Double, val antes: List<Double>, val depois: MutableList<Double>)
    private val reacoes = mutableListOf<Reacao>()
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

        carregarReacoes()

        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(4, 4, 4, 4)
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
            LinearLayout.LayoutParams(0, dp(20), 1f).apply { marginEnd = dp(2) })
        controles.addView(botao("■ PARAR", Color.rgb(95, 35, 55)) {
            stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
            status.text = "Captura parada"
        }, LinearLayout.LayoutParams(0, dp(20), 1f).apply { marginStart = dp(2) })
        topo.addView(controles, LinearLayout.LayoutParams(0, dp(22), 1f))

        relogio = TextClock(this).apply {
            format12Hour = "HH:mm:ss"
            format24Hour = "HH:mm:ss"
            timeZone = java.util.TimeZone.getDefault().id
            textSize = 12f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        topo.addView(relogio, LinearLayout.LayoutParams(dp(64), dp(28)).apply { marginStart = dp(5) })
        tela.addView(topo)

        status = TextView(this).apply {
            text = "Aguardando captura OCR..."
            textSize = 9f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.LTGRAY)
            setPadding(2, dp(2), 2, dp(2))
            maxLines = 1
        }
        tela.addView(status, LinearLayout.LayoutParams(-1, dp(18)))

        contadorCasas = TextView(this).apply {
            text = "Casas após 10x: --"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 105, 190))
            setTypeface(null, Typeface.BOLD)
            setPadding(1, dp(1), 1, dp(1))
            background = cartao()
        }
        tela.addView(contadorCasas, LinearLayout.LayoutParams(-1, dp(20)).apply { topMargin = dp(1) })

        painelFrequencia = TextView(this).apply {
            textSize = 9f
            setTextColor(Color.WHITE)
            setPadding(dp(5), dp(3), dp(5), dp(3))
            typeface = Typeface.MONOSPACE
            background = cartao()
        }
        tela.addView(painelFrequencia, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        val botaoReacoes = botao("REGISTRO DE REAÇÕES  ▼", Color.rgb(35, 65, 75)) {
            reacoesExpandida = !reacoesExpandida
            painelReacoes.visibility = if (reacoesExpandida) android.view.View.VISIBLE else android.view.View.GONE
        }
        tela.addView(botaoReacoes, LinearLayout.LayoutParams(-1, dp(27)).apply { topMargin = dp(3) })
        painelReacoes = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(dp(6), dp(5), dp(6), dp(5))
            typeface = Typeface.MONOSPACE
            background = cartao()
            visibility = android.view.View.GONE
        }
        tela.addView(painelReacoes, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        setContentView(tela)
        atualizarContador()
        atualizarFrequencia()
        atualizarReacoes()
    }

    private fun botao(rotulo: String, cor: Int, acao: () -> Unit) = Button(this).apply {
        text = rotulo
        textSize = 10f
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(2), 0, dp(2), 0)
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
            registrarReacao(valor)
            resultados.add(0, valor)
            if (resultados.size > 2000) resultados.removeAt(resultados.lastIndex)
            getSharedPreferences("aviator", MODE_PRIVATE).edit()
                .putString("resultados", resultados.joinToString(",")).apply()
            rodadaRegistrada = true
            status.text = "Registrado: " + String.format(Locale.US, "%.2fx", valor)
            atualizarContador()
            atualizarFrequencia()
            atualizarReacoes()
        }
    }

    private fun carregarReacoes() {
        val bruto = getSharedPreferences("aviator", MODE_PRIVATE).getString("reacoes_3x", "[]") ?: "[]"
        try {
            val array = JSONArray(bruto)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val antesJson = obj.getJSONArray("antes")
                val depoisJson = obj.getJSONArray("depois")
                val antes = (0 until antesJson.length()).map { antesJson.getDouble(it) }
                val depois = (0 until depoisJson.length()).map { depoisJson.getDouble(it) }.toMutableList()
                reacoes.add(Reacao(obj.getDouble("alvo"), antes, depois))
            }
        } catch (_: Exception) { reacoes.clear() }
    }

    private fun salvarReacoes() {
        val array = JSONArray()
        reacoes.forEach { reacao ->
            val obj = JSONObject()
            obj.put("alvo", reacao.alvo)
            obj.put("antes", JSONArray(reacao.antes))
            obj.put("depois", JSONArray(reacao.depois))
            array.put(obj)
        }
        getSharedPreferences("aviator", MODE_PRIVATE).edit().putString("reacoes_3x", array.toString()).apply()
    }

    private fun registrarReacao(valor: Double) {
        reacoes.forEach { if (it.depois.size < 5) it.depois.add(valor) }
        if (valor >= 3.0) {
            reacoes.add(Reacao(valor, resultados.take(5), mutableListOf()))
            if (reacoes.size > 100) reacoes.removeAt(0)
        }
        salvarReacoes()
    }

    private fun assinatura(janela: List<Double>): IntArray = intArrayOf(
        janela.count { it < 2.0 },
        janela.count { it >= 2.0 && it < 3.0 },
        janela.count { it >= 3.0 }
    )

    private fun atualizarReacoes() {
        if (!::painelReacoes.isInitialized) return
        val historico = resultados.asReversed()
        if (historico.size < 6) {
            painelReacoes.text = "RADAR 3x+ • aguardando pelo menos 6 rodadas."
            return
        }
        val atual = historico.takeLast(5)
        val assinaturaAtual = assinatura(atual)
        var semelhantes = 0
        var acertos = 0
        val tolerancia = 2
        for (i in 0 until historico.size - 5) {
            val janela = historico.subList(i, i + 5)
            val assinaturaHistorica = assinatura(janela)
            val distancia = assinaturaAtual.indices.sumOf { k ->
                kotlin.math.abs(assinaturaAtual[k] - assinaturaHistorica[k])
            }
            if (distancia <= tolerancia) {
                semelhantes++
                if (historico[i + 5] >= 3.0) acertos++
            }
        }
        val percentual = if (semelhantes == 0) 0 else (acertos * 100 / semelhantes)
        val linhas = mutableListOf<String>()
        linhas += "RADAR ANTECIPADO • ALVO 3x+"
        linhas += "ÚLTIMAS 5 CASAS: " + atual.joinToString("  ") { String.format(Locale.US, "%.2f", it) + "x" }
        linhas += "PERFIL: <2x ${assinaturaAtual[0]}  |  2–2,99x ${assinaturaAtual[1]}  |  3x+ ${assinaturaAtual[2]}"
        linhas += "--------------------------------------"
        linhas += if (semelhantes == 0) {
            "Ainda não encontrei janelas históricas parecidas."
        } else {
            "REAÇÕES SEMELHANTES: $semelhantes • SEGUIDAS DE 3x+: $acertos ($percentual%)"
        }
        linhas += when {
            semelhantes < 10 -> "AMOSTRA INSUFICIENTE • aguardando mais casos"
            percentual >= 60 -> "FREQUÊNCIA HISTÓRICA MAIS ALTA nesta amostra"
            percentual >= 40 -> "FREQUÊNCIA HISTÓRICA INTERMEDIÁRIA"
            else -> "FREQUÊNCIA HISTÓRICA MAIS BAIXA nesta amostra"
        }
        linhas += "--------------------------------------"
        linhas += "REGISTROS DE 3x+: ${reacoes.size}"
        reacoes.takeLast(3).asReversed().forEach { r ->
            linhas += "${String.format(Locale.US, "%.2f", r.alvo)}x | antes: " +
                r.antes.asReversed().joinToString(" ") { String.format(Locale.US, "%.2f", it) + "x" } +
                " | depois: ${r.depois.size}/5"
        }
        linhas += "Comparação por quantidade em cada faixa; histórico não prevê a próxima rodada."
        painelReacoes.text = linhas.joinToString("\\n")
    }

    private fun atualizarFrequencia() {
        if (!::painelFrequencia.isInitialized) return
        val cronologico = resultados.asReversed()
        fun analisar(casas: Int): String {
            if (cronologico.size <= casas) return "${casas} CASAS: aguardando histórico"
            val padraoAtual = cronologico.takeLast(casas)
            var casos = 0
            var acertos = 0
            // Compara sequências históricas completas, sem usar a rodada atual como resultado futuro.
            for (i in 0 until cronologico.size - casas) {
                val janela = cronologico.subList(i, i + casas)
                val semelhante = janela.indices.all { k ->
                    kotlin.math.abs(janela[k] - padraoAtual[k]) <=
                        maxOf(0.75, padraoAtual[k] * 0.35)
                }
                if (semelhante) {
                    casos++
                    if (cronologico[i + casas] >= 2.0) acertos++
                }
            }
            val pct = if (casos == 0) 0 else acertos * 100 / casos
            val estado = if (casos < 10) "poucos casos" else "amostra"
            return "${casas} CASAS • ${acertos}/${casos} após 2x+ • ${pct}% • ${estado}"
        }
        painelFrequencia.text = "ANÁLISE CURTA (PRÓXIMA 2x+)\n" +
            analisar(2) + "\n" + analisar(3)
    }

    private fun atualizarContador() {
        val indice = resultados.indexOfFirst { it >= 10.0 }
        contadorCasas.text = if (indice < 0) "CASAS APÓS 10x: --"
            else "CASAS APÓS 10x: $indice"
    }
}
