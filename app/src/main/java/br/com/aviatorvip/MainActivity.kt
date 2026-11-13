
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

    // Motor adaptativo experimental: descreve o histórico sem presumir que prevê a próxima rodada.
    private data class ResumoJanela(
        val total: Int,
        val abaixo2: Int,
        val de2a10: Int,
        val de10a50: Int,
        val acima50: Int,
        val media: Double
    )

    private fun resumirJanela(tamanho: Int): ResumoJanela {
        val janela = resultados.take(tamanho)
        return ResumoJanela(
            total = janela.size,
            abaixo2 = janela.count { it < 2.0 },
            de2a10 = janela.count { it >= 2.0 && it < 10.0 },
            de10a50 = janela.count { it >= 10.0 && it < 50.0 },
            acima50 = janela.count { it >= 50.0 },
            media = if (janela.isEmpty()) 0.0 else janela.average()
        )
    }

    private fun avaliarEstrategia() {
        if (!::painelEstrategia.isInitialized) return
        atualizarPainelEstrategia()
        atualizarPainelVisual()
        atualizarBibliotecaPadroes()
    }

    private fun atualizarPainelEstrategia() {
        val r20 = resumirJanela(20)
        val r50 = resumirJanela(50)
        val r100 = resumirJanela(100)
        painelEstrategia.textSize = 15f
        painelEstrategia.setLineSpacing(5f, 1.0f)
        painelEstrategia.text =
            "🧠 MOTOR ADAPTATIVO — EXPERIMENTAL\n" +
            "Histórico registrado: ${resultados.size} rodadas\n" +
            "Janela 20: ${r20.total} resultados | abaixo de 2x: ${r20.abaixo2} | 2x+: ${r20.total - r20.abaixo2}\n" +
            "Janela 50: ${r50.total} resultados | 10x+: ${r50.de10a50 + r50.acima50}\n" +
            "Janela 100: ${r100.total} resultados | 50x+: ${r100.acima50}\n\n" +
            "Os indicadores descrevem frequências observadas. Não representam previsão nem garantia da próxima rodada."
    }

    private fun atualizarPainelVisual() {
        if (!::painelEstatisticas.isInitialized || !::quadroEstrategias.isInitialized) return
        val r20 = resumirJanela(20)
        val r50 = resumirJanela(50)
        val r100 = resumirJanela(100)
        painelEstatisticas.text =
            "DISTRIBUIÇÃO POR JANELA\n" +
            "20 rodadas: <2x ${r20.abaixo2} • 2–9,99x ${r20.de2a10} • 10–49,99x ${r20.de10a50} • 50x+ ${r20.acima50}\n" +
            "50 rodadas: <2x ${r50.abaixo2} • 2–9,99x ${r50.de2a10} • 10–49,99x ${r50.de10a50} • 50x+ ${r50.acima50}\n" +
            "100 rodadas: <2x ${r100.abaixo2} • 2–9,99x ${r100.de2a10} • 10–49,99x ${r100.de10a50} • 50x+ ${r100.acima50}\n" +
            "Média nas últimas 100: ${String.format(Locale.US, "%.2f", r100.media)}x"

        quadroEstrategias.removeAllViews()
        val janelas = listOf(20, 50, 100)
        janelas.forEach { n ->
            val resumo = resumirJanela(n)
            val linha = TextView(this).apply {
                text = "JANELA ${n}: ${resumo.total}/${n} rodadas carregadas"
                textSize = 13f
                setTextColor(Color.WHITE)
                setPadding(8, 10, 8, 10)
            }
            quadroEstrategias.addView(linha)
        }
        val nota = TextView(this).apply {
            text = "Os 8 espaços de estratégia ficam reservados. Nenhum gatilho antigo está ativo nesta versão."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(8, 10, 8, 10)
        }
        quadroEstrategias.addView(nota)
    }

    private fun atualizarBibliotecaPadroes() {
        if (!::quadroPadroes.isInitialized) return
        quadroPadroes.removeAllViews()
        val janelas = listOf(20, 50, 100)
        janelas.forEach { n ->
            val resumo = resumirJanela(n)
            val taxaBaixa = if (resumo.total == 0) 0.0 else resumo.abaixo2 * 100.0 / resumo.total
            val taxaAlta = if (resumo.total == 0) 0.0 else (resumo.de10a50 + resumo.acima50) * 100.0 / resumo.total
            val bloco = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 8, 8, 8)
            }
            bloco.addView(TextView(this).apply {
                text = "JANELA ${n} RODADAS"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(100, 205, 255))
            })
            bloco.addView(TextView(this).apply {
                text = "Abaixo de 2x: ${String.format(Locale.US, "%.1f", taxaBaixa)}% | 10x ou mais: ${String.format(Locale.US, "%.1f", taxaAlta)}%"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(0, 4, 0, 0)
            })
            quadroPadroes.addView(bloco)
        }
        quadroPadroes.addView(TextView(this).apply {
            text = "MODO DE OBSERVAÇÃO: o motor compara janelas e registra frequências. Só haverá gatilhos depois de testes históricos e validação fora da amostra."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(8, 10, 8, 10)
        })
    }

    private fun atualizarHistorico() {
        // A faixa horizontal antiga foi removida; os resultados ficam na grade colorida.
    }

}
