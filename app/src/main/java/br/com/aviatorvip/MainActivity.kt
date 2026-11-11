
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
import android.view.Gravity
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var entrada: EditText
    private lateinit var historico: TextView

    private val resultados = mutableListOf<Double>()
    private var candidatoOCR: Double? = null
    private var leiturasDoCandidato = 0
    private var ultimoResultadoAutomatico: Double? = null

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

        val titulo = TextView(this).apply {
            text = "AVIATOR VIP ANALYZER"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 65, 145))
        }
        tela.addView(titulo)

        val iniciar = Button(this).apply {
            text = "INICIAR CAPTURA OCR"
            setOnClickListener { solicitarCaptura() }
        }
        tela.addView(iniciar)

        val parar = Button(this).apply {
            text = "PARAR CAPTURA"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
                status.text = "Captura parada."
            }
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
            text = "REGISTRAR RESULTADO"
            setOnClickListener { registrarManual() }
        }
        tela.addView(registrar)

        historico = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
        }

        val rolagem = ScrollView(this)
        rolagem.addView(historico)
        tela.addView(rolagem, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(tela)
        atualizarHistorico()
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

    override fun onStop() {
        try {
            unregisterReceiver(receptor)
        } catch (_: Exception) {
        }
        super.onStop()
    }

    private fun processarOCR(texto: String) {
        val valor = padrao.findAll(texto).lastOrNull()
            ?.groupValues?.get(1)
            ?.replace(",", ".")
            ?.toDoubleOrNull()

        if (valor == null || valor < 1.0 || valor > 100000.0) {
            candidatoOCR = null
            leiturasDoCandidato = 0
            ultimoResultadoAutomatico = null
            status.text = "OCR ativo — aguardando multiplicador..."
            return
        }

        if (candidatoOCR == valor) {
            leiturasDoCandidato++
        } else {
            candidatoOCR = valor
            leiturasDoCandidato = 1
        }

        status.text = "Identificado %.2fx — confirmando...".format(Locale.US, valor)

        if (leiturasDoCandidato >= 2 && ultimoResultadoAutomatico != valor) {
            resultados.add(0, valor)
            salvar()
            atualizarHistorico()
            ultimoResultadoAutomatico = valor
            status.text = "Registrado automaticamente: %.2fx".format(Locale.US, valor)
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
    }

    private fun salvar() {
        getSharedPreferences("aviator", MODE_PRIVATE)
            .edit()
            .putString("resultados", resultados.joinToString(","))
            .apply()
    }

    private fun atualizarHistorico() {
        historico.text = resultados.take(100)
            .mapIndexed { i, valor ->
                "${i + 1}. %.2fx".format(Locale.US, valor)
            }.joinToString("\n")
    }
}
