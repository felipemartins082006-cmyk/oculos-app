package com.martins.baixa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Mantém a música tocando com a tela desligada ou com outro app aberto:
 * notificação com capa, letra atual e botões; controles na tela bloqueada, fones e bluetooth.
 * O som em si sai do WebView (a página); este serviço só mantém o app vivo e repassa os botões.
 */
class MediaService : Service() {

  companion object {
    const val CHANNEL = "tocando"
    const val NOTIF_ID = 7
    /** Para onde vão os botões (play, próxima…). A MainActivity registra aqui. */
    @Volatile var listener: ((String) -> Unit)? = null
    @Volatile var instance: MediaService? = null

    fun update(ctx: Context, json: String) {
      val i = Intent(ctx, MediaService::class.java).setAction("state").putExtra("json", json)
      val st = runCatching { JSONObject(json) }.getOrNull() ?: return
      if (st.optBoolean("stop")) {
        instance?.stopAll()
        return
      }
      val svc = instance
      if (svc != null) {
        svc.applyState(json) // já está rodando: atualiza direto (já estamos na thread principal)
        return
      }
      if (!st.optBoolean("playing")) return
      ctx.startForegroundService(i)
    }

    fun lyric(line: String) {
      instance?.setLyric(line)
    }
  }

  private lateinit var session: MediaSession
  private var wake: PowerManager.WakeLock? = null
  private var wifi: WifiManager.WifiLock? = null
  private var title = ""
  private var artist = ""
  private var line = ""
  private var playing = false
  private var pos = 0L
  private var dur = 0L
  private var artUrl = ""
  private var art: Bitmap? = null
  private var foreground = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    instance = this
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL, "Tocando agora", NotificationManager.IMPORTANCE_LOW).apply {
          setShowBadge(false)
          description = "Controles da música ou vídeo que está tocando"
        })
    session =
        MediaSession(this, "BaixaMartins").apply {
          setCallback(
              object : MediaSession.Callback() {
                override fun onPlay() = send("play")
                override fun onPause() = send("pause")
                override fun onSkipToNext() = send("next")
                override fun onSkipToPrevious() = send("prev")
                override fun onStop() = send("pause")
                override fun onSeekTo(p: Long) = send("seek:$p")
              })
          setSessionActivity(openAppIntent())
          isActive = true
        }
    val pm = getSystemService(PowerManager::class.java)
    wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BaixaMartins:tocando").apply { setReferenceCounted(false) }
    @Suppress("DEPRECATION")
    wifi =
        (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BaixaMartins:rede")
            .apply { setReferenceCounted(false) }
  }

  private fun send(cmd: String) {
    listener?.invoke(cmd)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      "state" -> intent.getStringExtra("json")?.let { applyState(it) }
      "toggle", "next", "prev" -> send(intent.action!!)
    }
    if (!foreground) {
      // Android exige que o serviço apareça na hora
      startFg()
    }
    return START_NOT_STICKY
  }

  fun applyState(json: String) {
    val st = JSONObject(json)
    val newTitle = st.optString("title")
    if (newTitle != title) line = ""
    title = newTitle
    artist = st.optString("artist")
    playing = st.optBoolean("playing")
    pos = st.optLong("pos")
    dur = st.optLong("dur")
    val a = st.optString("art")
    if (a.isNotEmpty() && a != artUrl) {
      artUrl = a
      art = null
      loadArt(a)
    }
    if (playing) {
      wake?.acquire(6 * 60 * 60 * 1000L)
      wifi?.acquire()
    } else {
      wake?.release()
      wifi?.release()
    }
    publish()
  }

  fun setLyric(l: String) {
    if (l == line) return
    line = l
    publish()
  }

  private fun loadArt(u: String) {
    thread {
      val bmp =
          runCatching {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 5000
                c.readTimeout = 8000
                c.inputStream.use { BitmapFactory.decodeStream(it) }
              }
              .getOrNull()
      if (bmp != null && u == artUrl) {
        art = bmp
        publish()
      }
    }
  }

  private fun publish() {
    session.setMetadata(
        MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, line.ifEmpty { artist })
            .putLong(MediaMetadata.METADATA_KEY_DURATION, dur)
            .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
            .build())
    session.setPlaybackState(
        PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, pos, if (playing) 1f else 0f)
            .build())
    val n = buildNotification()
    if (playing) {
      startFg(n)
    } else {
      // pausado: a notificação fica, mas o Android pode fechar o app se precisar de memória
      if (foreground) {
        stopForeground(STOP_FOREGROUND_DETACH)
        foreground = false
      }
      getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }
  }

  private fun startFg(n: Notification = buildNotification()) {
    if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    else startForeground(NOTIF_ID, n)
    foreground = true
  }

  private fun openAppIntent(): PendingIntent =
      PendingIntent.getActivity(
          this, 0,
          Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

  private fun action(icon: Int, label: String, cmd: String): Notification.Action {
    val pi =
        PendingIntent.getService(
            this, cmd.hashCode(), Intent(this, MediaService::class.java).setAction(cmd),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()
  }

  private fun buildNotification(): Notification =
      Notification.Builder(this, CHANNEL)
          .setSmallIcon(R.drawable.ic_note)
          .setContentTitle(title.ifEmpty { "Baixa Martins" })
          .setContentText(if (line.isNotEmpty()) "♪ $line" else artist)
          .setSubText(if (line.isNotEmpty()) artist else null)
          .setLargeIcon(art)
          .setContentIntent(openAppIntent())
          .setOngoing(playing)
          .setShowWhen(false)
          .setVisibility(Notification.VISIBILITY_PUBLIC)
          .setCategory(Notification.CATEGORY_TRANSPORT)
          .addAction(action(R.drawable.ic_prev, "Anterior", "prev"))
          .addAction(
              if (playing) action(R.drawable.ic_pause, "Pausar", "toggle")
              else action(R.drawable.ic_play, "Tocar", "toggle"))
          .addAction(action(R.drawable.ic_next, "Próxima", "next"))
          .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
          .build()

  fun stopAll() {
    wake?.release()
    wifi?.release()
    stopForeground(STOP_FOREGROUND_REMOVE)
    foreground = false
    stopSelf()
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    // fechou o app pela lista de recentes: para tudo
    stopAll()
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    instance = null
    session.isActive = false
    session.release()
    wake?.release()
    wifi?.release()
    super.onDestroy()
  }
}
