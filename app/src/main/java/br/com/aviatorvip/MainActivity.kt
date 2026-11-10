package br.com.aviatorvip

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val resultados = mutableListOf<Double>()
    private lateinit var entrada: EditText
    private lateinit var estatisticas: TextView
    private lateinit var historico: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferencias = getSharedPreferences("aviator", MODE_PRIVATE)
        val salvos = preferencias.getString("resultados", "") ?: ""

        if (salvos.isNotEmpty()) {
            salvos.split(",").forEach {
                it.toDoubleOrNull()?.let { valor ->
                    resultados.add(valor)
                }
            }
        }

        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 20)
            setBackgroundColor(Color.rgb(15, 20, 32))
        }

        val titulo = TextView(this).apply {
            text = "AVIATOR VIP ANALYZER"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.RED)
        }

        tela.addView(titulo)

        entrada = EditText(this).apply {
            hint = "Digite o multiplicador (ex.: 2,50)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            inputType = 8194
        }

        tela.addView(entrada)

        val botao = Button(this).apply {
            text = "REGISTRAR RESULTADO"
            setOnClickListener { registrar() }
        }

        tela.addView(botao)

        estatisticas = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 20, 0, 20)
        }

        tela.addView(estatisticas)

        historico = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
        }

        val rolagem = ScrollView(this)
        rolagem.addView(historico)
        tela.addView(rolagem, LinearLayout.LayoutParams(-1, 0, 1f))

        val limpar = Button(this).apply {
            text = "LIMPAR HISTÓRICO"
            setOnClickListener {
                resultados.clear()
                salvar()
                atualizar()
            }
        }

        tela.addView(limpar)

        setContentView(tela)
        atualizar()
    }

    private fun registrar() {
        val valor = entrada.text.toString()
            .replace(",", ".").toDoubleOrNull()

        if (valor == null || valor < 1) {
            Toast.makeText(
                this,
                "Digite um multiplicador válido",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        resultados.add(0, valor)
        salvar()
        entrada.text.clear()
        atualizar()
    }

    private fun salvar() {
        getSharedPreferences("aviator", MODE_PRIVATE)
            .edit()
            .putString("resultados", resultados.joinToString(","))
            .apply()
    }

    private fun atualizar() {
        val media = if (resultados.isEmpty()) 0.0
            else resultados.average()

        val acima2 = resultados.count { it >= 2.0 }
        val acima10 = resultados.count { it >= 10.0 }

        estatisticas.text =
            "Rodadas registradas: ${resultados.size}\n" +
            "Média: %.2fx\n".format(Locale.US, media) +
            "Resultados acima de 2x: $acima2\n" +
            "Resultados acima de 10x: $acima10\n\n" +
            "Estatísticas do histórico, não previsões."

        historico.text = resultados.take(100)
            .mapIndexed { indice, valor ->
                "${indice + 1}. %.2fx".format(Locale.US, valor)
            }.joinToString("\n")
    }
}
