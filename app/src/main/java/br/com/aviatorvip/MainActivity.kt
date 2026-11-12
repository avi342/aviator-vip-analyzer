
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
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var entrada: EditText
    
private lateinit var historico: LinearLayout
    private lateinit var painelEstrategia: TextView
    private lateinit var painelEstatisticas: TextView
    private lateinit var quadroEstrategias: LinearLayout
    
    private val resultados = mutableListOf<Double>()
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

        entrada = EditText(this).apply {
            hint = "Multiplicador manual (ex.: 2,50)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            inputType = 8194
        }
        tela.addView(entrada)

        val registrar = Button(this).apply {
            text = "＋  REGISTRAR RESULTADO"
            setOnClickListener { registrarManual() }
            estilizarBotao(this, Color.rgb(42, 48, 85), Color.rgb(125, 145, 255))
        }
        tela.addView(registrar)

      
historico = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

val rolagem = HorizontalScrollView(this).apply {
    isHorizontalScrollBarEnabled = true
    addView(historico)
}

tela.addView(
    rolagem,
    LinearLayout.LayoutParams(-1, 90)
)

        painelEstrategia = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(16, 18, 16, 18)
            background = cartao(Color.rgb(27, 35, 58))
        }
        tela.addView(painelEstrategia)

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
        avaliarEstrategia()
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
        salvar()
        atualizarHistorico()
        avaliarEstrategia()
        rodadaRegistrada = true
        status.text = "Resultado registrado: $valor x"
    }
}
private fun registrarManual() {
       
    val valor = entrada.text.toString()
            .replace(",", ".").toDoubleOrNull()

        if (valor == null || valor < 1) {
            Toast.makeText(this, "Multiplicador inválido", Toast.LENGTH_SHORT).show()
            return
        }

        resultados.add(0, valor)
        salvar()
        entrada.text.clear()
        atualizarHistorico()
        avaliarEstrategia()
    }

    private fun salvar() {
        getSharedPreferences("aviator", MODE_PRIVATE)
            .edit()
            .putString("resultados", resultados.joinToString(","))
            .apply()
    }

    private fun avaliarEstrategia() {
        val prefs = getSharedPreferences("aviator", MODE_PRIVATE)
        val rosas = resultados.take(300).count { it >= 10.0 && it < 40.0 }
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
            "Rosas (10x–39,99x): " + rosas + "  •  40x+: " + altos + "\n" +
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
                text = if (i == 1) "CICLO ROSA  •  " + rosas + "/8 resultados" else "Disponível para configurar"
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
        painelEstrategia.text = "ESTRATÉGIA 01 — CICLO ROSA\n" +
            "Janela: últimas 300 rodadas\n" +
            "Resultados rosas (10x a 39,99x): $rosas de 8\n" +
            if (ativa) "SINAL DISPARADO — observe antes de decidir" else "Aguardando completar 8 resultados"
    }

    private fun atualizarHistorico() {
        historico.removeAllViews()

resultados.take(100).forEach { valor ->

    val cor = when {
        valor < 2.0 -> Color.rgb(48, 160, 230)
        valor < 10.0 -> Color.rgb(145, 70, 220)
        else -> Color.rgb(236, 69, 160)
    }

    val item = TextView(this).apply {
        text = "%.2fx".format(Locale.US, valor)
        setTextColor(cor)
        textSize = 16f
        setPadding(18, 12, 18, 12)
        setTypeface(null, android.graphics.Typeface.BOLD)
    }

    val parametros = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    parametros.marginEnd = 8
    historico.addView(item, parametros)
}
    }
}
