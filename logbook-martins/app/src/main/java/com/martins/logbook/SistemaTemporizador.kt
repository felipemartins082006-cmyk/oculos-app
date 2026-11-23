package com.martins.logbook

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * O temporizador fora da tela:
 * - salva o estado (assim não zera se você sair do app ou o Android fechar o app);
 * - agenda um alarme do sistema para o fim (vibra, toca e avisa mesmo com o app fechado);
 * - mostra uma notificação com a contagem regressiva enquanto roda.
 */
object SistemaTemporizador {
  private const val CANAL_CONTAGEM = "temporizador_contagem"
  private const val CANAL_FIM = "temporizador_fim"
  const val NOTIF_ID = 4711

  fun configurar(ctx: Context) {
    val app = ctx.applicationContext
    carregar(app)
    criarCanais(app)
    Temporizador.aoMudar = { salvar(app); agendar(app) }
    agendar(app)
  }

  private fun prefs(ctx: Context) = ctx.getSharedPreferences("temporizador", Context.MODE_PRIVATE)

  fun salvar(ctx: Context) {
    prefs(ctx).edit()
        .putLong("duracao", Temporizador.duracaoMs)
        .putLong("fim", Temporizador.fimEm ?: -1)
        .putLong("pausado", Temporizador.restantePausado ?: -1)
        .putBoolean("terminou", Temporizador.terminou)
        .apply()
  }

  fun carregar(ctx: Context, agora: Long = System.currentTimeMillis()) {
    val p = prefs(ctx)
    Temporizador.aberto = false // ao abrir o app, o pop-up começa fechado (a bolha mostra o tempo)
    Temporizador.duracaoMs = p.getLong("duracao", 90_000)
    val fim = p.getLong("fim", -1).takeIf { it > 0 }
    val pausado = p.getLong("pausado", -1).takeIf { it >= 0 }
    Temporizador.terminou = p.getBoolean("terminou", false)
    if (fim != null && fim <= agora) {
      // Terminou enquanto o app estava fechado (o alarme do sistema já avisou).
      Temporizador.fimEm = null
      Temporizador.restantePausado = 0
      Temporizador.terminou = true
    } else {
      Temporizador.fimEm = fim
      Temporizador.restantePausado = pausado
    }
  }

  private fun criarCanais(ctx: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CANAL_CONTAGEM, "Temporizador (contagem)", NotificationManager.IMPORTANCE_LOW))
    nm.createNotificationChannel(NotificationChannel(CANAL_FIM, "Temporizador (fim do descanso)", NotificationManager.IMPORTANCE_HIGH).apply {
      enableVibration(true)
    })
  }

  private fun pendente(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
      ctx, 0, Intent(ctx, AlarmeTemporizador::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )

  private fun abrirApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
      ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )

  /** Agenda (ou cancela) o alarme do fim e atualiza a notificação de contagem. */
  fun agendar(ctx: Context) {
    val am = ctx.getSystemService(AlarmManager::class.java)
    am.cancel(pendente(ctx))
    val fim = Temporizador.fimEm
    if (fim == null) {
      Temporizador.alarmeAgendado = false
      if (!Temporizador.terminou) NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
      if (Temporizador.restantePausado != null && Temporizador.restantePausado!! > 0) notificarPausado(ctx)
      return
    }
    Temporizador.alarmeAgendado = runCatching {
      if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fim, pendente(ctx))
      } else {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fim, pendente(ctx))
      }
      true
    }.getOrDefault(false)
    notificarContagem(ctx, fim)
  }

  private fun podeNotificar(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
      ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

  private fun notificarContagem(ctx: Context, fim: Long) {
    if (!podeNotificar(ctx)) return
    val n = NotificationCompat.Builder(ctx, CANAL_CONTAGEM)
        .setSmallIcon(R.drawable.ic_timer)
        .setContentTitle("Descansando")
        .setContentText("Toque para abrir o Logbook")
        .setWhen(fim).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
        .setContentIntent(abrirApp(ctx))
        .build()
    runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) }
  }

  private fun notificarPausado(ctx: Context) {
    if (!podeNotificar(ctx)) return
    val n = NotificationCompat.Builder(ctx, CANAL_CONTAGEM)
        .setSmallIcon(R.drawable.ic_timer)
        .setContentTitle("Temporizador pausado")
        .setContentText("Faltam ${fmtDuracao(Temporizador.restantePausado ?: 0)}")
        .setOngoing(true).setSilent(true).setContentIntent(abrirApp(ctx))
        .build()
    runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) }
  }

  fun notificarFim(ctx: Context) {
    if (!podeNotificar(ctx)) return
    // Tela de aviso por cima de tudo (até na tela bloqueada); com a tela ligada, aparece como pop-up no topo.
    val telaCheia = PendingIntent.getActivity(
        ctx, 2, Intent(ctx, AlarmeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    fun acao(nome: String, codigo: Int) = PendingIntent.getBroadcast(
        ctx, codigo, Intent(ctx, AcaoTemporizador::class.java).setAction(nome),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val n = NotificationCompat.Builder(ctx, CANAL_FIM)
        .setSmallIcon(R.drawable.ic_timer)
        .setContentTitle("Tempo! ⏱")
        .setContentText("O descanso acabou. Bora para a próxima série!")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setFullScreenIntent(telaCheia, true)
        .addAction(0, "+30s", acao(AcaoTemporizador.MAIS_30, 3))
        .addAction(0, "Fechar", acao(AcaoTemporizador.FECHAR, 4))
        .setContentIntent(abrirApp(ctx))
        .build()
    runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) }
  }
}

/** Disparado pelo Android no fim do temporizador (mesmo com o app fechado). */
class AlarmeTemporizador : BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent?) {
    alarme(ctx)
    SistemaTemporizador.notificarFim(ctx)
    // Atualiza o estado salvo (e a tela, se o app estiver aberto).
    ctx.getSharedPreferences("temporizador", Context.MODE_PRIVATE).edit()
        .putLong("fim", -1).putLong("pausado", 0).putBoolean("terminou", true).apply()
    if (Temporizador.fimEm != null) {
      Temporizador.alarmeAgendado = false
      Temporizador.acabou()
    }
  }
}

/** Botões da notificação de fim: "+30s" (mais um descanso curto) e "Fechar". */
class AcaoTemporizador : BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent?) {
    SistemaTemporizador.configurar(ctx)
    NotificationManagerCompat.from(ctx).cancel(SistemaTemporizador.NOTIF_ID)
    when (intent?.action) {
      MAIS_30 -> { Temporizador.iniciar(30_000); Temporizador.aberto = false }
      FECHAR -> Temporizador.zerar()
    }
  }

  companion object {
    const val MAIS_30 = "com.martins.logbook.MAIS_30"
    const val FECHAR = "com.martins.logbook.FECHAR"
  }
}
