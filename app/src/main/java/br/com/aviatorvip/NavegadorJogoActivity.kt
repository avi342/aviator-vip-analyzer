package br.com.aviatorvip

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.widget.Toast

class NavegadorJogoActivity : Activity() {
    private var navegador: WebView? = null
    private lateinit var estado: TextView

    private val urlJogo = "https://www.apostaboom.bet/casino/spribe/aviator-vip"

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
            text = "Preparando navegador..."
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 6, 8, 6)
        }
        val abrirChrome = Button(this).apply {
            text = "ABRIR APOSTABOOM NO CHROME"
            setOnClickListener { abrirNoNavegadorExterno() }
        }
        barra.addView(voltar)
        barra.addView(estado)
        barra.addView(abrirChrome)
        tela.addView(barra)

        setContentView(tela)

        try {
            val web = WebView(this)
            navegador = web
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.settings.loadsImagesAutomatically = true
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    estado.text = "Página carregada. Faça login e abra o Aviator."
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        estado.text = "Não foi possível carregar a página. Tente pelo Chrome."
                    }
                }
            }
            web.webChromeClient = WebChromeClient()
            tela.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
            estado.text = "Abrindo ApostaBoom..."
            web.loadUrl(urlJogo)
        } catch (e: Exception) {
            estado.text = "Navegador interno indisponível neste celular."
            Toast.makeText(this, "Não foi possível iniciar o navegador interno.", Toast.LENGTH_LONG).show()
        }
    }

    private fun abrirNoNavegadorExterno() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlJogo)))
        } catch (e: Exception) {
            Toast.makeText(this, "Não encontrei um navegador para abrir o site.", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val web = navegador
        if (web != null && web.canGoBack()) {
            web.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        navegador?.let {
            it.stopLoading()
            it.removeAllViews()
            it.destroy()
        }
        navegador = null
        super.onDestroy()
    }
}
