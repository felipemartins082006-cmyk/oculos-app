package com.martins.espelho

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.ImageSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço que:
 *  1. captura a tela do celular (MediaProjection),
 *  2. reduz cada quadro para 600x600 (tamanho da tela dos óculos),
 *  3. envia o quadro para o Meta Ray-Ban Display pelo kit da Meta.
 *
 * Roda em primeiro plano (com notificação) para continuar funcionando
 * enquanto você usa outros apps no celular.
 */
class MirrorService : Service() {

  companion object {
    private const val TAG = "EspelhoMartins"
    const val ACTION_START = "com.martins.espelho.START"
    const val ACTION_STOP = "com.martins.espelho.STOP"
    const val EXTRA_RESULT_CODE = "resultCode"
    const val EXTRA_RESULT_DATA = "resultData"

    private const val CHANNEL_ID = "espelho"
    private const val NOTIFICATION_ID = 1

    /** Tamanho da tela dos óculos. */
    private const val OUT_SIZE = 600

    /** Largura usada na captura (suficiente para o zoom de meia tela). */
    private const val CAPTURE_WIDTH = 540
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private var projection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var imageThread: HandlerThread? = null

  private var session: DeviceSession? = null
  @Volatile private var display: Display? = null
  @Volatile private var displayReady = false

  private var frameJob: Job? = null
  private var stopping = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopEverything("Parado")
        return START_NOT_STICKY
      }
      ACTION_START -> {
        // No Android 14+ é obrigatório entrar em primeiro plano ANTES de pegar a captura.
        startInForeground()
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data: Intent? =
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || data == null) {
          stopEverything("Permissão de captura de tela negada")
          return START_NOT_STICKY
        }
        if (MirrorState.running.value) return START_NOT_STICKY
        MirrorState.setRunning(true)
        stopping = false
        startCapture(resultCode, data)
        connectGlasses()
        startFrameLoop()
      }
    }
    return START_NOT_STICKY
  }

  // ---------------------------------------------------------------- Notificação

  private fun startInForeground() {
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Espelhamento", NotificationManager.IMPORTANCE_LOW))

    val openApp = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val stop = PendingIntent.getService(
        this, 1, Intent(this, MirrorService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE)

    val notification = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("Espelhando a tela nos óculos")
        .setContentText("Toque em Parar para encerrar")
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setContentIntent(openApp)
        .setOngoing(true)
        .addAction(Notification.Action.Builder(null, "Parar", stop).build())
        .build()

    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  }

  // ---------------------------------------------------------------- Captura de tela

  private fun startCapture(resultCode: Int, data: Intent) {
    val mpm = getSystemService(MediaProjectionManager::class.java)
    val mp = mpm.getMediaProjection(resultCode, data) ?: run {
      stopEverything("Não consegui iniciar a captura de tela")
      return
    }
    projection = mp

    val thread = HandlerThread("captura").also { it.start() }
    imageThread = thread
    val handler = Handler(thread.looper)

    // Obrigatório registrar antes de criar o VirtualDisplay.
    mp.registerCallback(object : MediaProjection.Callback() {
      override fun onStop() {
        stopEverything("Compartilhamento de tela encerrado")
      }
    }, handler)

    val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
    val screenW = minOf(bounds.width(), bounds.height())
    val screenH = maxOf(bounds.width(), bounds.height())
    val captureW = CAPTURE_WIDTH
    val captureH = (CAPTURE_WIDTH.toLong() * screenH / screenW).toInt()
    val dpi = (resources.displayMetrics.densityDpi * CAPTURE_WIDTH / screenW).coerceAtLeast(120)

    val reader = ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 2)
    imageReader = reader
    virtualDisplay = mp.createVirtualDisplay(
        "EspelhoMartins", captureW, captureH, dpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface, null, handler)
  }

  // ---------------------------------------------------------------- Conexão com os óculos

  private fun connectGlasses() {
    MirrorState.ensureDatInitialized(this)
    if (Wearables.registrationState.value != RegistrationState.REGISTERED) {
      stopEverything("Conecte o app ao Meta AI primeiro (botão no app)")
      return
    }
    MirrorState.setStatus("Procurando os óculos…")

    Wearables.createSession(AutoDeviceSelector(filter = { it.isDisplayCapable() })).fold(
        onSuccess = { newSession ->
          session = newSession
          scope.launch {
            newSession.state.collect { state ->
              Log.i(TAG, "Sessão: $state")
              when (state) {
                DeviceSessionState.STARTED -> if (display == null) attachDisplay(newSession)
                DeviceSessionState.STOPPED -> if (!stopping) stopEverything("Os óculos desconectaram")
                else -> {}
              }
            }
          }
          scope.launch {
            newSession.errors.collect { error ->
              Log.e(TAG, "Erro na sessão: ${error.description}")
              stopEverything("Erro nos óculos: ${error.description}")
            }
          }
          newSession.start()
        },
        onFailure = { error, _ ->
          stopEverything("Não achei os óculos: ${error.description}")
        },
    )
  }

  private fun attachDisplay(s: DeviceSession) {
    MirrorState.setStatus("Ligando a tela dos óculos…")
    s.addDisplay().fold(
        onSuccess = { d ->
          display = d
          scope.launch {
            d.state.collect { state ->
              Log.i(TAG, "Display: $state")
              displayReady = state == DisplayState.STARTED
              if (displayReady) MirrorState.setStatus("Espelhando ✔")
            }
          }
        },
        onFailure = { error, _ ->
          stopEverything("Não consegui usar a tela dos óculos: ${error.description}")
        },
    )
  }

  // ---------------------------------------------------------------- Envio dos quadros

  private fun startFrameLoop() {
    frameJob = scope.launch {
      val out = Bitmap.createBitmap(OUT_SIZE, OUT_SIZE, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(out)
      val paint = Paint(Paint.FILTER_BITMAP_FLAG)
      var lastHash = 0L
      var lastMode: MirrorMode? = null
      var lastSentAt = 0L
      var sentCount = 0
      var fpsWindowStart = SystemClock.elapsedRealtime()

      while (isActive) {
        val d = display
        if (d == null || !displayReady) { delay(200); continue }

        // Respeita o limite de quadros por segundo.
        val minInterval = 1000L / MirrorState.maxFps.value
        val wait = lastSentAt + minInterval - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)

        val frame = imageReader?.acquireLatestImage()?.let { img -> img.use { toBitmap(it) } }
        if (frame == null) { delay(30); continue }

        val mode = MirrorState.mode.value
        val src = cropFor(mode, frame.width, frame.height)
        canvas.drawColor(Color.BLACK)
        val scale = minOf(OUT_SIZE.toFloat() / src.width(), OUT_SIZE.toFloat() / src.height())
        val w = (src.width() * scale).toInt()
        val h = (src.height() * scale).toInt()
        val left = (OUT_SIZE - w) / 2
        val top = (OUT_SIZE - h) / 2
        canvas.drawBitmap(frame, src, Rect(left, top, left + w, top + h), paint)
        frame.recycle()

        // Se a tela não mudou, não reenvia (economiza bateria e Bluetooth).
        val hash = quickHash(out)
        if (hash == lastHash && mode == lastMode) { delay(80); continue }

        val toSend = out.copy(Bitmap.Config.ARGB_8888, false)
        val result = d.sendContent {
          flexBox(direction = Direction.COLUMN, padding = 0) {
            image(bitmap = toSend, sizePreset = ImageSize.FILL)
          }
        }
        var failed = false
        result.fold(
            onSuccess = {
              lastHash = hash
              lastMode = mode
              lastSentAt = SystemClock.elapsedRealtime()
              sentCount++
            },
            onFailure = { error, _ ->
              Log.w(TAG, "Falha ao enviar quadro: ${error.description}")
              failed = true
            },
        )
        if (failed) delay(300)

        val now = SystemClock.elapsedRealtime()
        if (now - fpsWindowStart >= 2000) {
          MirrorState.setFps(sentCount * 1000f / (now - fpsWindowStart))
          sentCount = 0
          fpsWindowStart = now
        }
      }
    }
  }

  /** Converte o quadro capturado em Bitmap (tratando o espaçamento extra das linhas). */
  private fun toBitmap(image: Image): Bitmap {
    val plane = image.planes[0]
    val pixelStride = plane.pixelStride
    val rowPadding = plane.rowStride - pixelStride * image.width
    val padded = Bitmap.createBitmap(
        image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
    padded.copyPixelsFromBuffer(plane.buffer)
    if (rowPadding == 0) return padded
    val exact = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
    padded.recycle()
    return exact
  }

  private fun cropFor(mode: MirrorMode, w: Int, h: Int): Rect = when (mode) {
    MirrorMode.FULL -> Rect(0, 0, w, h)
    MirrorMode.TOP -> Rect(0, 0, w, h / 2)
    MirrorMode.BOTTOM -> Rect(0, h / 2, w, h)
  }

  /** "Impressão digital" rápida da imagem, para saber se ela mudou. */
  private fun quickHash(b: Bitmap): Long {
    var hash = 1125899906842597L
    var y = 3
    while (y < b.height) {
      var x = 3
      while (x < b.width) {
        hash = 31 * hash + b.getPixel(x, y)
        x += 12
      }
      y += 12
    }
    return hash
  }

  // ---------------------------------------------------------------- Encerrar

  private fun stopEverything(message: String) {
    if (stopping) return
    stopping = true
    Log.i(TAG, "Encerrando: $message")
    MirrorState.setStatus(message)
    MirrorState.setRunning(false)
    MirrorState.setFps(0f)

    frameJob?.cancel()
    frameJob = null
    displayReady = false

    session?.let { s ->
      runCatching { s.removeDisplay() }
      runCatching { s.stop() }
    }
    session = null
    display = null

    runCatching { virtualDisplay?.release() }
    virtualDisplay = null
    runCatching { imageReader?.close() }
    imageReader = null
    runCatching { projection?.stop() }
    projection = null
    imageThread?.quitSafely()
    imageThread = null

    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    stopEverything(MirrorState.status.value)
    scope.cancel()
    super.onDestroy()
  }
}

/** Para usar `use {}` com Image (que só implementa AutoCloseable). */
private inline fun <R> Image.use(block: (Image) -> R): R =
    try { block(this) } finally { close() }

/** Atalho para abrir o serviço a partir da tela do app. */
fun Context.startMirrorService(resultCode: Int, data: Intent) {
  val intent = Intent(this, MirrorService::class.java)
      .setAction(MirrorService.ACTION_START)
      .putExtra(MirrorService.EXTRA_RESULT_CODE, resultCode)
      .putExtra(MirrorService.EXTRA_RESULT_DATA, data)
  startForegroundService(intent)
}

fun Context.stopMirrorService() {
  startService(Intent(this, MirrorService::class.java).setAction(MirrorService.ACTION_STOP))
}
