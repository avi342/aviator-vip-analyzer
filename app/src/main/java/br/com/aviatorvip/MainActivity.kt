
package br.com.aviatorvip

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

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
    private lateinit var botaoPainelCompleto: Button
    private lateinit var painelCompleto: LinearLayout
    private lateinit var painelFrequencia: TextView
    private lateinit var painelReacoes: TextView
    private data class Reacao(val alvo: Double, val antes: List<Double>, val depois: MutableList<Double>)
    private val reacoes = mutableListOf<Reacao>()
    private var historicoExpandido = false
    private var inicioGraficoTempoReal = 0L
    private var painelExpandido = false
    private var inicioAnaliseSessao = 0L
    private val posicoesRolagemFaixas = mutableMapOf<String, Int>()
    private val atualizadorMinutos = Handler(Looper.getMainLooper())
    private val atualizarMinutosRunnable = object : Runnable {
        override fun run() {
            atualizarContadorDezX()
            atualizarBibliotecaPadroes()
            atualizadorMinutos.postDelayed(this, 1000L)
        }
    }
    private var colunasHistorico = 9
    private lateinit var textoZoomHistorico: TextView
    private lateinit var campoQuantidade: EditText
    private lateinit var campoMinimo: EditText
    private lateinit var campoMaximo: EditText
    private lateinit var seletorCor: Spinner
    private var filtroRapido = "Todos"
    
    private var jogoAtivo = "AirJet"
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

        carregarHistoricoJogo(jogoAtivo, migrarLegado = true)
        inicioGraficoTempoReal = getSharedPreferences("aviator", MODE_PRIVATE)
            .getLong("grafico_inicio_tempo_real", 0L)
        iniciarNovaSessaoAnalise()
        carregarReacoes()
        if (reacoes.isEmpty()) reconstruirReacoesDoHistorico()

        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(3, 3, 3, 3)
            setBackgroundColor(Color.BLACK)
        }

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
        controlesCaptura.addView(parar, LinearLayout.LayoutParams(0, alturaControle, 0.8f).apply { marginStart = 2 })
        val resetar = Button(this).apply {
            text = "↻ RESET"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Zerar gráfico em tempo real?")
                    .setMessage("O gráfico começará novamente a partir de agora. As análises dos últimos 60 minutos, estratégias, Radar e histórico de até 2.000 rodadas serão preservados.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("ZERAR GRÁFICO") { _, _ ->
                        inicioGraficoTempoReal = System.currentTimeMillis()
                        iniciarNovaSessaoAnalise()
                        getSharedPreferences("aviator", MODE_PRIVATE).edit()
                            .putLong("grafico_inicio_tempo_real", inicioGraficoTempoReal)
                            .apply()
                        historicoExpandido = false
                        atualizarGrade()
                        atualizarHistorico()
                        atualizarFrequencia()
                        atualizarContadorDezX()
                        status.text = "Gráfico e análises zerados. Histórico geral preservado."
                    }.show()
            }
            estilizarBotao(this, Color.rgb(85, 35, 45), Color.rgb(245, 90, 130))
        }
        controlesCaptura.addView(resetar, LinearLayout.LayoutParams(0, alturaControle, 0.8f).apply { marginStart = 2 })
        tela.addView(controlesCaptura, LinearLayout.LayoutParams(-1, alturaControle).apply { bottomMargin = 2 })

        val linhaStatus = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status = TextView(this).apply {
            text = "Aguardando captura..."
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setPadding(3, 2, 3, 2)
        }
        contadorDezX = TextView(this).apply {
            text = "10x: --"
            textSize = 18f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setTextColor(Color.rgb(255, 105, 190))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(2, 2, 3, 2)
            maxLines = 1
        }
        relogioDigital = TextClock(this).apply {
            format12Hour = "HH:mm:ss"
            format24Hour = "HH:mm:ss"
            timeZone = java.util.TimeZone.getDefault().id
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(2, 2, 2, 2)
        }
        val alturaStatus = (34 * resources.displayMetrics.density).toInt()
        linhaStatus.addView(status, LinearLayout.LayoutParams(0, alturaStatus, 1f))
        linhaStatus.addView(relogioDigital, LinearLayout.LayoutParams((68 * resources.displayMetrics.density).toInt(), alturaStatus))
        linhaStatus.addView(contadorDezX, LinearLayout.LayoutParams(-2, alturaStatus))
        tela.addView(linhaStatus)

        gradeResultados = GridLayout(this).apply {
            columnCount = 9
            rowCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            setPadding(1, 1, 1, 1)
            setBackgroundColor(Color.BLACK)
        }
        gradeResultados.visibility = View.GONE
        tela.addView(gradeResultados, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 2 })

        botaoHistorico = Button(this).apply {
            text = "MOSTRAR GRÁFICO"
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
                gradeResultados.visibility = if (historicoExpandido) View.VISIBLE else View.GONE
                text = if (historicoExpandido) "RECOLHER GRÁFICO" else "MOSTRAR GRÁFICO"
                atualizarGrade()
            }
        }
        tela.addView(botaoHistorico, LinearLayout.LayoutParams(-1, (27 * resources.displayMetrics.density).toInt()).apply { bottomMargin = 4 })

        quadroPadroes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 3, 4, 3)
            setBackgroundColor(Color.BLACK)
        }
        quadroDezX = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 3, 4, 3)
            setBackgroundColor(Color.BLACK)
        }
        botaoPainelCompleto = Button(this).apply {
            text = "MOSTRAR RESTANTE DO PAINEL"
            textSize = 10f
            isAllCaps = false
            setTextColor(Color.WHITE)
            minimumHeight = (29 * resources.displayMetrics.density).toInt()
            minHeight = minimumHeight
            background = cartao(Color.rgb(25, 31, 49))
            setOnClickListener {
                painelExpandido = !painelExpandido
                painelCompleto.visibility = if (painelExpandido) View.VISIBLE else View.GONE
                text = if (painelExpandido) "RECOLHER PAINEL" else "MOSTRAR RESTANTE DO PAINEL"
            }
        }
        tela.addView(botaoPainelCompleto, margem())
        painelFrequencia = TextView(this).apply {
            textSize = 9f
            setTextColor(Color.WHITE)
            setPadding(5, 3, 5, 3)
            typeface = android.graphics.Typeface.MONOSPACE
            background = cartao(Color.rgb(22, 25, 38))
        }
        painelReacoes = TextView(this).apply {
            textSize = 9f
            setTextColor(Color.WHITE)
            setPadding(5, 3, 5, 3)
            typeface = android.graphics.Typeface.MONOSPACE
            background = cartao(Color.rgb(22, 25, 38))
        }
        painelCompleto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(quadroPadroes, margem())
            addView(quadroDezX, margem())
            addView(painelFrequencia, margem())
            addView(painelReacoes, margem())
        }
        tela.addView(painelCompleto, margem())

        painelEstrategia = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f }
        painelEstatisticas = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f }
        quadroEstrategias = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        
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
        atualizarFrequencia()
        atualizarReacoes()
        atualizadorMinutos.post(atualizarMinutosRunnable)

    }

    private fun solicitarImportacaoJogo(nomeJogo: String) {
        val campoChave = EditText(this).apply {
            hint = "Cole aqui sua chave GameData365"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
            addView(campoChave)
            addView(TextView(this@MainActivity).apply {
                text = "A chave será usada nesta consulta e não será salva no aplicativo nem no GitHub."
                textSize = 12f
                setTextColor(Color.LTGRAY)
                setPadding(0, 8, 0, 0)
            })
        }
        AlertDialog.Builder(this)
            .setTitle("Importar histórico $nomeJogo")
            .setMessage("A consulta identifica o jogo pela lista liberada para sua chave e busca até 50 rodadas. Usa 2 requisições.")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Importar", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val chave = campoChave.text.toString().trim()
                        if (chave.isBlank()) {
                            campoChave.error = "Informe sua chave"
                            return@setOnClickListener
                        }
                        dialog.dismiss()
                        status.text = "Conectando à GameData365 ($nomeJogo)..."
                        Thread {
                            try {
                                val api = GameData365Client(chave)
                                val gameId = api.findGameId(nomeJogo)
                                val historicoApi = api.fetchAirJetHistory(gameId, limit = 50)
                                runOnUiThread {
                                    jogoAtivo = nomeJogo
                                    carregarHistoricoJogo(nomeJogo)
                                    carregarReacoes()
                                    val preferencias = getSharedPreferences("aviator", MODE_PRIVATE)
                                    val chaveIds = "${nomeJogo.lowercase(Locale.ROOT)}_round_ids"
                                    val idsSalvos = preferencias.getStringSet(chaveIds, emptySet())
                                        ?.toMutableSet() ?: mutableSetOf()
                                    var adicionados = 0
                                    historicoApi.rounds.asReversed().forEach { rodada ->
                                        if (idsSalvos.add(rodada.roundId)) {
                                            resultados.add(0, rodada.multiplier)
                                            horarios.add(0, horarioDaApi(rodada.timestamp))
                                            marcasTempo.add(0, instanteDaApi(rodada.timestamp))
                                            adicionados++
                                        }
                                    }
                                    while (resultados.size > 2000) {
                                        resultados.removeAt(resultados.lastIndex)
                                        if (horarios.isNotEmpty()) horarios.removeAt(horarios.lastIndex)
                                        if (marcasTempo.isNotEmpty()) marcasTempo.removeAt(marcasTempo.lastIndex)
                                    }
                                    preferencias.edit().putStringSet(chaveIds, idsSalvos).apply()
                                    reconstruirReacoesDoHistorico()
                                    salvar()
                                    atualizarHistorico()
                                    atualizarContadorDezX()
                                    atualizarGrade()
                                    avaliarEstrategia()
                                    atualizarBibliotecaPadroes()
                                    atualizarFrequencia()
                                    atualizarReacoes()
                                    status.text = "$nomeJogo: $adicionados rodadas importadas."
                                    Toast.makeText(this@MainActivity, "$adicionados resultados importados do $nomeJogo.", Toast.LENGTH_LONG).show()
                                }
                            } catch (erro: Exception) {
                                runOnUiThread {
                                    status.text = "Falha ao importar $nomeJogo."
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("Não foi possível importar $nomeJogo")
                                        .setMessage(erro.message ?: "Erro de conexão com a GameData365.")
                                        .setPositiveButton("OK", null).show()
                                }
                            }
                        }.start()
                    }
                }
                dialog.show()
            }
    }

    private fun carregarHistoricoJogo(nomeJogo: String, migrarLegado: Boolean = false) {
        resultados.clear()
        horarios.clear()
        marcasTempo.clear()
        val prefs = getSharedPreferences("aviator", MODE_PRIVATE)
        val prefixo = nomeJogo.lowercase(Locale.ROOT)
        val legado = migrarLegado && nomeJogo == "AirJet"
        val valores = prefs.getString("${prefixo}_resultados", null)
            ?: if (legado) prefs.getString("resultados", "") else ""
        val horas = prefs.getString("${prefixo}_horarios", null)
            ?: if (legado) prefs.getString("horarios_resultados", "") else ""
        val tempos = prefs.getString("${prefixo}_tempos", null)
            ?: if (legado) prefs.getString("tempos_resultados", "") else ""
        valores.orEmpty().takeIf { it.isNotBlank() }?.split(",")?.forEach { it.toDoubleOrNull()?.let(resultados::add) }
        horas.orEmpty().takeIf { it.isNotBlank() }?.split("|")?.let(horarios::addAll)
        tempos.orEmpty().takeIf { it.isNotBlank() }?.split(",")?.forEach { it.toLongOrNull()?.let(marcasTempo::add) }
        while (horarios.size < resultados.size) horarios.add("--:--:--")
        while (marcasTempo.size < resultados.size) marcasTempo.add(0L)
    }

    private fun horarioDaApi(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return "--:--:--"
        return timestamp.substringAfter("T", timestamp.substringAfter(" ")).take(8)
    }

    private fun instanteDaApi(timestamp: String?): Long {
        if (timestamp.isNullOrBlank()) return 0L
        return try {
            val formato = if (timestamp.contains("T")) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US)
            } else {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            }
            formato.timeZone = java.util.TimeZone.getTimeZone("UTC")
            formato.parse(timestamp)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
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
        atualizadorMinutos.removeCallbacks(atualizarMinutosRunnable)
        try {
            unregisterReceiver(receptor)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
    
  
private fun processarOCR(texto: String) {

    if (texto.startsWith("PLATFORM:JETX:") || texto.startsWith("PLATFORM:AVIATOR:")) {
        val novoJogo = if (texto.startsWith("PLATFORM:JETX:")) "JetX" else "Aviator"
        if (jogoAtivo != novoJogo) {
            jogoAtivo = novoJogo
            candidatoOCR = null
            leiturasDoCandidato = 0
            rodadaRegistrada = false
            carregarHistoricoJogo(novoJogo)
            carregarReacoes()
            if (reacoes.isEmpty()) reconstruirReacoesDoHistorico()
            atualizarHistorico()
            atualizarContadorDezX()
            atualizarGrade()
            avaliarEstrategia()
            atualizarBibliotecaPadroes()
            atualizarFrequencia()
            atualizarReacoes()
        }
        status.text = "$novoJogo reconhecido • ${texto.substringAfterLast(":")}"
        return
    }

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
        registrarReacao(valor)
        resultados.add(0, valor)
        horarios.add(0, horarioAtual())
        marcasTempo.add(0, System.currentTimeMillis())
        atualizarMonitoramentoVip(valor)
        salvar()
        atualizarHistorico()
        atualizarContadorDezX()
        atualizarGrade()
        avaliarEstrategia()
        atualizarBibliotecaPadroes()
        atualizarFrequencia()
        atualizarReacoes()

        rodadaRegistrada = true
        status.text = "Resultado registrado: $valor x"
    }
}

    private fun salvar() {
        val prefixo = jogoAtivo.lowercase(Locale.ROOT)
        getSharedPreferences("aviator", MODE_PRIVATE).edit()
            .putString("${prefixo}_resultados", resultados.joinToString(","))
            .putString("${prefixo}_horarios", horarios.joinToString("|"))
            .putString("${prefixo}_tempos", marcasTempo.joinToString(","))
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

    private fun atualizarTextoZoomHistorico() {
        if (!::textoZoomHistorico.isInitialized) return
        textoZoomHistorico.text = when {
            colunasHistorico <= 4 -> "GRANDE"
            colunasHistorico <= 6 -> "MÉDIO"
            else -> "PEQUENO"
        } + " · ${colunasHistorico} col."
    }

    private fun atualizarGrade() {
        if (!::gradeResultados.isInitialized) return
        gradeResultados.removeAllViews()

        // O gráfico mostra apenas as rodadas registradas desde o último RESET.
        // O histórico completo permanece salvo separadamente, sem ser apagado.
        val densidade = resources.displayMetrics.density
        val indicesGrafico = resultados.indices.filter { indice ->
            (marcasTempo.getOrNull(indice) ?: 0L) >= inicioGraficoTempoReal
        }
        val quantidade = if (historicoExpandido) indicesGrafico.size.coerceAtLeast(9) else 9
        val colunas = 9
        gradeResultados.columnCount = colunas
        gradeResultados.rowCount = (quantidade + colunas - 1) / colunas

        for (posicao in 0 until quantidade) {
            val indiceResultado = indicesGrafico.getOrNull(posicao)
            val valor = indiceResultado?.let { resultados.getOrNull(it) }
            val cor = when {
                valor == null -> Color.rgb(35, 39, 50)
                valor < 2.0 -> Color.rgb(30, 105, 155)
                valor < 10.0 -> Color.rgb(78, 54, 135)
                valor < 40.0 -> Color.rgb(135, 43, 96)
                else -> Color.rgb(105, 32, 70)
            }
            val celula = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding((3 * densidade).toInt(), (4 * densidade).toInt(),
                    (3 * densidade).toInt(), (4 * densidade).toInt())
                background = GradientDrawable().apply {
                    setColor(cor)
                    setStroke((1 * densidade).toInt(), Color.rgb(65, 72, 92))
                    cornerRadius = 7 * densidade
                }
                if (valor != null) {
                    addView(TextView(this@MainActivity).apply {
                        text = "%.2fx".format(Locale.US, valor)
                        gravity = Gravity.CENTER
                        textSize = when (colunas) { in 3..4 -> 17f; in 5..6 -> 14f; else -> 11f }
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        maxLines = 1
                        setSingleLine(true)
                    })
                    val horario = indiceResultado?.let { horarios.getOrNull(it) }.orEmpty()
                    if (horario.isNotBlank() && horario != "--:--:--") {
                        addView(TextView(this@MainActivity).apply {
                            text = horario
                            gravity = Gravity.CENTER
                            textSize = when (colunas) { in 3..4 -> 11f; in 5..6 -> 9f; else -> 8f }
                            setTextColor(Color.rgb(225, 230, 240))
                            maxLines = 1
                            setSingleLine(true)
                        })
                    }
                } else {
                    addView(TextView(this@MainActivity).apply {
                        text = "—"
                        gravity = Gravity.CENTER
                        textSize = 12f
                        setTextColor(Color.DKGRAY)
                    })
                }
            }
            val linha = posicao / colunas
            val coluna = posicao % colunas
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(linha, 1),
                GridLayout.spec(coluna, 1f)
            ).apply {
                width = 0
                height = when (colunas) { in 3..4 -> (54 * densidade).toInt(); in 5..6 -> (46 * densidade).toInt(); else -> (39 * densidade).toInt() }
                setMargins((2 * densidade).toInt(), (2 * densidade).toInt(),
                    (2 * densidade).toInt(), (2 * densidade).toInt())
            }
            gradeResultados.addView(celula, lp)
        }

        botaoHistorico.text = if (historicoExpandido) {
            "⌃ RECOLHER GRÁFICO"
        } else {
            "VER MAIS GRÁFICO (${indicesGrafico.size.coerceAtMost(2000)})"
        }
        botaoHistorico.visibility = if (indicesGrafico.size > 9) View.VISIBLE else View.GONE
    }

    private fun iniciarNovaSessaoAnalise() {
        inicioAnaliseSessao = System.currentTimeMillis()
        val editor = getSharedPreferences("aviator", MODE_PRIVATE).edit()
        for (id in listOf("10", "25")) {
            editor.putBoolean("radar${id}x_ativo", false)
                .putInt("radar${id}x_restantes", 0)
                .putInt("radar${id}x_acertos", 0)
                .putString("radar${id}x_resultado", "AGUARDANDO")
                .putInt("radar${id}x_ultimo_gatilho", -1)
        }
        for (i in 0 until 6) {
            editor.putBoolean("vip_${i}_ativo", false)
                .putInt("vip_${i}_restantes", 0)
                .putString("vip_${i}_resultado", "AGUARDANDO")
                .putInt("vip_${i}_ultimo_gatilho", -1)
        }
        editor.apply()
    }

    private fun atualizarContadorDezX() {
        if (!::contadorDezX.isInitialized) return
        // resultados[0] é a rodada mais recente. O índice do último 10x
        // equivale à quantidade de rodadas/casas desde aquele multiplicador.
        val indice = resultados.indices.firstOrNull { i ->
            resultados[i] >= 10.0 && (marcasTempo.getOrNull(i) ?: 0L) >= inicioAnaliseSessao
        } ?: -1
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


    private fun chaveReacoes(): String =
        "reacoes_3x_" + jogoAtivo.lowercase(Locale.ROOT)

    private fun carregarReacoes() {
        reacoes.clear()
        val prefs = getSharedPreferences("aviator", MODE_PRIVATE)
        val bruto = prefs.getString(chaveReacoes(), null)
            ?: if (jogoAtivo == "Aviator") prefs.getString("reacoes_3x", "[]") else "[]"
            ?: "[]"
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
        } catch (_: Exception) {
            reacoes.clear()
        }
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
        getSharedPreferences("aviator", MODE_PRIVATE).edit()
            .putString(chaveReacoes(), array.toString())
            .apply()
    }

    private fun reconstruirReacoesDoHistorico() {
        reacoes.clear()
        val cronologico = resultados.asReversed()
        cronologico.forEachIndexed { indice, valor ->
            if (valor >= 3.0) {
                val inicioAntes = (indice - 5).coerceAtLeast(0)
                val antes = (inicioAntes until indice).map { cronologico[it] }.asReversed()
                val fimDepois = (indice + 5).coerceAtMost(cronologico.lastIndex)
                val depois = if (indice < fimDepois) {
                    ((indice + 1)..fimDepois).map { cronologico[it] }.toMutableList()
                } else mutableListOf()
                reacoes.add(Reacao(valor, antes, depois))
            }
        }
        while (reacoes.size > 100) reacoes.removeAt(0)
        salvarReacoes()
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
        val historicoCronologico = resultados.asReversed()
        if (historicoCronologico.size < 6) {
            painelReacoes.text = "RADAR 3x+ • aguardando pelo menos 6 rodadas."
            return
        }
        val atual = historicoCronologico.takeLast(5)
        val assinaturaAtual = assinatura(atual)
        var semelhantes = 0
        var acertos = 0
        val tolerancia = 2
        for (i in 0 until historicoCronologico.size - 5) {
            val janela = historicoCronologico.subList(i, i + 5)
            val assinaturaHistorica = assinatura(janela)
            val distancia = assinaturaAtual.indices.sumOf { k ->
                kotlin.math.abs(assinaturaAtual[k] - assinaturaHistorica[k])
            }
            if (distancia <= tolerancia) {
                semelhantes++
                if (historicoCronologico[i + 5] >= 3.0) acertos++
            }
        }
        val percentual = if (semelhantes == 0) 0 else acertos * 100 / semelhantes
        val linhas = mutableListOf<String>()
        linhas += "RADAR ANTECIPADO • ALVO 3x+"
        linhas += "ÚLTIMAS 5 CASAS: " + atual.joinToString("  ") {
            String.format(Locale.US, "%.2f", it) + "x"
        }
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
        painelReacoes.text = textoComDestaque(linhas.joinToString("\n"), listOf("REAÇÕES SEMELHANTES:", "AMOSTRA INSUFICIENTE", "FREQUÊNCIA HISTÓRICA"))
    }

    private fun atualizarFrequencia() {
        if (!::painelFrequencia.isInitialized) return
        // As análises curtas usam somente as rodadas da sessão atual.
        // O histórico completo permanece salvo para o gráfico e demais painéis.
        val cronologicoCompleto = resultados.asReversed()
        val temposCronologicos = marcasTempo.asReversed()
        val cronologico = cronologicoCompleto.indices.filter { idx ->
            val instante = temposCronologicos.getOrNull(idx) ?: 0L
            instante >= inicioAnaliseSessao && instante > 0L
        }.map { cronologicoCompleto[it] }

        data class Leitura(val texto: String, val casos: Int, val acertos: Int, val baixos: Int)
        fun analisar(casas: Int, alvo: Double): Leitura {
            if (cronologico.size <= casas) {
                return Leitura("${casas} CASA${if (casas == 1) "" else "S"} • aguardando rodadas", 0, 0, 0)
            }
            val padraoAtual = cronologico.takeLast(casas)
            var casos = 0
            var acertos = 0
            var baixos = 0
            for (i in 0 until cronologico.size - casas) {
                val janela = cronologico.subList(i, i + casas)
                val semelhante = janela.indices.all { k ->
                    kotlin.math.abs(janela[k] - padraoAtual[k]) <=
                        maxOf(0.75, padraoAtual[k] * 0.35)
                }
                if (semelhante) {
                    casos++
                    val proximo = cronologico[i + casas]
                    if (proximo >= alvo) acertos++
                    if (proximo < 2.0) baixos++
                }
            }
            val percentual = if (casos == 0) 0 else acertos * 100 / casos
            val percentualBaixos = if (casos == 0) 0 else baixos * 100 / casos
            val alvoTexto = if (alvo >= 10.0) "10x+" else "2x+"
            val estado = if (casos < 10) "poucos casos" else "casos"
            val linha = "${casas} CASA${if (casas == 1) "" else "S"} • ${percentual}% ${alvoTexto} • ${percentualBaixos}% abaixo 2x • ${casos} ${estado}"
            return Leitura(linha, casos, acertos, baixos)
        }

        val um = analisar(1, 10.0)
        val dois = analisar(2, 2.0)
        val tres = analisar(3, 2.0)
        val statusSessao = when {
            um.casos < 10 -> "INDEFINIDO • AMOSTRA PEQUENA"
            um.baixos > um.acertos -> "BAIXOS PREDOMINAM NO HISTÓRICO"
            um.acertos > um.baixos -> "10X+ PREDOMINAM NO HISTÓRICO"
            else -> "COMPORTAMENTO MISTO"
        }
        painelFrequencia.text = textoComDestaque(
            "ANÁLISE DA SESSÃO • ${cronologico.size} RODADAS\n" +
                "1 CASA: ${um.texto}\n" +
                "2 CASAS: ${dois.texto}\n" +
                "3 CASAS: ${tres.texto}\n" +
                statusSessao + "\n" +
                "Frequências históricas; não são previsão da próxima rodada.",
            listOf("1 CASA", "2 CASAS", "3 CASAS", "BAIXOS PREDOMINAM", "10X+ PREDOMINAM", "INDEFINIDO")
        )
    }
