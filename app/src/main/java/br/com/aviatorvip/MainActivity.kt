
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
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var entrada: EditText
    
private lateinit var historico: LinearLayout
    private lateinit var painelEstrategia: TextView
    private lateinit var painelEstatisticas: TextView
    private lateinit var quadroPadroes: LinearLayout
    private lateinit var quadroEstrategias: LinearLayout
    private lateinit var gradeResultados: GridLayout
    private lateinit var campoQuantidade: EditText
    private lateinit var campoMinimo: EditText
    private lateinit var campoMaximo: EditText
    private lateinit var seletorCor: Spinner
    private var filtroRapido = "Todos"
    
    private val resultados = mutableListOf<Double>()
    private val horarios = mutableListOf<String>()
    private val marcasTempo = mutableListOf<Long>()
    private var candidatoOCR: Double? = null
    private var leiturasDoCandidato = 0 
    private var rodadaRegistrada = false
    private var historicoOCRAnterior = emptyList<Double>()
private var primeiraLeituraOCR = true

    private val padrao = Regex("""(?i)(\d{1,5}(?:[.,]\d{1,2})?)\s*x""")

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val texto = intent?.getStringExtra(
                ScreenCaptureService.EXTRA_TEXT
            ).orEmpty()
            processarOCR(texto)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val salvos = getSharedPreferences("aviator", MODE_PRIVATE)
            .getString("resultados", "") ?: ""

        if (salvos.isNotEmpty()) {
            salvos.split(",").forEach {
                it.toDoubleOrNull()?.let(resultados::add)
            }
        }
        val horariosSalvos = getSharedPreferences("aviator", MODE_PRIVATE)
            .getString("horarios_resultados", "") ?: ""
        if (horariosSalvos.isNotEmpty()) horarios.addAll(horariosSalvos.split("|"))
        while (horarios.size < resultados.size) horarios.add("--:--:--")
        val temposSalvos = getSharedPreferences("aviator", MODE_PRIVATE)
            .getString("tempos_resultados", "") ?: ""
        if (temposSalvos.isNotEmpty()) temposSalvos.split(",").forEach {
            it.toLongOrNull()?.let(marcasTempo::add)
        }
        while (marcasTempo.size < resultados.size) marcasTempo.add(0L)

        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
            setBackgroundColor(Color.rgb(15, 20, 32))
        }

        val topo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 22, 18, 22)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(28, 35, 65), Color.rgb(65, 24, 75), Color.rgb(24, 35, 65))
            ).apply {
                cornerRadius = 24f
                setStroke(1, Color.rgb(190, 55, 150))
            }
        }
        val titulo = TextView(this).apply {
            text = "AVIATOR VIP"
            textSize = 28f
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(255, 90, 185))
        }
        val subtitulo = TextView(this).apply {
            text = "ANALYZER  •  PAINEL INTELIGENTE"
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            setTextColor(Color.rgb(190, 205, 230))
        }
        topo.addView(titulo)
        topo.addView(subtitulo)
        tela.addView(topo, margem())

        val abrirJogo = Button(this).apply {
            text = "🌐  ABRIR APOSTABOOM"
            setOnClickListener {
                try {
                    val url = android.net.Uri.parse("https://www.apostaboom.bet/casino/spribe/aviator-vip")
                    val intent = Intent(Intent.ACTION_VIEW, url)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Não foi possível abrir a ApostaBoom. Verifique se o Chrome está instalado.", Toast.LENGTH_LONG).show()
                }
            }
            estilizarBotao(this, Color.rgb(45, 65, 105), Color.rgb(100, 175, 255))
        }
        tela.addView(abrirJogo)

        val iniciar = Button(this).apply {
            text = "▶  INICIAR CAPTURA OCR"
            setOnClickListener { solicitarCaptura() }
            estilizarBotao(this, Color.rgb(15, 125, 115), Color.rgb(75, 235, 205))
        }
        tela.addView(iniciar)

        val parar = Button(this).apply {
            text = "■  PARAR CAPTURA"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
                status.text = "Captura parada."
            }
            estilizarBotao(this, Color.rgb(75, 35, 65), Color.rgb(230, 85, 155))
        }
        tela.addView(parar)

        status = TextView(this).apply {
            text = "Aguardando captura..."
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 18, 0, 18)
        }
        tela.addView(status)


        gradeResultados = GridLayout(this).apply {
            columnCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            setPadding(6, 8, 6, 8)
            setBackgroundColor(Color.rgb(20, 28, 45))
        }
        tela.addView(
            gradeResultados,
            LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12
            }
        )

        painelEstrategia = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(16, 18, 16, 18)
            background = cartao(Color.rgb(27, 35, 58))
        }
        tela.addView(painelEstrategia, margem())

        val tituloPadroes = TextView(this).apply {
            text = "PADRÕES IDENTIFICADOS"
            textSize = 14f
            setTextColor(Color.rgb(100, 205, 255))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(4, 18, 4, 8)
        }
        tela.addView(tituloPadroes)
        quadroPadroes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            background = cartao(Color.rgb(12, 19, 33))
        }
        tela.addView(quadroPadroes, margem())

        painelEstatisticas = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(16, 16, 16, 16)
            background = cartao(Color.rgb(20, 30, 52))
        }
        tela.addView(painelEstatisticas, margem())

        val cabecalho = TextView(this).apply {
            text = "ESTRATÉGIA ÚNICA • SEQUÊNCIA DE 2 RODADAS"
            textSize = 14f
            setTextColor(Color.rgb(255, 90, 175))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(4, 18, 4, 8)
        }
        tela.addView(cabecalho)
        quadroEstrategias = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            background = cartao(Color.rgb(22, 28, 43))
        }
        tela.addView(quadroEstrategias, margem())

        val rolagemTela = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(10, 14, 24))
            addView(tela)
        }
        setContentView(rolagemTela)
        atualizarHistorico()
        atualizarGrade()
        avaliarEstrategia()
        atualizarBibliotecaPadroes()
    }

    private fun solicitarCaptura() {
        val manager = getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager

        startActivityForResult(
            manager.createScreenCaptureIntent(), 701
        )
    }

    @Deprecated("Compatibilidade com autorização de captura")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 701) {
            if (resultCode == RESULT_OK && data != null) {
                val servico = Intent(this, ScreenCaptureService::class.java)
                servico.action = ScreenCaptureService.ACTION_START
                servico.putExtra(ScreenCaptureService.EXTRA_CODE, resultCode)
                servico.putExtra(ScreenCaptureService.EXTRA_DATA, data)

                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(servico)
                } else {
                    startService(servico)
                }

                status.text = "Captura autorizada. Procurando multiplicadores..."
            } else {
                status.text = "Permissão não concedida."
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filtro = IntentFilter(ScreenCaptureService.ACTION_TEXT)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receptor, filtro, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receptor, filtro)
        }
    }
    override fun onDestroy() {
        try {
            unregisterReceiver(receptor)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
    
  
private fun processarOCR(texto: String) {

    if (texto == "NO_ROUND_END") {
        candidatoOCR = null
        leiturasDoCandidato = 0
        rodadaRegistrada = false
        status.text = "Aguardando o fim da rodada..."
        return
    }

    if (texto == "ROUND_WAIT") {
        status.text = "Lendo multiplicador..."
        return
    }

    if (!texto.startsWith("ROUND_RESULT:")) return

    val valor = texto.substringAfter("ROUND_RESULT:")
        .replace(",", ".")
        .toDoubleOrNull() ?: return

    if (valor < 1.0 || valor > 100000.0) return
    if (rodadaRegistrada) return

    if (candidatoOCR == valor) {
        leiturasDoCandidato++
    } else {
        candidatoOCR = valor
        leiturasDoCandidato = 1
    }

    status.text = "Confirmando resultado: $valor x"

    if (leiturasDoCandidato >= 2) {
        resultados.add(0, valor)
        horarios.add(0, horarioAtual())
        marcasTempo.add(0, System.currentTimeMillis())
        salvar()
        atualizarHistorico()
        atualizarGrade()
        avaliarEstrategia()
        atualizarBibliotecaPadroes()
        rodadaRegistrada = true
        status.text = "Resultado registrado: $valor x"
    }
}

    private fun salvar() {
        getSharedPreferences("aviator", MODE_PRIVATE)
            .edit()
            .putString("resultados", resultados.joinToString(","))
            .putString("horarios_resultados", horarios.joinToString("|"))
            .putString("tempos_resultados", marcasTempo.joinToString(","))
            .apply()
    }

    private fun contarRosasDesdeUltimo40x(): Int {
        // Conta os resultados rosas nas últimas 300 rodadas.
        // Resultados azuis, roxos e 40x+ no meio não zeram a contagem.
        return resultados.take(300).count { it >= 10.0 && it < 40.0 }
    }
private fun avaliarEstrategia() {
        atualizarPainelEstrategia()
        atualizarPainelVisual()
    }
private fun atualizarPainelVisual() {
        if (!::painelEstatisticas.isInitialized || !::quadroEstrategias.isInitialized) return
        val j20 = resultados.take(20)
        val j50 = resultados.take(50)
        val j100 = resultados.take(100)
        fun resumo(lista: List<Double>): String {
            if (lista.isEmpty()) return "sem dados"
            val b = lista.count { it < 2.0 }
            val m = lista.count { it >= 2.0 && it < 5.0 }
            val a = lista.count { it >= 5.0 && it < 10.0 }
            val h = lista.count { it >= 10.0 }
            return "1–1,99x: $b | 2–4,99x: $m | 5–9,99x: $a | 10x+: $h"
        }
        painelEstatisticas.text = "DISTRIBUIÇÃO DO HISTÓRICO\\n" +
            "Últimas 20: ${j20.size} rodadas\\n${resumo(j20)}\\n\\n" +
            "Últimas 50: ${j50.size} rodadas\\n${resumo(j50)}\\n\\n" +
            "Últimas 100: ${j100.size} rodadas\\n${resumo(j100)}\\n\\n" +
            "Frequências descritivas; não são previsão da próxima rodada."
        quadroEstrategias.removeAllViews()
        val (ocorrencias, acertos) = testarPadraoRecente(2, 2.0)
        val taxa = if (ocorrencias == 0) 0.0 else acertos * 100.0 / ocorrencias
        val categorias = resultados.take(2).asReversed().map { categoriaTexto(it) }
        val sequencia = if (categorias.size == 2) categorias.joinToString(" → ") else "aguardando 2 resultados"
        quadroEstrategias.addView(TextView(this).apply {
            text = "SEQUÊNCIA ATUAL: $sequencia\\n" +
                "O teste procura essa mesma sequência no histórico e verifica se a rodada seguinte passou de 2x.\\n\\n" +
                if (ocorrencias < 20) {
                    "Amostra ainda pequena: $acertos acertos em $ocorrencias ocorrências. São necessários mais dados para avaliar estabilidade."
                } else {
                    "$acertos de $ocorrencias ocorrências • frequência histórica: ${"%.1f".format(Locale.US, taxa)}%"
                } +
                "\\n\\nA frequência é retrospectiva e não representa a chance real da próxima rodada."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(8, 12, 8, 12)
        })
    }

    private fun categoriaTexto(v: Double): String = when {
        v < 2.0 -> "AZUL (<2x)"
        v < 5.0 -> "ROXO (2–4,99x)"
        v < 10.0 -> "ROXO (5–9,99x)"
        v < 20.0 -> "ROSA (10–19,99x)"
        v < 50.0 -> "ROSA (20–49,99x)"
        else -> "ROSA (50x+)"
    }

    private fun atualizarGrade() {
        if (!::gradeResultados.isInitialized) return
        val filtrados = resultados.take(200).withIndex()
        gradeResultados.removeAllViews()
        filtrados.forEach { (indice, valor) ->
            val cor = when {
                valor < 2.0 -> Color.rgb(30, 145, 220)
                valor < 10.0 -> Color.rgb(112, 65, 190)
                valor < 40.0 -> Color.rgb(215, 55, 145)
                else -> Color.rgb(185, 45, 100)
            }
            val horario = horarios.getOrNull(indice) ?: "--:--:--"
            val multiplicadorTexto = "%.2fx".format(Locale.US, valor)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(2, 4, 2, 4)
                background = GradientDrawable().apply {
                    setColor(cor)
                    setStroke(1, Color.rgb(30, 35, 52))
                    cornerRadius = 8f
                }
                val multiplicador = TextView(this@MainActivity).apply {
                    text = multiplicadorTexto
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.WHITE)
                }
                val hora = TextView(this@MainActivity).apply {
                    text = horario
                    gravity = Gravity.CENTER
                    textSize = 9f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.BLACK)
                    visibility = android.view.View.VISIBLE
                }
                addView(multiplicador)
                addView(hora)
                contentDescription = "Horário local da rodada"
            }
            val larguraTela = resources.displayMetrics.widthPixels
            val larguraCelula = (larguraTela - (12 * resources.displayMetrics.density).toInt()) / 4
            val lp = GridLayout.LayoutParams().apply {
                width = larguraCelula
                height = (48 * resources.displayMetrics.density).toInt()
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED)
                setMargins(2, 3, 2, 3)
            }
            gradeResultados.addView(item, lp)
        }
    }

    private fun horarioAtual(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun estilizarBotao(botao: Button, corFundo: Int, corBorda: Int) {
        botao.setTextColor(Color.WHITE)
        botao.textSize = 14f
        botao.setTypeface(null, android.graphics.Typeface.BOLD)
        botao.isAllCaps = false
        botao.elevation = 8f
        botao.minimumHeight = 58
        botao.background = GradientDrawable().apply {
            setColor(corFundo)
            setStroke(2, corBorda)
            cornerRadius = 18f
        }
    }

    private fun cartao(cor: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(cor)
            setStroke(1, Color.rgb(65, 80, 115))
            cornerRadius = 18f
        }
    }

    private fun margem(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 12
        }
    }
private fun atualizarPainelEstrategia() {
        if (!::painelEstrategia.isInitialized) return
        painelEstrategia.textSize = 15f
        painelEstrategia.text =
            "ESTRATÉGIA ÚNICA — DUAS RODADAS\\n" +
            "Objetivo estatístico: medir quantas vezes a mesma sequência de duas categorias foi seguida por um resultado acima de 2x.\\n\\n" +
            "Rodadas armazenadas: ${resultados.size}\\n" +
            "Somente este teste ficará ativo. Nenhuma entrada automática é feita."
    }

    private fun atualizarBibliotecaPadroes() {
        if (!::quadroPadroes.isInitialized) return
        quadroPadroes.removeAllViews()
        quadroPadroes.addView(TextView(this).apply {
            text = "ESTRATÉGIA ATIVA\\nSequência de 2 rodadas → mede a frequência histórica de resultado seguinte acima de 2x. Outros detectores foram retirados desta tela."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        })
    }
private fun analisarCicloCronologico(): String {
        return "Análise cronológica antiga desativada. O motor novo compara sequências e resultados posteriores."
    }

    private fun atualizarHistorico() {
        // A faixa horizontal antiga foi removida; os resultados ficam na grade colorida.
    }

    private fun testarPadraoRecente(tamanho: Int, alvo: Double): Pair<Int, Int> {
        if (resultados.size <= tamanho + 1) return Pair(0, 0)
        fun categoria(v: Double): Int = when {
            v < 2.0 -> 0
            v < 5.0 -> 1
            v < 10.0 -> 2
            v < 20.0 -> 3
            v < 50.0 -> 4
            else -> 5
        }
        val atual = (0 until tamanho).map { categoria(resultados[tamanho - 1 - it]) }
        var ocorrencias = 0
        var acertos = 0
        for (inicio in tamanho + 1 until resultados.size) {
            var igual = true
            for (k in 0 until tamanho) {
                if (categoria(resultados[inicio - tamanho + k]) != atual[k]) {
                    igual = false
                    break
                }
            }
            if (igual) {
                ocorrencias++
                if (resultados[inicio - tamanho - 1] > alvo) acertos++
            }
        }
        return Pair(ocorrencias, acertos)
    }

}
