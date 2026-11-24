package com.martins.baixa

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

/**
 * O app é a página da pasta baixa-martins/web/ rodando dentro de um WebView.
 * Ela conversa com o programa do computador (http://endereço:47900).
 * Aqui ficam as partes que só o Android faz: mini tela (picture-in-picture), tocar em segundo
 * plano com notificação, salvar arquivos no celular e receber links do app do YouTube.
 */
class MainActivity : ComponentActivity() {

  private lateinit var web: WebView
  private var fullscreen = false
  private var playing = false
  private var kind = "audio"
  private var aspect = Rational(16, 9)
  private var pendingShare: String? = null
  private var pageReady = false

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

    ViewCompat.setOnApplyWindowInsetsListener(web) { v, insets ->
      val bars =
          insets.getInsets(
              WindowInsetsCompat.Type.systemBars() or
                  WindowInsetsCompat.Type.displayCutout() or
                  WindowInsetsCompat.Type.ime())
      if (isInPictureInPictureMode) v.setPadding(0, 0, 0, 0)
      else v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      WindowInsetsCompat.CONSUMED
    }

    web.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true // localStorage: endereço do computador e chave de acesso
      allowFileAccess = false
      cacheMode = WebSettings.LOAD_DEFAULT
      textZoom = 100
      setSupportZoom(false)
      mediaPlaybackRequiresUserGesture = false
      mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    }
    web.webChromeClient = WebChromeClient()

    // Página servida por http (não https): assim ela pode falar com o PC por http://
    val loader =
        WebViewAssetLoader.Builder()
            .setHttpAllowed(true)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    web.webViewClient =
        object : WebViewClient() {
          override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
              loader.shouldInterceptRequest(request.url)

          override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.url.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
            // link externo: abre no navegador
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
            return true
          }

          override fun onPageFinished(view: WebView, url: String) {
            pageReady = true
            deliverShare()
          }
        }

    web.addJavascriptInterface(Bridge(), "Android")

    // Botões da notificação / fones / tela bloqueada -> página
    MediaService.listener = { cmd -> runOnUiThread { js("window.mediaCmd && mediaCmd('$cmd')") } }

    onBackPressedDispatcher.addCallback(
        this,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            web.evaluateJavascript("window.appBack ? appBack() : false") { handled ->
              if (handled != "true") {
                // não fecha o app enquanto toca: só vai para o fundo
                if (playing) moveTaskToBack(true)
                else {
                  isEnabled = false
                  onBackPressedDispatcher.onBackPressed()
                  isEnabled = true
                }
              }
            }
          }
        })

    // botões dentro da mini tela
    val filter = IntentFilter(PIP_ACTION)
    if (Build.VERSION.SDK_INT >= 33) registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
    else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(pipReceiver, filter)

    handleShare(intent)
    if (savedInstanceState != null) web.restoreState(savedInstanceState)
    else web.loadUrl("http://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/web/index.html")
  }

  private fun js(code: String) = web.evaluateJavascript(code, null)

  // ---------- links compartilhados pelo YouTube ----------

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleShare(intent)
  }

  private fun handleShare(i: Intent?) {
    if (i?.action != Intent.ACTION_SEND) return
    val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
    // o YouTube manda "Título https://youtu.be/xxx": pega só o link
    pendingShare = Regex("https?://\\S+").find(text)?.value ?: text
    deliverShare()
  }

  private fun deliverShare() {
    val s = pendingShare ?: return
    if (!pageReady) return
    pendingShare = null
    js("window.sharedLink && sharedLink(${JSONObject.quote(s)})")
  }

  // ---------- mini tela (picture-in-picture) ----------

  private val pipReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
          i.getStringExtra("cmd")?.let { js("window.mediaCmd && mediaCmd('$it')") }
        }
      }

  private fun pipAction(icon: Int, title: String, cmd: String, code: Int): RemoteAction {
    val pi =
        PendingIntent.getBroadcast(
            this, code, Intent(PIP_ACTION).setPackage(packageName).putExtra("cmd", cmd),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    return RemoteAction(Icon.createWithResource(this, icon), title, title, pi)
  }

  private fun pipParams(): PictureInPictureParams {
    val b =
        PictureInPictureParams.Builder()
            .setAspectRatio(aspect)
            .setActions(
                listOf(
                    pipAction(R.drawable.ic_prev, "Anterior", "prev", 1),
                    if (playing) pipAction(R.drawable.ic_pause, "Pausar", "toggle", 2)
                    else pipAction(R.drawable.ic_play, "Tocar", "toggle", 2),
                    pipAction(R.drawable.ic_next, "Próxima", "next", 3)))
    // Android 12+: entra sozinho na mini tela ao sair do app com vídeo tocando
    if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(playing && kind == "video").setSeamlessResizeEnabled(true)
    return b.build()
  }

  private fun updatePip() {
    runCatching { setPictureInPictureParams(pipParams()) }
  }

  private fun enterPip() {
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    runCatching { enterPictureInPictureMode(pipParams()) }
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    // Android 8 a 11: vídeo tocando + botão "início" = mini tela
    if (Build.VERSION.SDK_INT < 31 && playing && kind == "video") enterPip()
  }

  override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
    super.onPictureInPictureModeChanged(inPip, newConfig)
    web.setPadding(0, 0, 0, 0)
    js("window.onPip && onPip($inPip)")
    if (!inPip) ViewCompat.requestApplyInsets(web)
  }

  // ---------- barras do sistema ----------

  private fun applyBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      if (fullscreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
      isAppearanceLightStatusBars = false
      isAppearanceLightNavigationBars = false
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    web.saveState(outState)
  }

  override fun onDestroy() {
    MediaService.listener = null
    runCatching { unregisterReceiver(pipReceiver) }
    super.onDestroy()
  }

  private fun askNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
  }

  inner class Bridge {
    /** Estado do player (JSON): título, artista, tocando, posição… */
    @JavascriptInterface
    fun mediaState(json: String) {
      runOnUiThread {
        val st = runCatching { JSONObject(json) }.getOrNull() ?: return@runOnUiThread
        val wasPlaying = playing
        playing = st.optBoolean("playing") && !st.optBoolean("stop")
        kind = st.optString("kind", "audio")
        if (kind == "video") {
          val w = st.optInt("w", 16).coerceAtLeast(1)
          val h = st.optInt("h", 9).coerceAtLeast(1)
          val r = w.toFloat() / h
          aspect = if (r in 0.42f..2.39f) Rational(w, h) else Rational(16, 9)
        } else aspect = Rational(1, 1)
        if (playing && !wasPlaying) askNotificationPermission()
        // vídeo tocando: tela não apaga sozinha; música: pode apagar (continua tocando)
        if (playing && kind == "video") window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        MediaService.update(this@MainActivity, json)
        updatePip()
      }
    }

    /** Linha da letra/legenda tocando agora (aparece na notificação). */
    @JavascriptInterface
    fun lyric(line: String) {
      runOnUiThread { MediaService.lyric(line) }
    }

    @JavascriptInterface
    fun enterPip(w: Int, h: Int) {
      runOnUiThread {
        val r = w.toFloat() / h.coerceAtLeast(1)
        aspect = if (r in 0.42f..2.39f) Rational(w, h.coerceAtLeast(1)) else Rational(16, 9)
        this@MainActivity.enterPip()
      }
    }

    @JavascriptInterface
    fun fullscreen(on: Boolean) {
      runOnUiThread {
        fullscreen = on
        applyBars()
      }
    }

    /** Salva no celular (pasta Download/BaixaMartins), com progresso na notificação. */
    @JavascriptInterface
    fun download(url: String, name: String) {
      runOnUiThread {
        runCatching {
          val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
          val req =
              DownloadManager.Request(Uri.parse(url))
                  .setTitle(safe)
                  .setDescription("Baixa Martins")
                  .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                  .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "BaixaMartins/$safe")
          getSystemService(DownloadManager::class.java).enqueue(req)
        }
            .onFailure { js("window.toast && toast('Não consegui salvar: ' + ${JSONObject.quote(it.message ?: "")}, true)") }
      }
    }

    @JavascriptInterface
    fun readClipboard(): String {
      val cm = getSystemService(ClipboardManager::class.java)
      return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString() ?: ""
    }

    @JavascriptInterface
    fun copy(text: String) {
      runOnUiThread {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Baixa Martins", text))
      }
    }
  }

  companion object {
    const val PIP_ACTION = "com.martins.baixa.PIP"
  }
}
