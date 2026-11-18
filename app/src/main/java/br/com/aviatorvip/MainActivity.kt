
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

        val importarAirJet = Button(this).apply {
            text = "IMPORTAR HISTÓRICO AIRJET (ATÉ 50)"
            textSize = 10f
            isAllCaps = false
            setOnClickListener { solicitarImportacaoAirJet() }
            estilizarBotao(this, Color.rgb(20, 55, 75), Color.rgb(65, 190, 230))
        }
        tela.addView(importarAirJet, LinearLayout.LayoutParams(-1, (30 * resources.displayMetrics.density).toInt()).apply {
            bottomMargin = 3
        })

        val linhaStatus = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status = TextView(this).apply {
            text = "Aguardando captura..."
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
            setTextColor(Color.WHITE)
            setPadding(2, 2, 2, 2)
        }
        contadorDezX = TextView(this).apply {
            text = "10x: --"
            textSize = 20f
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
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(2, 2, 2, 2)
            contentDescription = "Relógio digital com horas, minutos e segundos"
        }
        val alturaLinhaStatus = (38 * resources.displayMetrics.density).toInt()
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
            rowCount = 2
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

    private fun solicitarImportacaoAirJet() {
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
            .setTitle("Importar histórico AirJet")
            .setMessage("A importação consulta a lista de jogos e depois busca até 50 rodadas. Isso usa 2 requisições da sua cota.")
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
                        status.text = "Conectando à GameData365..."
                        Thread {
                            try {
                                val api = GameData365Client(chave)
                                val gameId = api.findAirJetGameId()
                                val historicoApi = api.fetchAirJetHistory(gameId, limit = 50)
                                runOnUiThread {
                                    val preferencias = getSharedPreferences("aviator", MODE_PRIVATE)
                                    val idsSalvos = preferencias.getStringSet("airjet_round_ids", emptySet())
                                        ?.toMutableSet() ?: mutableSetOf()
                                    var adicionados = 0
                                    // A API entrega do mais recente para o mais antigo.
                                    // Inserir do mais antigo para o mais recente mantém o índice zero como atual.
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
                                    preferencias.edit()
                                        .putStringSet("airjet_round_ids", idsSalvos)
                                        .apply()
                                    salvar()
                                    atualizarHistorico()
                                    atualizarContadorDezX()
                                    atualizarGrade()
                                    avaliarEstrategia()
                                    atualizarBibliotecaPadroes()
                                    status.text = "AirJet: $adicionados rodadas importadas."
                                    Toast.makeText(
                                        this@MainActivity,
                                        "$adicionados resultados importados do AirJet.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (erro: Exception) {
                                runOnUiThread {
                                    status.text = "Falha ao importar AirJet."
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("Não foi possível importar")
                                        .setMessage(erro.message ?: "Erro de conexão com a GameData365.")
                                        .setPositiveButton("OK", null)
                                        .show()
                                }
                            }
                        }.start()
                    }
                }
                dialog.show()
            }
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
        atualizarMonitoramentoVip(valor)
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
        gradeResultados.removeAllViews()

        // Grade fixa de 18 células: duas linhas com nove posições explícitas.
        val densidade = resources.displayMetrics.density
        val valoresVisiveis = resultados.take(18)
        for (posicao in 0 until 18) {
            val valor = valoresVisiveis.getOrNull(posicao)
            val celula = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                if (valor != null) {
                    val cor = when {
                        valor < 2.0 -> Color.rgb(30, 145, 220)
                        valor < 10.0 -> Color.rgb(112, 65, 190)
                        valor < 40.0 -> Color.rgb(215, 55, 145)
                        else -> Color.rgb(185, 45, 100)
                    }
                    addView(TextView(this@MainActivity).apply {
                        text = "%.2fx".format(Locale.US, valor)
                        gravity = Gravity.CENTER
                        textSize = 18f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(cor)
                        maxLines = 1
                        setSingleLine(true)
                    })
                    if (valor >= 10.0) {
                        val horario = horarios.getOrNull(posicao) ?: "--:--:--"
                        addView(TextView(this@MainActivity).apply {
                            text = horario
                            gravity = Gravity.CENTER
                            textSize = 10f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            maxLines = 1
                            setSingleLine(true)
                        })
                    }
                }
            }
            val linha = posicao / 9
            val coluna = posicao % 9
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(linha, 1),
                GridLayout.spec(coluna, 1f)
            ).apply {
                width = 0
                height = (54 * densidade).toInt()
                setMargins(1, 2, 1, 2)
            }
            gradeResultados.addView(celula, lp)
        }
        botaoHistorico.text = "⌄ VER HISTÓRICO COMPLETO"
        botaoHistorico.visibility = View.VISIBLE
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
        quadroPadroes.removeAllViews(); quadroDezX.removeAllViews(); quadroDezX.visibility = View.GONE
        val cronologico = resultados.asReversed()
        val ultimas30 = cronologico.takeLast(30)
        val rosas = ultimas30.count { it >= 10.0 && it < 40.0 }
        val ultimoDez = (0 until (cronologico.size - 13).coerceAtLeast(0)).lastOrNull { cronologico[it] >= 10.0 } ?: -1
        val casas = listOf(2, 3, 4, 5, 9, 13)
        val valoresCasas = casas.mapNotNull { casa -> val idx = ultimoDez + casa; if (ultimoDez >= 0 && idx < cronologico.size) cronologico[idx] else null }
        val baixasEspelho = valoresCasas.count { it < 2.0 }
        val janela6 = cronologico.takeLast(6)
        val quebra = janela6.windowed(2).any { it[0] < 2.0 && it[1] in 3.0..5.99 }
        val completos = listOf(rosas >= 8, valoresCasas.size == 6 && baixasEspelho >= 2, quebra).count { it }
        val sinais = listOf(rosas >= 3, valoresCasas.isNotEmpty(), janela6.count { it < 2.0 } >= 2).count { it }
        val p = getSharedPreferences("aviator", MODE_PRIVATE)
        val azul=Color.rgb(85,175,255); val rosa=Color.rgb(235,105,185); val roxo=Color.rgb(175,125,255); val cinza=Color.rgb(180,192,210)
        fun txt(s:String,n:Float,cor:Int,bold:Boolean=false)=TextView(this).apply { text=s; textSize=n; setTextColor(cor); if(bold)setTypeface(null,android.graphics.Typeface.BOLD); setPadding(2,1,2,1) }
        quadroPadroes.addView(txt("ESTRATÉGIAS VIP • ALVO 3x",17f,Color.WHITE,true))
        val vipNomes=listOf("REBOTE","ESPELHO","ALTERNÂNCIA","QUEBRA DE SEQUÊNCIA","ROSA QUEBRANDO SEQUÊNCIA","REPETIÇÃO DE CASAS PÓS-ROSA")
        val vipCores=listOf(azul,roxo,rosa,azul,rosa,roxo)
        vipNomes.forEachIndexed { i,nome ->
            val ativo=p.getBoolean("vip_${i}_ativo",false)
            val restante=p.getInt("vip_${i}_restantes",3)
            val resultado=p.getString("vip_${i}_resultado","AGUARDANDO") ?: "AGUARDANDO"
            val estado=when { ativo->"AVALIANDO"; resultado=="SIM"->"POSSÍVEL ENTRADA"; resultado=="NÃO"->"INSUFICIENTE"; else->"AGUARDANDO" }
            val linha=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
                setPadding(8,4,8,4)
                background=cartao(Color.rgb(20,29,47))
            }
            linha.addView(txt(nome,13f,vipCores[i],true),LinearLayout.LayoutParams(0,-2,1f))
            val estadoTexto=if(ativo) "AVALIANDO • $restante" else estado
            linha.addView(txt(estadoTexto,12f,if(estado=="POSSÍVEL ENTRADA")Color.rgb(90,220,145) else if(estado=="INSUFICIENTE")cinza else Color.WHITE,true))
            quadroPadroes.addView(linha,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=3 })
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
