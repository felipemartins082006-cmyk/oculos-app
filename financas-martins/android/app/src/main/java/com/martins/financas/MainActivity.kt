package com.martins.financas

import android.annotation.SuppressLint
import android.content.res.Configuration
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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
  private var pendingContent: String? = null
  private var pendingMime = "application/octet-stream"
  private val createDoc =
      registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val content = pendingContent
        pendingContent = null
        if (uri == null || content == null) return@registerForActivityResult
        val ok =
            runCatching {
                  contentResolver.openOutputStream(uri)!!.use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                  }
                }
                .isSuccess
        web.evaluateJavascript("window.appSaved && appSaved($ok)", null)
      }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

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

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    web.saveState(outState)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    web.setBackgroundColor(getColor(R.color.page))
  }

  inner class Bridge {
    @JavascriptInterface
    fun saveFile(name: String, mime: String, content: String) {
      runOnUiThread {
        pendingContent = content
        pendingMime = mime
        createDoc.launch(name)
      }
    }
  }
}
