package com.martins.logbook

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Backup automático a cada 24 horas, agendado no próprio Android (WorkManager):
 * roda mesmo sem abrir o app e continua depois de reiniciar o celular.
 * Salva em Downloads/Logbook/logbook-backup-automatico.json.
 */
class BackupDiario(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
  override fun doWork(): Result = runCatching {
    val app = applicationContext
    Backup.automatico(app, Logbook(app), Diario(app), Figuras(app), forcar = true)
    Result.success()
  }.getOrElse { Result.retry() }

  companion object {
    fun agendar(ctx: Context) {
      runCatching {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "backup-diario", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupDiario>(24, TimeUnit.HOURS).build(),
        )
      }
    }
  }
}
