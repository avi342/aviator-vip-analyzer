
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
    private lateinit var painelConfluencia: TextView
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
                        getSharedPreferences("aviator", MODE_PRIVATE).edit()
                            .putLong("grafico_inicio_tempo_real", inicioGraficoTempoReal)
                            .apply()
                        historicoExpandido = false
                        atualizarGrade()
                        status.text = "Gráfico zerado. Histórico e análises preservados."
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
        painelConfluencia = TextView(this).apply {
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
            addView(painelConfluencia, margem())
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
        atualizarConfluencia()
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
        val cronologico = resultados.asReversed()

        // Inversão separada por análise:
        // 1 casa procura o próximo resultado de 10x ou mais.
        // 2 e 3 casas procuram o próximo resultado abaixo de 2x.
        fun analisar(casas: Int, alvo10x: Boolean): String {
            val alvoTexto = if (alvo10x) "10x+" else "abaixo de 2x"
            if (cronologico.size <= casas) return "${casas} CASA(S): aguardando histórico"
            val padraoAtual = cronologico.takeLast(casas)
            var casos = 0
            var acertos = 0
            for (i in 0 until cronologico.size - casas) {
                val janela = cronologico.subList(i, i + casas)
                val semelhante = janela.indices.all { k ->
                    kotlin.math.abs(janela[k] - padraoAtual[k]) <=
                        maxOf(0.75, padraoAtual[k] * 0.35)
                }
                if (semelhante) {
                    casos++
                    val proximo = cronologico[i + casas]
                    if (if (alvo10x) proximo >= 10.0 else proximo < 2.0) acertos++
                }
            }
            val percentual = if (casos == 0) 0 else acertos * 100 / casos
            val estado = if (casos < 10) "poucos casos" else "amostra"
            return "${casas} CASA(S) • ${acertos}/${casos} ${alvoTexto} • ${percentual}% • ${estado}"
        }

        painelFrequencia.text = textoComDestaque(
            "ANÁLISES INVERTIDAS • ALVOS SEPARADOS\n" +
                analisar(1, true) + "\n" +
                analisar(2, false) + "\n" +
                analisar(3, false),
            listOf("ANÁLISES INVERTIDAS", "CASA(S)", "10x+", "abaixo de 2x", "poucos casos")
        )
        atualizarConfluencia()
    }

    private data class MetricasConfluencia(val casas2: Int, val radar3: Int, val casos2: Int, val casos3: Int)

    private fun metricasConfluencia(historico: List<Double>): MetricasConfluencia? {
        if (historico.size < 7) return null
        val padrao2 = historico.takeLast(2)
        var casos2 = 0
        var acertos2 = 0
        for (i in 0 until historico.size - 2) {
            val semelhante = (0..1).all { k ->
                kotlin.math.abs(historico[i + k] - padrao2[k]) <= maxOf(0.75, padrao2[k] * 0.35)
            }
            if (semelhante) {
                casos2++
                if (historico[i + 2] >= 2.0) acertos2++
            }
        }
        val assinaturaAtual = assinatura(historico.takeLast(5))
        var casos3 = 0
        var acertos3 = 0
        for (i in 0 until historico.size - 5) {
            val assinaturaHistorica = assinatura(historico.subList(i, i + 5))
            val distancia = assinaturaAtual.indices.sumOf { k ->
                kotlin.math.abs(assinaturaAtual[k] - assinaturaHistorica[k])
            }
            if (distancia <= 2) {
                casos3++
                if (historico[i + 5] >= 3.0) acertos3++
            }
        }
        val pct2 = if (casos2 == 0) 0 else acertos2 * 100 / casos2
        val pct3 = if (casos3 == 0) 0 else acertos3 * 100 / casos3
        return MetricasConfluencia(pct2, pct3, casos2, casos3)
    }

    private fun atualizarConfluencia() {
        if (!::painelConfluencia.isInitialized) return
        val cronologico = resultados.asReversed()
        val atual = metricasConfluencia(cronologico)
        if (atual == null) {
            painelConfluencia.text = "CONFLUÊNCIA 2x+/3x+ • AGUARDANDO HISTÓRICO"
            return
        }
        var sinais = 0
        var acima2 = 0
        var acima10 = 0
        for (i in 7 until cronologico.size) {
            val m = metricasConfluencia(cronologico.subList(0, i)) ?: continue
            if (m.radar3 in 30..35 && m.casas2 in 50..70) {
                sinais++
                if (cronologico[i] >= 2.0) acima2++
                if (cronologico[i] >= 10.0) acima10++
            }
        }
        val pct2 = if (sinais == 0) 0 else acima2 * 100 / sinais
        val ativa = atual.radar3 in 30..35 && atual.casas2 in 50..70
        val texto = "CONFLUÊNCIA • " + (if (ativa) "CRITÉRIOS JUNTOS" else "AGUARDANDO") +
            "\n2 CASAS \${atual.casas2}%  |  RADAR 3x+ \${atual.radar3}%" +
            "\nHIST. \$sinais SINAIS • 2x+ \$acima2 (\$pct2%) • 10x+ \$acima10"
        painelConfluencia.text = textoComDestaque(texto, listOf("CRITÉRIOS JUNTOS", "2x+", "10x+"))
    }

    private fun textoComDestaque(conteudo: String, marcadores: List<String>): CharSequence {
        val texto = android.text.SpannableString(conteudo)
        var inicio = 0
        conteudo.split("\n").forEach { linha ->
            val relevante = marcadores.any { linha.contains(it, ignoreCase = true) }
            if (relevante && linha.isNotBlank()) {
                texto.setSpan(android.text.style.ForegroundColorSpan(Color.rgb(255, 190, 110)), inicio, inicio + linha.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                texto.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), inicio, inicio + linha.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            inicio += linha.length + 1
        }
        return texto
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
        painelEstrategia.textSize = 17f
        painelEstrategia.text =
            "ESTRATÉGIA PRINCIPAL — DUAS RODADAS → PRÓXIMA ≥ 2x\\n" +
            "Rodadas armazenadas: ${resultados.size}\\n" +
            "O aplicativo acompanha somente esta combinação e atualiza a frequência quando chega uma nova rodada.\\n\\n" +
            "O histórico serve para medir ocorrências anteriores; não é garantia de resultado futuro."
    }

    private data class ResultadoPadrao(val comparacoes: Int, val sucessos: Int, val tamanho: Int)

    // Compara blocos por categoria de cor, aceitando pequenas diferenças entre multiplicadores.
    private fun buscarPadraoAtual(serie: List<Double>, alvo: (Double) -> Boolean): ResultadoPadrao {
        val tamanhos = listOf(6, 9, 12, 15)
        var melhor = ResultadoPadrao(0, 0, 0)
        for (tamanho in tamanhos) {
            if (serie.size < tamanho + 5) continue
            val consulta = serie.takeLast(tamanho).map { categoria(it) }
            val limite = serie.size - tamanho - 5
            var comparacoes = 0
            var sucessos = 0
            for (inicio in 0..limite) {
                var iguais = 0
                for (k in 0 until tamanho) {
                    if (categoria(serie[inicio + k]) == consulta[k]) iguais++
                }
                if (iguais.toDouble() / tamanho >= 0.75) {
                    comparacoes++
                    if ((inicio + tamanho until inicio + tamanho + 5).any { alvo(serie[it]) }) sucessos++
                }
            }
            if (comparacoes >= 3 && (melhor.comparacoes == 0 || sucessos.toDouble() / comparacoes > melhor.sucessos.toDouble() / melhor.comparacoes)) {
                melhor = ResultadoPadrao(comparacoes, sucessos, tamanho)
            }
        }
        return melhor
    }

    private fun atualizarMonitoramento10x(valor: Double) {
        val prefs = getSharedPreferences("aviator", MODE_PRIVATE)
        val cronologico = resultados.asReversed()
        val base = cronologico.dropLast(1)
        val objetivos = listOf(Triple("10",10.0,null as Double?),Triple("25",2.0,5.0 as Double?))
        objetivos.forEach { (id,minimo,maximo) ->
            val chaveAtivo = "radar" + id + "x_ativo"
            val ativo = prefs.getBoolean(chaveAtivo,false)
            if (ativo) {
                val restantes=(prefs.getInt("radar" + id + "x_restantes",5)-1).coerceAtLeast(0)
                val acertou=valor>=minimo&&(maximo==null||valor<=maximo)
                val acertos=prefs.getInt("radar" + id + "x_acertos",0)+if(acertou)1 else 0
                val editor=prefs.edit().putInt("radar" + id + "x_restantes",restantes).putInt("radar" + id + "x_acertos",acertos).putBoolean(chaveAtivo,restantes>0)
                if(restantes==0){editor.putString("radar" + id + "x_resultado",if(acertos>0)"SIM" else "NÃO");editor.putInt("radar" + id + "x_ultimo_fim",resultados.size)}
                else editor.putString("radar" + id + "x_resultado","AVALIANDO")
                editor.apply()
                return@forEach
            }
            val candidatos=descobrirPadroes(base,minimo,2,maximo)
            val atual=cronologico.takeLast(5).map{categoriaValidador(it)}
            val candidato=candidatos.firstOrNull{(padrao,stats)->padrao.size<=atual.size&&atual.takeLast(padrao.size)==padrao&&stats.ocorrencias>=4&&stats.percentual>=55.0}
            val ultimoGatilho=prefs.getInt("radar" + id + "x_ultimo_gatilho",-1)
            if(candidato!=null&&ultimoGatilho!=resultados.size){
                val(padrao,stats)=candidato
                prefs.edit().putBoolean(chaveAtivo,true).putInt("radar" + id + "x_restantes",5).putInt("radar" + id + "x_acertos",0)
                    .putString("radar" + id + "x_resultado","AVALIANDO").putInt("radar" + id + "x_matches",stats.ocorrencias)
                    .putInt("radar" + id + "x_hits",stats.vitorias).putInt("radar" + id + "x_bloco",padrao.size)
                    .putInt("radar" + id + "x_ultimo_gatilho",resultados.size).apply()
            }else if(ultimoGatilho!=resultados.size){
                prefs.edit().putString("radar" + id + "x_resultado","AGUARDANDO").apply()
            }
        }
    }

    // Seis detectores independentes; cada sinal observa as próximas três rodadas.
    private fun atualizarMonitoramentoVip(valor: Double) {
        val prefs=getSharedPreferences("aviator", MODE_PRIVATE)
        val serie=resultados.asReversed()
        val indiceAtual=serie.size-1
        val categorias=serie.map { categoriaValidador(it) }
        val editor=prefs.edit()
        for(i in 0 until 6) {
            if(prefs.getBoolean("vip_${i}_ativo",false)) {
                val restante=(prefs.getInt("vip_${i}_restantes",3)-1).coerceAtLeast(0)
                if(valor>=3.0) editor.putBoolean("vip_${i}_ativo",false).putInt("vip_${i}_restantes",0).putString("vip_${i}_resultado","SIM")
                else if(restante==0) editor.putBoolean("vip_${i}_ativo",false).putInt("vip_${i}_restantes",0).putString("vip_${i}_resultado","NÃO")
                else editor.putInt("vip_${i}_restantes",restante)
            }
        }
        editor.apply()
        if(serie.size<2) return
        val rosaIndices=serie.indices.filter { serie[it]>=10.0 }
        val ultimaRosa=rosaIndices.lastOrNull()
        val casaPos=if(ultimaRosa!=null) indiceAtual-ultimaRosa else -1
        val alternancia=categorias.size>=4 && categorias.takeLast(4).let { it[0]==it[2] && it[1]==it[3] && it[0]!=it[1] }
        val quebra=categorias.size>=4 && categorias.takeLast(4).let { it[0]==it[1] && it[1]==it[2] && it[2]!=it[3] }
        val rosaQuebra=serie.size>=4 && serie.last()>=10.0 && categorias.takeLast(4).let { it[0]==it[1] && it[1]==it[2] && it[2]!=it[3] }
        val rebote=serie.size>=3 && serie.takeLast(3).all { it<2.0 }
        val espelho=casaPos in listOf(2,3,4,5,9,13) && serie.last()<2.0
        val repeticao=casaPos in listOf(2,3,4,5,9,13) && rosaIndices.dropLast(1).any { antigo ->
            val distancia=indiceAtual-antigo
            distancia in listOf(2,3,4,5,9,13)
        }
        val gatilhos=listOf(rebote,espelho,alternancia,quebra,rosaQuebra,repeticao)
        gatilhos.forEachIndexed { i, disparou ->
            val chave="vip_${i}_"
            if(disparou && !prefs.getBoolean(chave+"ativo",false) && prefs.getInt(chave+"ultimo_gatilho",-1)!=indiceAtual) {
                prefs.edit().putBoolean(chave+"ativo",true).putInt(chave+"restantes",3)
                    .putString(chave+"resultado","AVALIANDO").putInt(chave+"ultimo_gatilho",indiceAtual).apply()
            }
        }
    }

    private fun atualizarBibliotecaPadroes() {
        if (!::quadroPadroes.isInitialized || !::quadroDezX.isInitialized) return
        val posicoesAntesDaAtualizacao = mutableMapOf<String, Int>().apply { putAll(posicoesRolagemFaixas) }
        quadroPadroes.removeAllViews()
        quadroDezX.removeAllViews()

        // A análise usa sempre a janela móvel dos últimos 60 minutos.
        // O histórico completo continua armazenado separadamente (até 2.000 rodadas).
        val agora = System.currentTimeMillis()
        val corte = agora - 60L * 60L * 1000L
        val serieCronoCompleta = resultados.asReversed()
        val temposCronoCompleta = marcasTempo.asReversed()
        val horariosCronoCompleta = horarios.asReversed()
        val indicesJanela = serieCronoCompleta.indices.filter { idx ->
            val instante = temposCronoCompleta.getOrNull(idx) ?: 0L
            instante in corte..agora
        }
        val serie = indicesJanela.map { serieCronoCompleta[it] }
        val tempos = indicesJanela.map { temposCronoCompleta[it] }
        val horariosJanela = indicesJanela.map { horariosCronoCompleta.getOrNull(it).orEmpty() }

        val cores = listOf(Color.rgb(70,165,245), Color.rgb(160,115,245), Color.rgb(245,90,175))
        fun linha(texto: String, tamanho: Float = 13f, cor: Int = Color.WHITE, negrito: Boolean = false) {
            quadroPadroes.addView(TextView(this).apply {
                text = texto
                textSize = tamanho
                setTextColor(cor)
                if (negrito) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(3, 2, 3, 2)
            })
        }
        fun tempo(ms: Long): String {
            if (ms <= 0L) return "--"
            return "${ms / 60000L} min"
        }
        fun faixaHorizontal(titulo: String, itens: List<String>, cor: Int) {
            linha(titulo, 13f, cor, true)
            val faixa = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = true
                setOnScrollChangeListener { _, scrollX, _, _, _ ->
                    posicoesRolagemFaixas[titulo] = scrollX
                }
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    itens.forEach { item ->
                        addView(TextView(this@MainActivity).apply {
                            text = item
                            textSize = 13f
                            setTextColor(Color.WHITE)
                            setPadding(8, 5, 8, 5)
                            background = cartao(Color.rgb(28, 34, 48))
                        }, LinearLayout.LayoutParams(-2, -2).apply {
                            rightMargin = (5 * resources.displayMetrics.density).toInt()
                        })
                    }
                })
            }
            faixa.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN,
                    android.view.MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            quadroPadroes.addView(faixa)
            faixa.post { faixa.scrollTo(posicoesAntesDaAtualizacao[titulo] ?: 0, 0) }
        }

        // O painel de casas usa uma janela móvel independente de 20 minutos.
        val corteCasas = agora - 20L * 60L * 1000L
        val indices20Min = serieCronoCompleta.indices.filter { idx ->
            val instante = temposCronoCompleta.getOrNull(idx) ?: 0L
            instante in corteCasas..agora
        }
        val serie20Min = indices20Min.map { serieCronoCompleta[it] }
        val rosas20Min = serie20Min.indices.filter { serie20Min[it] >= 10.0 }
        val frequenciasCasas = mutableMapOf<Int, Int>()
        for (i in 1 until rosas20Min.size) {
            val casas = rosas20Min[i] - rosas20Min[i - 1]
            if (casas in 1..20) frequenciasCasas[casas] = (frequenciasCasas[casas] ?: 0) + 1
        }
        linha("CASAS QUE SE REPETIRAM APÓS 10X+  •  ÚLTIMOS 20 MIN", 13f, Color.rgb(245,150,205), true)
        val gradeCasas = GridLayout(this).apply {
            columnCount = 10
            rowCount = 2
            useDefaultMargins = false
            setPadding(1, 2, 1, 2)
        }
        // Só aparecem as posições com repetição observada na janela atual.
        for (casa in 1..20) {
            val contagem = frequenciasCasas[casa] ?: 0
            if (contagem == 0) continue
            val celula = TextView(this).apply {
                text = "${casa}ª\n${contagem}x"
                gravity = Gravity.CENTER
                textSize = 10f
                setTextColor(Color.WHITE)
                setPadding(1, 2, 1, 2)
                background = cartao(Color.rgb(28, 34, 48))
            }
            gradeCasas.addView(celula, GridLayout.LayoutParams(
                GridLayout.spec((casa - 1) / 10, 1),
                GridLayout.spec((casa - 1) % 10, 1f)
            ).apply {
                width = 0
                height = (25 * resources.displayMetrics.density).toInt()
                setMargins(2, 2, 2, 2)
            })
        }
        if (frequenciasCasas.isEmpty()) {
            linha("Nenhuma casa repetida nesta janela de 20 minutos.", 11f, Color.LTGRAY)
        } else {
            quadroPadroes.addView(gradeCasas)
        }
        // Painel paralelo: repetição dos intervalos em minutos entre eventos 10x+,
        // calculados somente com eventos ocorridos dentro da mesma janela móvel de 20 minutos.
        val frequenciasMinutos = mutableMapOf<Int, Int>()
        for (i in 1 until rosas20Min.size) {
            val indiceAnterior = indices20Min[rosas20Min[i - 1]]
            val indiceAtual = indices20Min[rosas20Min[i]]
            val tAnterior = temposCronoCompleta.getOrNull(indiceAnterior) ?: 0L
            val tAtual = temposCronoCompleta.getOrNull(indiceAtual) ?: 0L
            if (tAnterior > 0L && tAtual > tAnterior) {
                val minutos = ((tAtual - tAnterior) / 60000L).toInt()
                if (minutos in 1..20) {
                    frequenciasMinutos[minutos] = (frequenciasMinutos[minutos] ?: 0) + 1
                }
            }
        }
        linha("INTERVALOS EM MINUTOS QUE SE REPETIRAM ENTRE 10X+  •  ÚLTIMOS 20 MIN", 13f, Color.rgb(100,205,255), true)
        val gradeMinutos = GridLayout(this).apply {
            columnCount = 10
            rowCount = 2
            useDefaultMargins = false
            setPadding(1, 2, 1, 2)
        }
        for (minuto in 1..20) {
            val contagem = frequenciasMinutos[minuto] ?: 0
            if (contagem == 0) continue
            val celula = TextView(this).apply {
                text = "${minuto} min\n${contagem}x"
                gravity = Gravity.CENTER
                textSize = 10f
                setTextColor(Color.WHITE)
                setPadding(1, 2, 1, 2)
                background = cartao(Color.rgb(28, 34, 48))
            }
            gradeMinutos.addView(celula, GridLayout.LayoutParams(
                GridLayout.spec((minuto - 1) / 10, 1),
                GridLayout.spec((minuto - 1) % 10, 1f)
            ).apply {
                width = 0
                height = (25 * resources.displayMetrics.density).toInt()
                setMargins(2, 2, 2, 2)
            })
        }
        if (frequenciasMinutos.isEmpty()) {
            linha("Nenhum intervalo em minutos se repetiu nesta janela de 20 minutos.", 11f, Color.LTGRAY)
        } else {
            quadroPadroes.addView(gradeMinutos)
        }
        // As faixas de minutagem continuam usando a janela móvel de 60 minutos.
        val rosasCrono = serie.indices.filter { serie[it] >= 10.0 }
        val registrosMinutagem10x = rosasCrono.mapIndexed { indice, idx ->
            val anteriorIdx = if (indice > 0) rosasCrono[indice - 1] else -1
            val casas = if (anteriorIdx >= 0) idx - anteriorIdx else 0
            val t0 = if (anteriorIdx >= 0) tempos.getOrNull(anteriorIdx) ?: 0L else 0L
            val t1 = tempos.getOrNull(idx) ?: 0L
            val intervalo = if (t0 > 0L && t1 > t0) tempo(t1 - t0) else "--"
            val casaTexto = if (anteriorIdx >= 0) "${casas}ª" else "—"
            Triple(casaTexto, "${serie[idx]}x", intervalo)
        }.asReversed().take(5)

        linha("ÚLTIMOS 5 RESULTADOS 10X+", 13f, Color.rgb(245,150,205), true)
        if (registrosMinutagem10x.isEmpty()) {
            linha("Aguardando resultados 10x+", 11f, Color.LTGRAY)
        } else {
            val grade10x = GridLayout(this).apply {
                columnCount = 5
                rowCount = 1
                useDefaultMargins = false
                setPadding(1, 2, 1, 2)
            }
            registrosMinutagem10x.forEachIndexed { posicao, registro ->
                val celula = TextView(this).apply {
                    text = "${registro.first}\n${registro.second}\n${registro.third}"
                    gravity = Gravity.CENTER
                    textSize = 9f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(1, 2, 1, 2)
                    maxLines = 3
                    background = cartao(Color.rgb(28, 34, 48))
                }
                grade10x.addView(celula, GridLayout.LayoutParams(
                    GridLayout.spec(0, 1),
                    GridLayout.spec(posicao, 1f)
                ).apply {
                    width = 0
                    height = (30 * resources.displayMetrics.density).toInt()
                    setMargins(2, 2, 2, 2)
                })
            }
            quadroPadroes.addView(grade10x)
        }



    }

    private data class EstatisticaPadrao(val ocorrencias:Int,val vitorias:Int,val derrotas:Int,val semGale:Int,val porGale:List<Int>,val maiorSequencia:Int) {
        val percentual:Double get()=if(ocorrencias==0)0.0 else vitorias*100.0/ocorrencias
    }
    private fun estatisticaPadrao(serie:List<Double>,padrao:List<Int>,alvo:Double,gales:Int,alvoMax:Double?=null):EstatisticaPadrao {
        if(padrao.isEmpty()||serie.size<padrao.size+1)return EstatisticaPadrao(0,0,0,0,List(gales+1){0},0)
        var total=0;var vitorias=0;var semGale=0;val porGale=MutableList(gales+1){0};val sinais=mutableListOf<Boolean>()
        for(inicio in 0..(serie.size-padrao.size-1)){
            var igual=true
            for(k in padrao.indices)if(categoriaValidador(serie[inicio+k])!=padrao[k]){igual=false;break}
            if(!igual)continue
            total++;var nivel=-1
            for(g in 0..gales){val idx=inicio+padrao.size+g;if(idx<serie.size&&serie[idx]>=alvo&&(alvoMax==null||serie[idx]<=alvoMax)){nivel=g;break}}
            val venceu=nivel>=0;sinais.add(venceu)
            if(venceu){vitorias++;porGale[nivel]++;if(nivel==0)semGale++}
        }
        var atual=0;var maior=0
        sinais.forEach{if(it){atual++;maior=maxOf(maior,atual)}else atual=0}
        return EstatisticaPadrao(total,vitorias,total-vitorias,semGale,porGale,maior)
    }
    private fun categoriaValidador(v:Double):Int=when{v<2.0->0;v<10.0->1;else->2}
    private fun nomeCor(id:Int)=when(id){0->"AZUL";1->"ROXO";else->"ROSA"}
    private fun descobrirPadroes(serie:List<Double>,alvo:Double,gales:Int,alvoMax:Double?=null):List<Pair<List<Int>,EstatisticaPadrao>> {
        if(serie.size<50)return emptyList()
        val corte=(serie.size*0.65).toInt();val treino=serie.take(corte);val teste=serie.drop(corte)
        val candidatos=mutableListOf<Pair<List<Int>,EstatisticaPadrao>>()
        for(tamanho in 2..5){
            val total=Math.pow(3.0,tamanho.toDouble()).toInt()
            for(codigo in 0 until total){
                var n=codigo;val padrao=MutableList(tamanho){0}
                for(i in tamanho-1 downTo 0){padrao[i]=n%3;n/=3}
                val hist=estatisticaPadrao(treino,padrao,alvo,gales,alvoMax)
                val valid=estatisticaPadrao(teste,padrao,alvo,gales,alvoMax)
                if(hist.ocorrencias>=6&&valid.ocorrencias>=4&&valid.percentual>=55.0)candidatos.add(padrao to valid)
            }
        }
        return candidatos.sortedWith(compareByDescending<Pair<List<Int>,EstatisticaPadrao>>{it.second.percentual}.thenByDescending{it.second.ocorrencias}).take(5)
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
