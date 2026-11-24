package com.martins.baixa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONObject

/**
 * Mantém os downloads e a IA trabalhando com a tela desligada ou com outro app aberto,
 * mostrando o progresso numa notificação. Some sozinho quando a fila acaba.
 */
class WorkService : Service() {

  companion object {
    const val CHANNEL = "baixando"
    const val NOTIF_ID = 8
    @Volatile var instance: WorkService? = null

    /** json: { active: n, text: "...", progress: 0..1 } */
    fun update(ctx: Context, json: String) {
      val st = runCatching { JSONObject(json) }.getOrNull() ?: return
      val active = st.optInt("active")
      val svc = instance
      if (svc != null) {
        if (active == 0) svc.finish() else svc.show(st)
        return
      }
      if (active == 0) return
      runCatching {
        ctx.startForegroundService(Intent(ctx, WorkService::class.java).putExtra("json", json))
      }
    }
  }

  private var wake: PowerManager.WakeLock? = null
  private var wifi: WifiManager.WifiLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    instance = this
    getSystemService(NotificationManager::class.java)
        .createNotificationChannel(
            NotificationChannel(CHANNEL, "Downloads e letras", NotificationManager.IMPORTANCE_LOW).apply {
              setShowBadge(false)
              description = "Progresso dos downloads e da inteligência artificial"
            })
    wake =
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BaixaMartins:trabalhando")
            .apply { setReferenceCounted(false) }
    @Suppress("DEPRECATION")
    wifi =
        (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BaixaMartins:baixando")
            .apply { setReferenceCounted(false) }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val st = intent?.getStringExtra("json")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
    show(st)
    return START_NOT_STICKY
  }

  fun show(st: JSONObject) {
    val n = build(st)
    if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    else startForeground(NOTIF_ID, n)
    wake?.acquire(3 * 60 * 60 * 1000L)
    wifi?.acquire()
  }

  private fun build(st: JSONObject): Notification {
    val active = st.optInt("active", 1)
    val p = st.optDouble("progress", -1.0)
    val open =
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    return Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_down)
        .setContentTitle(if (active == 1) "Trabalhando em 1 item" else "Trabalhando em $active itens")
        .setContentText(st.optString("text", "Baixa Martins"))
        .setProgress(100, if (p >= 0) (p * 100).toInt() else 0, p < 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setContentIntent(open)
        .setCategory(Notification.CATEGORY_PROGRESS)
        .build()
  }

  fun finish() {
    wake?.release()
    wifi?.release()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    instance = null
    wake?.release()
    wifi?.release()
    super.onDestroy()
  }
}
