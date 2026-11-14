
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
    private lateinit var contadorDezX: TextView
    private lateinit var relogioDigital: TextClock
    private lateinit var entrada: EditText
    
private lateinit var historico: LinearLayout
    private lateinit var painelEstrategia: TextView
    private lateinit var painelEstatisticas: TextView
    private lateinit var quadroPadroes: LinearLayout
    private lateinit var quadroDezX: LinearLayout
    private lateinit var quadroEstrategias: LinearLayout
    private lateinit var gradeResultados: GridLayout
    private lateinit var botaoHistorico: Button
    private var historicoExpandido = false
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
            setPadding(3, 3, 3, 3)
            setBackgroundColor(Color.BLACK)
        }

        val topo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(2, 2, 2, 2)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(28, 35, 65), Color.rgb(65, 24, 75), Color.rgb(24, 35, 65))
            ).apply {
                cornerRadius = 12f
                setStroke(1, Color.rgb(190, 55, 150))
            }
        }
        val titulo = TextView(this).apply {
            text = "AVIATOR VIP"
            textSize = 10f
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(255, 90, 185))
        }
        val subtitulo = TextView(this).apply {
            text = "ANALYZER  •  PAINEL INTELIGENTE"
            textSize = 6f
            gravity = Gravity.CENTER
            letterSpacing = 0.02f
            setTextColor(Color.rgb(190, 205, 230))
        }
        topo.addView(titulo)
        topo.addView(subtitulo)
        tela.addView(topo, margem())

        val controlesCaptura = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val iniciar = Button(this).apply {
            text = "▶ OCR"
            setOnClickListener { solicitarCaptura() }
            estilizarBotao(this, Color.rgb(15, 125, 115), Color.rgb(75, 235, 205))
            textSize = 8f
            minimumHeight = 0
            minHeight = 0
            minimumWidth = 0
            setPadding(2, 0, 2, 0)
            isAllCaps = false
        }
        val parar = Button(this).apply {
            text = "■ Parar"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
                status.text = "Captura parada."
            }
            estilizarBotao(this, Color.rgb(75, 35, 65), Color.rgb(230, 85, 155))
            textSize = 8f
            minimumHeight = 0
            minHeight = 0
            minimumWidth = 0
            setPadding(2, 0, 2, 0)
            isAllCaps = false
        }
        val alturaControle = (23 * resources.displayMetrics.density).toInt()
        controlesCaptura.addView(iniciar, LinearLayout.LayoutParams(0, alturaControle, 1f).apply { marginEnd = 2 })
        controlesCaptura.addView(parar, LinearLayout.LayoutParams(0, alturaControle, 1f).apply { marginStart = 2 })
        tela.addView(controlesCaptura, LinearLayout.LayoutParams(-1, alturaControle).apply { bottomMargin = 2 })

        val linhaStatus = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status = TextView(this).apply {
            text = "Aguardando captura..."
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
            setTextColor(Color.WHITE)
            setPadding(2, 2, 2, 2)
        }
        contadorDezX = TextView(this).apply {
            text = "10x: --"
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setTextColor(Color.rgb(255, 105, 190))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(2, 2, 4, 2)
            maxLines = 1
        }
        relogioDigital = TextClock(this).apply {
            format12Hour = "HH:mm:ss"
            format24Hour = "HH:mm:ss"
            timeZone = java.util.TimeZone.getDefault().id
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(2, 2, 2, 2)
            contentDescription = "Relógio digital com horas, minutos e segundos"
        }
        val alturaLinhaStatus = (30 * resources.displayMetrics.density).toInt()
        linhaStatus.addView(status, LinearLayout.LayoutParams(0, alturaLinhaStatus, 1f))
        // Coluna central com largura reservada e respiro antes do contador rosa.
        linhaStatus.addView(relogioDigital, LinearLayout.LayoutParams((72 * resources.displayMetrics.density).toInt(), alturaLinhaStatus).apply {
            marginStart = (5 * resources.displayMetrics.density).toInt()
            marginEnd = (9 * resources.displayMetrics.density).toInt()
        })
        linhaStatus.addView(contadorDezX, LinearLayout.LayoutParams(-2, alturaLinhaStatus))
        tela.addView(linhaStatus)


        gradeResultados = GridLayout(this).apply {
            columnCount = 9
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            setPadding(1, 1, 1, 1)
            setBackgroundColor(Color.BLACK)
        }
        tela.addView(
            gradeResultados,
            LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 4
            }
        )
        botaoHistorico = Button(this).apply {
            text = "⌄ VER HISTÓRICO COMPLETO"
            textSize = 9f
            isAllCaps = false
            setTextColor(Color.WHITE)
            minimumHeight = (25 * resources.displayMetrics.density).toInt()
            minHeight = minimumHeight
            minimumWidth = 0
            setPadding(2, 0, 2, 0)
            background = cartao(Color.rgb(25, 31, 49))
            setOnClickListener {
                historicoExpandido = !historicoExpandido
                atualizarGrade()
            }
        }
        tela.addView(botaoHistorico, LinearLayout.LayoutParams(-1, (27 * resources.displayMetrics.density).toInt()).apply { bottomMargin = 3 })


        painelEstrategia = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(16, 18, 16, 18)
            background = cartao(Color.rgb(27, 35, 58))
        }


        val tituloPadroes = TextView(this).apply {
            text = "PADRÕES IDENTIFICADOS"
            textSize = 14f
            setTextColor(Color.rgb(100, 205, 255))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(4, 7, 4, 4)
        }
        // Cabeçalho da central removido para liberar espaço.
        quadroPadroes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 2, 2, 2)
            setBackgroundColor(Color.BLACK)
        }
        tela.addView(quadroPadroes, margem())

        // Título da central especial removido para liberar espaço.
        quadroDezX = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 2, 2, 2)
            setBackgroundColor(Color.BLACK)
        }
        tela.addView(quadroDezX, margem())

        painelEstatisticas = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(16, 16, 16, 16)
            background = cartao(Color.rgb(20, 30, 52))
        }


        val cabecalho = TextView(this).apply {
            text = "ESTRATÉGIA PRINCIPAL • PADRÃO DE 2 RODADAS"
            textSize = 14f
            setTextColor(Color.rgb(255, 90, 175))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(4, 18, 4, 8)
        }

        quadroEstrategias = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            background = cartao(Color.rgb(22, 28, 43))
        }


        val rolagemTela = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.BLACK)
            addView(tela)
        }
        setContentView(rolagemTela)
        atualizarHistorico()
        atualizarContadorDezX()
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
        val formacaoAntesDaRodada = formacaoCompleta(resultados.asReversed())
        resultados.add(0, valor)
        horarios.add(0, horarioAtual())
        marcasTempo.add(0, System.currentTimeMillis())
        atualizarMonitoramento10x(valor, formacaoAntesDaRodada)
        salvar()
        atualizarHistorico()
        atualizarContadorDezX()
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
        atualizarBibliotecaPadroes()
    }

    private fun categoriaTexto(v: Double): String = when {
        v < 2.0 -> "AZUL (<2x)"
        v < 5.0 -> "ROXO (2–4,99x)"
        v < 10.0 -> "ROXO (5–9,99x)"
        v < 20.0 -> "ROSA (10–19,99x)"
        v < 50.0 -> "ROSA (20–49,99x)"
        else -> "ROSA (50x+)"
    }

    // resultados[0] é o mais recente. Para cada ocorrência histórica,
    // comparamos duas rodadas anteriores e avaliamos a rodada seguinte.
    private fun contarOcorrenciasDuasRodadas(): Int {
        if (resultados.size < 3) return 0
        val atualAntiga = categoria(resultados[1])
        val atualRecente = categoria(resultados[0])
        var total = 0
        for (alvo in 1 until resultados.size - 1) {
            if (categoria(resultados[alvo + 1]) == atualRecente &&
                categoria(resultados[alvo + 2]) == atualAntiga) {
                total++
            }
        }
        return total
    }

    private fun contarAcertosDuasRodadas(): Int {
        if (resultados.size < 3) return 0
        val atualAntiga = categoria(resultados[1])
        val atualRecente = categoria(resultados[0])
        var acertos = 0
        for (alvo in 1 until resultados.size - 1) {
            if (categoria(resultados[alvo + 1]) == atualRecente &&
                categoria(resultados[alvo + 2]) == atualAntiga &&
                resultados[alvo] >= 2.0) {
                acertos++
            }
        }
        return acertos
    }

    private fun categoria(v: Double): Int = when {
        v < 2.0 -> 0
        v < 5.0 -> 1
        v < 10.0 -> 2
        v < 20.0 -> 3
        v < 50.0 -> 4
        else -> 5
    }

    private fun atualizarGrade() {
        if (!::gradeResultados.isInitialized) return
        val filtrados = resultados.take(if (historicoExpandido) resultados.size else 18).withIndex()
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
                setPadding(0, 0, 0, 0)
                val multiplicador = TextView(this@MainActivity).apply {
                    text = multiplicadorTexto
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(cor)
                    maxLines = 1
                    setSingleLine(true)
                }
                val hora = TextView(this@MainActivity).apply {
                    text = if (valor >= 10.0) horario else ""
                    gravity = Gravity.CENTER
                    textSize = 8f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    visibility = if (valor >= 10.0) android.view.View.VISIBLE else android.view.View.GONE
                    maxLines = 1
                    setSingleLine(true)
                }
                addView(multiplicador)
                addView(hora)
                contentDescription = "Multiplicador $multiplicadorTexto, horário $horario"
            }
            val densidade = resources.displayMetrics.density
            val larguraDisponivel = if (gradeResultados.width > 0) gradeResultados.width else resources.displayMetrics.widthPixels
            val larguraCelula = ((larguraDisponivel - (12 * densidade).toInt()) / 9).coerceAtLeast(1)
            val lp = GridLayout.LayoutParams().apply {
                width = larguraCelula
                height = (26 * resources.displayMetrics.density).toInt()
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED)
                setMargins(0, 1, 0, 1)
            }
            gradeResultados.addView(item, lp)
        }
        botaoHistorico.text = if (historicoExpandido) "⌃ RECOLHER HISTÓRICO" else "⌄ VER HISTÓRICO COMPLETO"
        botaoHistorico.visibility = if (resultados.size > 18) View.VISIBLE else View.GONE
    }

    private fun atualizarContadorDezX() {
        if (!::contadorDezX.isInitialized) return
        // resultados[0] é a rodada mais recente. O índice do último 10x
        // equivale à quantidade de rodadas/casas desde aquele multiplicador.
        val indice = resultados.indexOfFirst { it >= 10.0 }
        if (indice < 0) {
            contadorDezX.text = "Último 10x: (--) "
            return
        }
        val instante = marcasTempo.getOrNull(indice) ?: 0L
        val casasDesdeDezX = indice
        val tempo = if (instante > 0L) {
            val minutos = ((System.currentTimeMillis() - instante).coerceAtLeast(0L) / 60000L)
            String.format(Locale.getDefault(), "%02d min", minutos)
        } else ""
        val textoContador = "Último 10x: ($casasDesdeDezX) $tempo"
        val inicioContagem = textoContador.indexOf('(')
        val fimContagem = textoContador.indexOf(')', inicioContagem)
        val textoColorido = android.text.SpannableString(textoContador)
        if (inicioContagem >= 0 && fimContagem > inicioContagem) {
            textoColorido.setSpan(
                android.text.style.ForegroundColorSpan(Color.WHITE),
                inicioContagem,
                fimContagem + 1,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        contadorDezX.text = textoColorido
    }

    private fun horarioAtual(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun estilizarBotao(botao: Button, corFundo: Int, corBorda: Int) {
        botao.setTextColor(Color.WHITE)
        botao.textSize = 8f
        botao.setTypeface(null, android.graphics.Typeface.BOLD)
        botao.isAllCaps = false
        botao.elevation = 1f
        botao.minimumHeight = (23 * resources.displayMetrics.density).toInt()
        botao.minHeight = botao.minimumHeight
        botao.minimumWidth = 0
        botao.setPadding(2, 0, 2, 0)
        botao.background = GradientDrawable().apply {
            setColor(corFundo)
            setStroke(1, corBorda)
            cornerRadius = 7f
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
            bottomMargin = 5
        }
    }
private fun atualizarPainelEstrategia() {
        if (!::painelEstrategia.isInitialized) return
        painelEstrategia.textSize = 15f
        painelEstrategia.text =
            "ESTRATÉGIA PRINCIPAL — DUAS RODADAS → PRÓXIMA ≥ 2x\\n" +
            "Rodadas armazenadas: ${resultados.size}\\n" +
            "O aplicativo acompanha somente esta combinação e atualiza a frequência quando chega uma nova rodada.\\n\\n" +
            "O histórico serve para medir ocorrências anteriores; não é garantia de resultado futuro."
    }

    private fun formacaoCompleta(chronologico: List<Double>): Boolean {
        if (chronologico.isEmpty()) return false
        val rosas = chronologico.takeLast(30).count { it >= 10.0 && it < 40.0 }
        if (rosas < 8) return false

        val ultimoDez = chronologico.indexOfLast { it >= 10.0 }
        if (ultimoDez < 0) return false
        val casas = listOf(2, 3, 4, 5, 9, 13)
        val valoresCasas = casas.map { casa ->
            val indice = ultimoDez + casa
            if (indice < chronologico.size) chronologico[indice] else return false
        }
        val baixasEspelho = valoresCasas.count { it < 2.0 }
        if (baixasEspelho < 2) return false

        val janela = chronologico.takeLast(6)
        val baixaSeguidaMedia = janela.windowed(2).any { par ->
            par[0] < 2.0 && par[1] in 3.0..5.99
        }
        return baixaSeguidaMedia
    }

    private fun atualizarMonitoramento10x(valor: Double, formacaoAntes: Boolean) {
        val prefs = getSharedPreferences("aviator", MODE_PRIVATE)
        val ativo = prefs.getBoolean("radar10x_ativo", false)
        if (ativo) {
            val restantes = prefs.getInt("radar10x_restantes", 5)
            val acertos = prefs.getInt("radar10x_acertos", 0) + if (valor >= 10.0) 1 else 0
            val novoRestante = (restantes - 1).coerceAtLeast(0)
            prefs.edit()
                .putInt("radar10x_acertos", acertos)
                .putInt("radar10x_restantes", novoRestante)
                .putBoolean("radar10x_ativo", novoRestante > 0)
                .putInt("radar10x_ultimo_acerto", if (novoRestante == 0) acertos else prefs.getInt("radar10x_ultimo_acerto", 0))
                .putInt("radar10x_ultimo_total", if (novoRestante == 0) 5 else prefs.getInt("radar10x_ultimo_total", 0))
                .putInt("radar10x_concluidos", prefs.getInt("radar10x_concluidos", 0) + if (novoRestante == 0) 1 else 0)
                .apply()
        } else {
            val formacaoAgora = formacaoCompleta(resultados.asReversed())
            if (!formacaoAntes && formacaoAgora) {
                prefs.edit()
                    .putBoolean("radar10x_ativo", true)
                    .putInt("radar10x_restantes", 5)
                    .putInt("radar10x_acertos", 0)
                    .putLong("radar10x_inicio", System.currentTimeMillis())
                    .apply()
            }
        }
    }

    private fun atualizarBibliotecaPadroes() {
        if (!::quadroPadroes.isInitialized || !::quadroDezX.isInitialized) return
        quadroPadroes.removeAllViews()
        quadroDezX.removeAllViews()
        quadroDezX.visibility = View.GONE

        val cronologico = resultados.asReversed()
        val rosas = cronologico.takeLast(30).count { it >= 10.0 && it < 40.0 }
        val rosasStatus = when {
            rosas >= 8 -> "CRITÉRIO COMPLETO"
            rosas >= 5 -> "EM FORMAÇÃO"
            rosas >= 3 -> "OBSERVAR"
            else -> "AGUARDAR"
        }

        val ultimoDez = cronologico.indexOfLast { it >= 10.0 }
        val casas = listOf(2, 3, 4, 5, 9, 13)
        val valoresCasas = casas.mapNotNull { casa ->
            val indice = ultimoDez + casa
            if (ultimoDez >= 0 && indice < cronologico.size) cronologico[indice] else null
        }
        val casasVistas = valoresCasas.size
        val baixas = valoresCasas.count { it < 2.0 }
        val intermediarias = valoresCasas.count { it >= 2.0 && it < 10.0 }
        val espelhoStatus = when {
            casasVistas == 6 && baixas >= 2 -> "CRITÉRIO COMPLETO"
            baixas + intermediarias >= 2 -> "EM FORMAÇÃO"
            casasVistas > 0 -> "OBSERVAR"
            else -> "AGUARDAR"
        }

        val janela = cronologico.takeLast(6)
        val baixaSeguidaMedia = janela.windowed(2).any { par ->
            par[0] < 2.0 && par[1] in 3.0..5.99
        }
        val baixasRecentes = janela.count { it < 2.0 }
        val baixoAltoStatus = when {
            baixaSeguidaMedia -> "CRITÉRIO COMPLETO"
            baixasRecentes >= 2 -> "OBSERVAR"
            else -> "AGUARDAR"
        }

        val estados = listOf(rosasStatus, espelhoStatus, baixoAltoStatus)
        val completos = estados.count { it == "CRITÉRIO COMPLETO" }
        val ativos = estados.count { it != "AGUARDAR" }
        val radarStatus = when {
            completos == 3 -> "FORMAÇÃO COMPLETA"
            ativos >= 2 -> "PADRÕES EM OBSERVAÇÃO"
            ativos == 1 -> "UM PADRÃO EM OBSERVAÇÃO"
            else -> "AGUARDANDO FORMAÇÃO"
        }

        quadroPadroes.addView(TextView(this).apply {
            text = "RADAR DE FORMAÇÃO"
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(255, 105, 190))
            setPadding(8, 10, 8, 3)
        })
        quadroPadroes.addView(TextView(this).apply {
            text = "$radarStatus\nCritérios completos: $completos/3  •  Módulos com sinais: $ativos/3"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (completos == 3) Color.rgb(105, 220, 155) else Color.WHITE)
            setPadding(8, 3, 8, 8)
        })
        quadroPadroes.addView(TextView(this).apply {
            text = "COMO LER: OBSERVAR = sinal inicial. EM FORMAÇÃO = parte do padrão apareceu. CRITÉRIO COMPLETO = todas as regras daquele módulo foram encontradas. A formação completa inicia a avaliação das próximas 5 rodadas."
            textSize = 11f
            setTextColor(Color.rgb(185, 198, 220))
            setPadding(8, 3, 8, 10)
        })

        fun adicionarModulo(nome: String, detalhe: String, estado: String, progresso: String) {
            val corEstado = when (estado) {
                "CRITÉRIO COMPLETO" -> Color.rgb(105, 220, 155)
                "EM FORMAÇÃO" -> Color.rgb(255, 190, 95)
                "OBSERVAR" -> Color.rgb(110, 185, 255)
                else -> Color.rgb(175, 185, 205)
            }
            val cartaoModulo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 10, 12, 10)
                background = cartao(Color.rgb(22, 30, 48))
                addView(TextView(this@MainActivity).apply {
                    text = nome
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })
                addView(TextView(this@MainActivity).apply {
                    text = detalhe
                    textSize = 12f
                    setTextColor(Color.rgb(205, 215, 232))
                    setPadding(0, 4, 0, 5)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "$estado  •  $progresso"
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(corEstado)
                })
            }
            quadroPadroes.addView(cartaoModulo, margem())
        }

        adicionarModulo("01 • CICLO ROSA", "$rosas de 8 resultados entre 10x e 39,99x nas últimas 30 rodadas.", rosasStatus, "$rosas/8")
        adicionarModulo("02 • ESPELHO DAS CASAS", "$casasVistas de 6 posições analisadas (casas 2, 3, 4, 5, 9 e 13). Encontradas: $baixas baixas e $intermediarias intermediárias.", espelhoStatus, "$casasVistas/6 posições")
        adicionarModulo("03 • QUEBRA DE BAIXAS", "Nas últimas 6 rodadas: $baixasRecentes abaixo de 2x. Procura baixa seguida de resultado entre 3x e 5,99x.", baixoAltoStatus, if (baixaSeguidaMedia) "sequência encontrada" else "$baixasRecentes baixas recentes")

        // Backtest cronológico: só considera formações completas e as cinco rodadas posteriores.
        var sinais = 0
        var acertos = 0
        var janelasBase = 0
        var acertosBase = 0
        var estavaCompleta = false
        for (i in cronologico.indices) {
            val prefixo = cronologico.take(i + 1)
            val completaAgora = formacaoCompleta(prefixo)
            if (i + 5 < cronologico.size) {
                janelasBase++
                if (cronologico.subList(i + 1, i + 6).any { it >= 10.0 }) acertosBase++
                if (completaAgora && !estavaCompleta) {
                    sinais++
                    if (cronologico.subList(i + 1, i + 6).any { it >= 10.0 }) acertos++
                }
            }
            estavaCompleta = completaAgora
        }
        val prefs = getSharedPreferences("aviator", MODE_PRIVATE)
        val monitorando = prefs.getBoolean("radar10x_ativo", false)
        val restantes = prefs.getInt("radar10x_restantes", 5)
        val acertosAoVivo = prefs.getInt("radar10x_acertos", 0)
        val concluidos = prefs.getInt("radar10x_concluidos", 0)
        val ultimoAcerto = prefs.getInt("radar10x_ultimo_acerto", 0)
        val taxaSinal = if (sinais > 0) String.format(Locale.US, "%.1f%%", acertos * 100.0 / sinais) else "Amostra insuficiente"
        val taxaBase = if (janelasBase > 0) String.format(Locale.US, "%.1f%%", acertosBase * 100.0 / janelasBase) else "—"

        quadroPadroes.addView(TextView(this).apply {
            text = "AVALIAÇÃO DO OBJETIVO • 10x OU MAIS"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(255, 105, 190))
            setPadding(8, 14, 8, 5)
        })
        val textoAoVivo = when {
            monitorando -> "ACOMPANHAMENTO ATIVO: $restantes de 5 rodadas restantes • $acertosAoVivo resultado(s) 10x+ até agora."
            concluidos > 0 -> "Última janela concluída: $ultimoAcerto resultado(s) 10x+ em 5 rodadas. Janelas ao vivo concluídas: $concluidos."
            completos == 3 -> "Formação completa já presente no histórico. Aguardando uma nova formação para iniciar uma janela ao vivo."
            else -> "Aguardando formação completa. Quando os três critérios forem atingidos, começa a contagem das próximas 5 rodadas."
        }
        quadroPadroes.addView(TextView(this).apply {
            text = textoAoVivo
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(8, 3, 8, 8)
        })
        quadroPadroes.addView(TextView(this).apply {
            text = "HISTÓRICO: $acertos de $sinais formações tiveram pelo menos um 10x+ nas 5 rodadas seguintes ($taxaSinal). REFERÊNCIA: $acertosBase de $janelasBase janelas comuns tiveram 10x+ nas próximas 5 rodadas ($taxaBase)."
            textSize = 12f
            setTextColor(Color.rgb(205, 215, 232))
            setPadding(8, 3, 8, 10)
        })
        quadroPadroes.addView(TextView(this).apply {
            text = "A taxa histórica é descritiva, não uma probabilidade garantida. Formações podem se sobrepor e amostras pequenas oscilam bastante."
            textSize = 10f
            setTextColor(Color.rgb(165, 180, 205))
            setPadding(8, 0, 8, 8)
        })
    }

    private fun medirPadrao(tamanho: Int, alvo: Double): Pair<Int, Int> {
        if (resultados.size <= tamanho) return 0 to 0
        val padraoAtual = (tamanho - 1 downTo 0).map { categoria(resultados[it]) }
        var ocorrencias = 0
        var acertos = 0
        for (indiceSeguinte in 0 until resultados.size - tamanho) {
            var corresponde = true
            for (posicao in 0 until tamanho) {
                val indiceHistorico = indiceSeguinte + tamanho - posicao
                if (categoria(resultados[indiceHistorico]) != padraoAtual[posicao]) { corresponde = false; break }
            }
            if (corresponde) { ocorrencias++; if (resultados[indiceSeguinte] > alvo) acertos++ }
        }
        return ocorrencias to acertos
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
