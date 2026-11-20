package com.martins.financas

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Backup automático a cada 12 horas, feito pelo próprio Android (funciona com o app fechado).
 *
 * Os dados ficam no armazenamento da página, que o Android não consegue ler com o app fechado.
 * Por isso a página deixa uma cópia atualizada em [snapshot] sempre que algo muda; aqui essa
 * cópia é gravada como um arquivo novo na pasta escolhida pela pessoa.
 */
class BackupWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

  override fun doWork(): Result {
    val ok = write(applicationContext, null)
    return if (ok) Result.success() else Result.retry()
  }

  companion object {
    private const val WORK = "backup-12h"
    private const val KEEP = 30 // guarda os 30 backups automáticos mais recentes (15 dias)

    fun prefs(ctx: Context) = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun snapshot(ctx: Context) = File(ctx.filesDir, "ultimo-backup.json")

    fun schedule(ctx: Context) {
      val req = PeriodicWorkRequestBuilder<BackupWorker>(12, TimeUnit.HOURS, 1, TimeUnit.HOURS).build()
      WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(WORK)

    /** Grava um backup na pasta. Usa [content] se vier da página; senão, a última cópia salva. */
    fun write(ctx: Context, content: String?): Boolean =
        runCatching {
              val p = prefs(ctx)
              val tree = Uri.parse(p.getString("tree", null) ?: return false)
              val bytes = content?.toByteArray(Charsets.UTF_8) ?: snapshot(ctx).takeIf { it.exists() }?.readBytes() ?: return false
              if (bytes.isEmpty()) return false
              val cr = ctx.contentResolver
              val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
              val stamp = SimpleDateFormat("yyyy-MM-dd-HH'h'mm", Locale.US).format(Date())
              val file = DocumentsContract.createDocument(cr, parent, "application/json", "financas-backup-auto-$stamp.json") ?: return false
              cr.openOutputStream(file)!!.use { it.write(bytes) }
              p.edit().putLong("lastAuto", System.currentTimeMillis()).apply()
              prune(ctx, tree)
              true
            }
            .getOrDefault(false)

    // apaga os automáticos mais antigos para a pasta não encher (os manuais nunca são apagados)
    private fun prune(ctx: Context, tree: Uri) {
      runCatching {
        val cr = ctx.contentResolver
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Pair<String, String>>()
        cr.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
          while (it.moveToNext()) {
            val name = it.getString(1) ?: continue
            if (name.startsWith("financas-backup-auto-") && name.endsWith(".json")) found += it.getString(0) to name
          }
        }
        found.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
          runCatching { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
      }
    }
  }
}
