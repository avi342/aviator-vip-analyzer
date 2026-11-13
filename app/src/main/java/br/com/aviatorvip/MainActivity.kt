
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
            text = "CENTRAL DE ESTRATÉGIAS  •  8 SLOTS"
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
        val prefs = getSharedPreferences("aviator", MODE_PRIVATE)
        val rosas = contarRosasDesdeUltimo40x()
        val jaDisparou = prefs.getBoolean("ciclo_rosa_disparado", false)

        if (rosas >= 8 && !jaDisparou) {
            prefs.edit()
                .putBoolean("ciclo_rosa_disparado", true)
                .putString("ciclo_rosa_ultimo_sinal", System.currentTimeMillis().toString())
                .apply()
            Toast.makeText(
                this,
                "ALERTA: Estratégia 01 — 8 resultados rosas nas últimas 300 rodadas",
                Toast.LENGTH_LONG
            ).show()
        } else if (rosas < 8 && jaDisparou) {
            prefs.edit().putBoolean("ciclo_rosa_disparado", false).apply()
        }

        atualizarPainelEstrategia(rosas)
        atualizarPainelVisual(rosas)
    }

    private fun atualizarPainelVisual(rosas: Int) {
        val janela = resultados.take(300)
        val azuis = janela.count { it < 2.0 }
        val roxos = janela.count { it >= 2.0 && it < 10.0 }
        val altos = janela.count { it >= 40.0 }
        val media = if (janela.isEmpty()) 0.0 else janela.average()
        painelEstatisticas.text =
            "RESUMO DAS ÚLTIMAS " + janela.size + " RODADAS\n" +
            "Azuis: " + azuis + "  •  Roxos: " + roxos + "\n" +
            "Rosas no ciclo atual: " + rosas + "  •  40x+: " + altos + "\n" +
            "Média da janela: " + "%.2fx".format(Locale.US, media)

        quadroEstrategias.removeAllViews()
        for (i in 1..8) {
            val linha = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 12, 8, 12)
            }
            val numero = TextView(this).apply {
                text = "%02d".format(i)
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (i == 1) Color.rgb(255, 90, 175) else Color.rgb(135, 150, 175))
                setPadding(4, 0, 18, 0)
            }
            val descricao = TextView(this).apply {
                text = if (i == 1) "CICLO ROSA  •  " + rosas + "/8 resultados rosas" else "Disponível para configurar"
                textSize = 13f
                setTextColor(if (i == 1) Color.WHITE else Color.rgb(145, 155, 175))
            }
            linha.addView(numero)
            linha.addView(descricao)
            quadroEstrategias.addView(linha)
            if (i < 8) {
                val divisor = android.view.View(this).apply {
                    setBackgroundColor(Color.rgb(48, 56, 75))
                }
                quadroEstrategias.addView(divisor, LinearLayout.LayoutParams(-1, 1))
            }
        }
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

    private fun atualizarPainelEstrategia(rosas: Int = resultados.take(300).count { it >= 10.0 && it < 40.0 }) {
        val ativa = getSharedPreferences("aviator", MODE_PRIVATE)
            .getBoolean("ciclo_rosa_disparado", false)
        painelEstrategia.textSize = 16f
        painelEstrategia.setLineSpacing(5f, 1.0f)
        painelEstrategia.text =
            "📊 ESTRATÉGIA 01 — CICLO ROSA\n" +
            "Rosas de 10x a 39,99x (últimas 300): $rosas\n" +
            (if (ativa) "🟢 Critério ativo" else "⏳ Aguardando condição") +
            "\n\n⏱️ ESTRATÉGIA 04 — CICLO CRONOLÓGICO\n" +
            analisarCicloCronologico()
    }


    private fun atualizarBibliotecaPadroes() {
        if (!::quadroPadroes.isInitialized) return
        quadroPadroes.removeAllViews()
        adicionarReconhecedoresExpandidos()
        val dados = (0 until minOf(resultados.size, marcasTempo.size))
            .map { i -> Pair(resultados[i], marcasTempo[i]) }
            .filter { it.second > 0L }.asReversed()
        val salvos = getSharedPreferences("aviator", MODE_PRIVATE)
            .getStringSet("biblioteca_padroes", emptySet())?.toMutableSet() ?: mutableSetOf()
        for (i in dados.indices) {
            if (dados[i].first < 10.0) continue
            var j = i + 1
            var baixo = false
            var alto = false
            while (j < dados.size && dados[j].first < 10.0) {
                val v = dados[j].first
                if (v <= 1.50) baixo = true
                if (v in 2.41..6.00) alto = true
                j++
            }
            if (j >= dados.size || !baixo || !alto) continue
            val minutos = kotlin.math.round((dados[j].second - dados[i].second) / 60000.0).toLong()
            if (minutos in listOf(3L, 4L, 7L, 9L)) {
                val sequencia = dados.subList(i + 1, j).joinToString(",") {
                    String.format(Locale.US, "%.2f", it.first)
                }
                if (sequencia.isNotBlank()) salvos.add(sequencia)
            }
        }
        getSharedPreferences("aviator", MODE_PRIVATE).edit()
            .putStringSet("biblioteca_padroes", salvos).apply()
        if (salvos.isEmpty()) {
            quadroPadroes.addView(TextView(this).apply {
                text = "Nenhum padrão completo identificado ainda."
                textSize = 13f
                setTextColor(Color.LTGRAY)
            })
            return
        }
        salvos.sorted().takeLast(20).forEachIndexed { idx, seq ->
            val linha = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 7, 0, 7)
            }
            linha.addView(TextView(this).apply {
                text = "PADRÃO ${idx + 1}"
                textSize = 10f
                setTextColor(Color.LTGRAY)
            })
            val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            seq.split(",").forEach { n ->
                val v = n.toDoubleOrNull() ?: return@forEach
                val cor = when {
                    v < 2.0 -> Color.rgb(30, 145, 220)
                    v < 10.0 -> Color.rgb(112, 65, 190)
                    else -> Color.rgb(215, 55, 145)
                }
                val chip = TextView(this@MainActivity).apply {
                    text = "${String.format(Locale.US, "%.2f", v)}x"
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(5, 5, 5, 5)
                    background = GradientDrawable().apply {
                        setColor(cor)
                        cornerRadius = 5f
                    }
                }
                chips.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = 5 })
            }
            linha.addView(chips)
            quadroPadroes.addView(linha)
            quadroPadroes.addView(View(this).apply {
                setBackgroundColor(Color.rgb(45, 58, 78))
            }, LinearL
    // Reconhecedores ampliados: observações e candidatos não são previsões.
    private fun adicionarReconhecedoresExpandidos() {
        val recentes = resultados.take(6)
        val rosas = resultados.take(300).count { it >= 10.0 && it < 40.0 }
        val rosaIndex = resultados.indexOfFirst { it >= 10.0 }
        val depoisDaRosa = if (rosaIndex > 0) resultados.take(rosaIndex).reversed() else emptyList()
        val casas = listOf(2, 3, 4, 5, 9, 13).mapNotNull { p -> depoisDaRosa.getOrNull(p - 1)?.let { p to it } }
        val baixasEspelho = casas.count { it.second < 2.0 }
        val mediasEspelho = casas.count { it.second >= 2.0 && it.second < 10.0 }
        val baixas = recentes.count { it < 2.0 }
        val medias = recentes.count { it >= 2.0 && it < 10.0 }
        val cronologico = resultados.take(8).reversed()
        val baixoAlto = cronologico.zipWithNext().any { (a, b) -> a < 2.0 && b in 3.0..5.99 }
        val recuperacao = recentes.size >= 4 && baixas >= 2 && medias >= 1
        val espelhoAtivo = baixasEspelho + mediasEspelho >= 2
        val ativos = listOf(rosas >= 3, espelhoAtivo, baixoAlto, recuperacao).count { it }
        data class Sinal(val nome: String, val nivel: String, val detalhe: String)
        val sinais = listOf(
            Sinal("01 · CICLO ROSA", when { rosas >= 8 -> "CRITÉRIO ATINGIDO"; rosas >= 5 -> "CANDIDATO"; rosas >= 3 -> "OBSERVAÇÃO"; else -> "SEM SINAL" }, "$rosas/8 rosas de 10x a 39,99x nas últimas 300 rodadas."),
            Sinal("02 · ESPELHO DAS CASAS", when { casas.size >= 6 && baixasEspelho >= 2 -> "CRITÉRIO ATINGIDO"; espelhoAtivo -> "CANDIDATO"; rosaIndex >= 0 -> "OBSERVAÇÃO"; else -> "SEM SINAL" }, if (rosaIndex < 0) "Aguardando resultado de 10x+." else "Casas: ${casas.size}/6; baixas: $baixasEspelho; intermediárias: $mediasEspelho."),
            Sinal("03 · BAIXO + ALTO", if (baixoAlto) "CANDIDATO" else if (baixas >= 2) "OBSERVAÇÃO" else "SEM SINAL", "Últimas 6: $baixas azuis e $medias roxos; busca baixa seguida de 3x–5,99x."),
            Sinal("04 · CICLO DE RECUPERAÇÃO", if (recuperacao) "CANDIDATO" else if (baixas > 0 && medias > 0) "OBSERVAÇÃO" else "SEM SINAL", "Últimas 6 rodadas: $baixas azuis e $medias roxos."),
            Sinal("05 · GATILHO COMBINADO", when { ativos >= 3 -> "CRITÉRIO ATINGIDO"; ativos >= 2 -> "CANDIDATO"; ativos == 1 -> "OBSERVAÇÃO"; else -> "SEM SINAL" }, "$ativos reconhecedores com sinal ativo; não é previsão da próxima rodada.")
        )
        sinais.forEach { s ->
            val cor = when (s.nivel) {
                "CRITÉRIO ATINGIDO" -> Color.rgb(75, 220, 150)
                "CANDIDATO" -> Color.rgb(100, 190, 255)
                "OBSERVAÇÃO" -> Color.rgb(255, 200, 90)
                else -> Color.LTGRAY
            }
            val bloco = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 9, 8, 9) }
            bloco.addView(TextView(this).apply { text = "${s.nome}  •  ${s.nivel}"; textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(cor) })
            bloco.addView(TextView(this).apply { text = s.detalhe; textSize = 12f; setTextColor(Color.WHITE); setPadding(0, 4, 0, 0) })
            quadroPadroes.addView(bloco)
        }
        quadroPadroes.addView(TextView(this).apply {
            text = "Sinais estatísticos do histórico; não garantem a próxima rodada."
            textSize = 11f; setTextColor(Color.LTGRAY); setPadding(8, 8, 8, 12)
        })
    }

ayout.LayoutParams(-1, 1))
        }
    }

    private fun analisarCicloCronologico(): String {
        val n = minOf(resultados.size, marcasTempo.size)
        if (n < 3) return "Ainda coletando rodadas com horário automático."
        val dados = (0 until n).map { i -> Pair(resultados[i], marcasTempo[i]) }
            .filter { it.second > 0L }.asReversed()
        if (dados.size < 3) return "Histórico antigo sem horário exato não entra nesta análise."

        val alvos = listOf(3L, 4L, 7L, 9L)
        val ocorrencias = mutableMapOf<Long, Int>()
        var ciclosCompletos = 0
        for (i in dados.indices) {
            if (dados[i].first < 10.0) continue
            var j = i + 1
            var baixo = false
            var alto = false
            while (j < dados.size && dados[j].first < 10.0) {
                val v = dados[j].first
                if (v <= 1.50) baixo = true
                if (v in 2.41..6.00) alto = true
                j++
            }
            if (j >= dados.size || !baixo || !alto) continue
            val deltaMs = dados[j].second - dados[i].second
            if (deltaMs <= 0L) continue
            // Arredonda o intervalo para o minuto mais próximo: 3m30s passa a 4 min.
            val minutos = kotlin.math.round(deltaMs / 60000.0).toLong()
            if (minutos in alvos) {
                ciclosCompletos++
                ocorrencias[minutos] = (ocorrencias[minutos] ?: 0) + 1
            }
        }

        val ultimaRosa = dados.indexOfLast { it.first >= 10.0 }
        if (ultimaRosa < 0) return "Ainda não há rosa registrada com horário."
        val desde = dados.drop(ultimaRosa + 1)
        val baixoAtual = desde.any { it.first <= 1.50 }
        val altoAtual = desde.any { it.first in 2.41..6.00 }
        val segundos = ((System.currentTimeMillis() - dados[ultimaRosa].second) / 1000L).coerceAtLeast(0L)
        val minutosDecorridos = segundos / 60L
        val faixaAtual = when {
            baixoAtual && altoAtual -> "🟢 BAIXO + ALTO encontrados"
            baixoAtual -> "🟡 Baixo encontrado; falta um alto (2,41x–6x)"
            altoAtual -> "🟡 Alto encontrado; falta um baixo (até 1,50x)"
            else -> "🔵 Aguardando baixo e alto"
        }
        val proximo = alvos.firstOrNull { it > minutosDecorridos }
        val historicoTexto = if (ocorrencias.isEmpty()) "Ainda sem ciclos completos confirmados." else
            ocorrencias.toSortedMap().entries.joinToString("   •   ") { "${it.key} min: ${it.value}x" }

        return "ÚLTIMA ROSA: ${horarios.getOrNull(resultados.indices.lastOrNull { resultados[it] >= 10.0 } ?: 0) ?: "--:--:--"}\n" +
            "Tempo desde a rosa: ${minutosDecorridos}min ${segundos % 60}s\n" +
            "$faixaAtual\n" +
            "Ciclos históricos com baixo + alto: $ciclosCompletos\n" +
            "Frequência: $historicoTexto\n" +
            (if (proximo != null) "Próxima faixa a observar: ${proximo} min" else "Faixas 3, 4, 7 e 9 min já passaram") +
            "\n\n⚠️ Frequência histórica não garante a próxima rosa."
    }

    private fun atualizarHistorico() {
        // A faixa horizontal antiga foi removida; os resultados ficam na grade colorida.
    }

}
