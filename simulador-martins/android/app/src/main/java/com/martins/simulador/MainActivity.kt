package com.martins.simulador

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader

/**
 * O app é a versão web (pasta simulador-martins/) rodando num WebView, servida de dentro do APK.
 * Não usa internet: o histórico de preços vem em data.js e a carteira fica salva no celular.
 */
class MainActivity : ComponentActivity() {

  private lateinit var web: WebView

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Segue o tema do celular (claro/escuro), como a página
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
    )
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false

    web = WebView(this)
    web.layoutParams =
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    web.setBackgroundColor(getColor(R.color.page))
    setContentView(web)

    // Barras do sistema e teclado: a página fica sempre na área visível
    ViewCompat.setOnApplyWindowInsetsListener(web) { v, insets ->
      val bars =
          insets.getInsets(
              WindowInsetsCompat.Type.systemBars() or
                  WindowInsetsCompat.Type.displayCutout() or
                  WindowInsetsCompat.Type.ime())
      v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      WindowInsetsCompat.CONSUMED
    }

    web.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true // localStorage: guarda a carteira e o plano
      allowFileAccess = false
      allowContentAccess = false
      cacheMode = WebSettings.LOAD_NO_CACHE
      textZoom = 100
      setSupportZoom(false)
    }

    // Endereço https fixo para o armazenamento ficar sempre no mesmo lugar
    val loader =
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    web.webViewClient =
        object : WebViewClient() {
          override fun shouldInterceptRequest(
              view: WebView,
              request: WebResourceRequest,
          ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

          // A página não navega para fora do app
          override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
              request.url.host != WebViewAssetLoader.DEFAULT_DOMAIN
        }

    if (savedInstanceState != null) web.restoreState(savedInstanceState)
    else web.loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/web/index.html")
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    web.setBackgroundColor(getColor(R.color.page))
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    web.saveState(outState)
  }
}
