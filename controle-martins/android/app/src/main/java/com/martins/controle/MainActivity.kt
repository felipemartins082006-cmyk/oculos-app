package com.martins.controle

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader

/**
 * O app é a página da pasta controle-martins/web/ rodando dentro de um WebView.
 * Ela conversa com o programa do computador por WebSocket (ws://endereço:47800).
 */
class MainActivity : ComponentActivity() {

  private lateinit var web: WebView
  private var fullscreen = false

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false

    web = WebView(this)
    web.layoutParams =
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    web.setBackgroundColor(getColor(R.color.page))
    setContentView(web)
    applyBars()

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
      domStorageEnabled = true // localStorage: lista de computadores pareados
      allowFileAccess = false
      cacheMode = WebSettings.LOAD_NO_CACHE
      textZoom = 100
      setSupportZoom(false)
      mediaPlaybackRequiresUserGesture = false
    }

    // Página servida por http (não https): assim ela pode abrir ws:// para o computador
    val loader =
        WebViewAssetLoader.Builder()
            .setHttpAllowed(true)
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

    web.addJavascriptInterface(Bridge(), "Android")

    // Voltar: fecha janelas / volta para a lista antes de sair do app
    onBackPressedDispatcher.addCallback(
        this,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            web.evaluateJavascript("window.appBack ? appBack() : false") { handled ->
              if (handled != "true") {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
              }
            }
          }
        })

    if (savedInstanceState != null) web.restoreState(savedInstanceState)
    else web.loadUrl("http://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/web/index.html")
  }

  // Barra de navegação escondida; em tela cheia some também a barra de status.
  private fun applyBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      hide(WindowInsetsCompat.Type.navigationBars())
      if (fullscreen) hide(WindowInsetsCompat.Type.statusBars())
      else show(WindowInsetsCompat.Type.statusBars())
      isAppearanceLightStatusBars = false
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) applyBars()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    web.saveState(outState)
  }

  inner class Bridge {
    // Tela do celular fica acesa enquanto estiver controlando um computador
    @JavascriptInterface
    fun keepScreenOn(on: Boolean) {
      runOnUiThread {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }

    @JavascriptInterface
    fun fullscreen(on: Boolean) {
      runOnUiThread {
        fullscreen = on
        applyBars()
      }
    }
  }
}
