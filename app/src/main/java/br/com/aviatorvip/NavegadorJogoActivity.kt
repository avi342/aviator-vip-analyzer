package br.com.aviatorvip

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color

class NavegadorJogoActivity : Activity() {
    private lateinit var navegador: WebView
    private lateinit var estado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 20, 32))
        }

        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
        }

        val voltar = Button(this).apply {
            text = "← VOLTAR AO ANALYZER"
            setOnClickListener { finish() }
        }
        estado = TextView(this).apply {
            text = "Abrindo ApostaBoom..."
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 6, 8, 6)
        }
        barra.addView(voltar)
        barra.addView(estado)
        tela.addView(barra)

        navegador = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    estado.text = "Página carregada. Faça login e abra o Aviator."
                }
            }
            webChromeClient = WebChromeClient()
        }
        tela.addView(navegador, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(tela)
        navegador.loadUrl("https://www.apostaboom.bet/casino/spribe/aviator-vip")
    }

    override fun onBackPressed() {
        if (::navegador.isInitialized && navegador.canGoBack()) {
            navegador.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::navegador.isInitialized) {
            navegador.stopLoading()
            navegador.destroy()
        }
        super.onDestroy()
    }
}
