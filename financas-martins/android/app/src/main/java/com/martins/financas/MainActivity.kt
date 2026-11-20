package com.martins.financas

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import android.os.Bundle
import android.provider.OpenableColumns
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
  // Pasta do backup automático semanal (escolhida uma vez; o Android lembra a permissão)
  private val prefs by lazy { getSharedPreferences("backup", MODE_PRIVATE) }
  private val pickFolder =
      registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        var name: String? = null
        if (uri != null) {
          runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs.edit().putString("tree", uri.toString()).apply()
            name = folderName(uri) ?: "pasta escolhida"
          }
        }
        web.evaluateJavascript("window.appFolderPicked && appFolderPicked(${JSONObject.quote(name ?: "")} || null)", null)
      }

  private fun folderName(tree: Uri): String? {
    val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) return it.getString(0)
    }
    return null
  }

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

    // o toque longo fica com o app (selecionar vários no extrato), não com a seleção de texto do sistema
    web.setOnLongClickListener { true }
    web.isLongClickable = false
    web.isHapticFeedbackEnabled = false
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

          override fun onPageFinished(view: WebView, url: String) {
            pageReady = true
            deliverPendingFile()
          }
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
            // qualquer arquivo pode ser escolhido: o app confere o conteúdo (extrato ou backup)
            val types = if (params.acceptTypes.any { it.startsWith("image") }) arrayOf("image/*") else arrayOf("*/*")
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

    // garante que o backup de 12 em 12 horas continua agendado
    if (prefs.getBoolean("auto", false) && prefs.getString("tree", null) != null) BackupWorker.schedule(this)

    handleOpenIntent(intent)
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

  // ---- Arquivo aberto de fora ("Abrir com Finanças" / compartilhar com o app) ----
  private var pageReady = false
  private var pendingFile: Pair<String, String>? = null

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleOpenIntent(intent)
  }

  private fun handleOpenIntent(intent: Intent?) {
    val uri: Uri =
        when (intent?.action) {
          Intent.ACTION_VIEW -> intent.data
          Intent.ACTION_SEND ->
              if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
              else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
          else -> null
        } ?: return
    runCatching {
          var name = "arquivo"
          contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> name = n }
          }
          val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
          if (bytes.size > 40 * 1024 * 1024) error("arquivo grande demais")
          pendingFile = name to String(bytes, Charsets.UTF_8)
        }
        .onFailure { pendingFile = null }
    deliverPendingFile()
  }

  private fun deliverPendingFile() {
    val (name, text) = pendingFile ?: return
    if (!pageReady) return
    pendingFile = null
    web.evaluateJavascript(
        "window.appOpenFile ? appOpenFile(${JSONObject.quote(name)}, ${JSONObject.quote(text)}) : 0", null)
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

    @JavascriptInterface
    fun pickBackupFolder() {
      runOnUiThread { runCatching { pickFolder.launch(null) } }
    }

    // Grava um arquivo novo na pasta escolhida. Devolve false se a pasta sumiu ou perdeu a permissão.
    @JavascriptInterface
    fun writeBackup(name: String, content: String): Boolean = BackupWorker.write(this@MainActivity, content)

    // Cópia atualizada dos dados, para o backup de 12 em 12 horas funcionar com o app fechado
    @JavascriptInterface
    fun stashBackup(content: String) {
      runCatching {
        val f = BackupWorker.snapshot(this@MainActivity)
        val tmp = java.io.File(f.parentFile, f.name + ".tmp")
        tmp.writeText(content, Charsets.UTF_8)
        tmp.renameTo(f)
      }
    }

    // Liga/desliga o backup automático feito pelo sistema (a cada 12 horas)
    @JavascriptInterface
    fun setAutoBackup(on: Boolean) {
      prefs.edit().putBoolean("auto", on).apply()
      if (on) BackupWorker.schedule(this@MainActivity) else BackupWorker.cancel(this@MainActivity)
    }

    // Quando foi o último backup automático (milissegundos; 0 = nunca)
    @JavascriptInterface
    fun lastAutoBackup(): Double = prefs.getLong("lastAuto", 0L).toDouble()

    private fun save(name: String, mime: String, bytes: ByteArray) {
      runOnUiThread {
        pendingContent = bytes
        pendingMime = mime
        createDoc.launch(name)
      }
    }
  }
}
