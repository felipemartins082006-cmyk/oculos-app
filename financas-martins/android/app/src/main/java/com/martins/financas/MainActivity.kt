package com.martins.financas

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Base64
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader

/**
 * O app é a versão web (pasta financas-martins/) rodando dentro de um WebView,
 * servida a partir do próprio APK. Não usa internet: os dados ficam salvos no celular.
 */
class MainActivity : ComponentActivity() {

  private lateinit var web: WebView

  // Seleção de extratos (vários arquivos de uma vez)
  private var filesCallback: ValueCallback<Array<Uri>>? = null
  private val pickFiles =
      registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        filesCallback?.onReceiveValue(uris.toTypedArray())
        filesCallback = null
      }

  // Salvar backup / planilha: o Android pergunta onde guardar
  private var pendingContent: ByteArray? = null
  private var pendingMime = "application/octet-stream"
  private val createDoc =
      registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val content = pendingContent
        pendingContent = null
        if (uri == null || content == null) return@registerForActivityResult
        val ok =
            runCatching {
                  contentResolver.openOutputStream(uri)!!.use { it.write(content) }
                }
                .isSuccess
        web.evaluateJavascript("window.appSaved && appSaved($ok)", null)
      }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // App sempre escuro: ícones claros na barra de status
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
    hideNavigationBar()

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
      domStorageEnabled = true // localStorage: onde ficam os lançamentos
      allowFileAccess = false
      allowContentAccess = true
      cacheMode = WebSettings.LOAD_NO_CACHE
      textZoom = 100
      setSupportZoom(false)
    }

    // Serve os arquivos do APK num endereço https fixo, para o armazenamento ficar sempre no mesmo lugar
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

    web.webChromeClient =
        object : WebChromeClient() {
          override fun onShowFileChooser(
              webView: WebView,
              callback: ValueCallback<Array<Uri>>,
              params: FileChooserParams,
          ): Boolean {
            filesCallback?.onReceiveValue(null)
            filesCallback = callback
            val types =
                if (params.acceptTypes.any { it.contains("json") }) arrayOf("application/json", "*/*")
                else
                    arrayOf(
                        "application/pdf",
                        "text/*",
                        "text/csv",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.oasis.opendocument.spreadsheet",
                        "application/x-ofx",
                        "application/octet-stream",
                        "*/*",
                    )
            runCatching { pickFiles.launch(types) }
                .onFailure {
                  filesCallback = null
                  return false
                }
            return true
          }
        }

    web.addJavascriptInterface(Bridge(), "Android")

    // Voltar: fecha janelas e volta ao Panorama antes de sair do app
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
    else web.loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/web/index.html")
  }

  // Esconde a barra de navegação do Android (voltar / início / recentes).
  // Ela reaparece por um instante ao deslizar o dedo a partir da borda de baixo.
  private fun hideNavigationBar() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      hide(WindowInsetsCompat.Type.navigationBars())
      isAppearanceLightStatusBars = false
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) hideNavigationBar()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    web.saveState(outState)
  }

  inner class Bridge {
    @JavascriptInterface
    fun saveFile(name: String, mime: String, content: String) {
      save(name, mime, content.toByteArray(Charsets.UTF_8))
    }

    // Planilha Excel (binária) vem da página em base64
    @JavascriptInterface
    fun saveBase64(name: String, mime: String, base64: String) {
      save(name, mime, Base64.decode(base64, Base64.DEFAULT))
    }

    private fun save(name: String, mime: String, bytes: ByteArray) {
      runOnUiThread {
        pendingContent = bytes
        pendingMime = mime
        createDoc.launch(name)
      }
    }
  }
}
