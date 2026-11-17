package com.martins.logbook

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import org.json.JSONObject

/**
 * Backup completo do app num único arquivo JSON:
 * séries (com a hora de cada uma), diário (peso, sono, cabeça, comida, cronômetro) e figuras (com as suas fotos).
 *
 * Feito para durar entre versões: a importação lê qualquer versão de backup (campos que faltarem ficam vazios,
 * campos novos são ignorados por versões antigas) e também planilhas, texto, PDF e exportações de outros apps.
 */
object Backup {
  const val VERSAO = 3
  private const val PASTA = "Logbook"

  fun gerar(lb: Logbook, diario: Diario, figuras: Figuras?): JSONObject = JSONObject()
      .put("logbook", VERSAO)
      .put("app", "LogbookMartins")
      .put("criadoEm", LocalDateTime.now().withNano(0).toString())
      .put("series", lb.paraJson())
      .put("diario", diario.paraJson())
      .apply { figuras?.let { put("figuras", it.paraJson()) } }

  /** Um backup do app? (qualquer versão) */
  fun ehBackup(texto: String): Boolean {
    val t = texto.trimStart()
    return t.startsWith("{") && "\"logbook\"" in t.take(300)
  }

  data class Conteudo(val series: List<SerieLida>, val diario: JSONObject, val figuras: JSONObject?, val versao: Int, val criadoEm: String?)

  fun ler(texto: String): Conteudo? = runCatching {
    val o = JSONObject(texto.trimStart())
    Conteudo(
        series = Logbook.seriesDeJson(o.optJSONArray("series") ?: org.json.JSONArray()),
        diario = o.optJSONObject("diario") ?: JSONObject(),
        figuras = o.optJSONObject("figuras"),
        versao = o.optInt("logbook", 1),
        criadoEm = o.optString("criadoEm").ifEmpty { null },
    )
  }.getOrNull()

  /** Restaura sem duplicar: séries iguais no mesmo dia não entram de novo; diário e figuras são sobrescritos. */
  fun restaurar(c: Conteudo, lb: Logbook, diario: Diario, figuras: Figuras?): Importacao {
    val res = lb.importar(c.series)
    diario.restaurar(c.diario)
    c.figuras?.let { figuras?.restaurar(it) }
    return res
  }

  /** Planilha (.csv) com todas as séries: abre no Excel, Google Planilhas, etc. E dá para importar de volta. */
  fun csv(series: List<Serie>): String = buildString {
    append("data,hora,exercicio,kg,reps,nota,1rm_estimado\n")
    series.sortedWith(compareBy({ it.data }, { it.id })).forEach { s ->
      val hora = s.momento?.let { horaDe(it) }.orEmpty()
      fun q(t: String) = "\"" + t.replace("\"", "\"\"") + "\""
      append("${s.data},$hora,${q(s.exercicio)},${fmtPeso(s.peso)},${s.reps},${q(s.nota)},${fmtPeso(Math.round(s.umRm * 10) / 10.0)}\n")
    }
  }

  fun nomeArquivo(data: LocalDate = LocalDate.now()) = "logbook-backup-$data.json"

  /**
   * Salva direto na pasta Downloads/Logbook do celular (sem escolher pasta). Android 10 ou mais novo.
   * Se já existe um arquivo com o mesmo nome criado pelo app, ele é atualizado.
   * Devolve onde salvou, ou null se não deu (aí o app pede para escolher a pasta).
   */
  fun salvarEmDownloads(ctx: Context, nome: String, conteudo: ByteArray, tipo: String): String? {
    if (Build.VERSION.SDK_INT < 29) return null
    return runCatching {
      val cr = ctx.contentResolver
      val colecao = MediaStore.Downloads.EXTERNAL_CONTENT_URI
      val existente = cr.query(
          colecao, arrayOf(MediaStore.Downloads._ID),
          "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
          arrayOf(nome, "${Environment.DIRECTORY_DOWNLOADS}/$PASTA/"), null,
      )?.use { if (it.moveToFirst()) Uri.withAppendedPath(colecao, it.getLong(0).toString()) else null }
      val uri = existente ?: cr.insert(colecao, ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, nome)
        put(MediaStore.Downloads.MIME_TYPE, tipo)
        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$PASTA")
      })!!
      cr.openOutputStream(uri, "wt")!!.use { it.write(conteudo) }
      "Downloads/$PASTA/$nome"
    }.getOrNull()
  }

  /** Abre o "Compartilhar" do Android com o arquivo (WhatsApp, Drive, e-mail…). */
  fun compartilhar(ctx: Context, nome: String, conteudo: ByteArray, tipo: String) {
    val pasta = File(ctx.cacheDir, "backup").apply { mkdirs() }
    val f = File(pasta, nome).apply { writeBytes(conteudo) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.arquivos", f)
    val envio = Intent(Intent.ACTION_SEND).setType(tipo).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(envio, "Enviar backup").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  /**
   * Backup automático: uma vez por dia, ao abrir o app, atualiza Downloads/Logbook/logbook-backup-automatico.json.
   * Assim, mesmo sem lembrar, sempre existe uma cópia recente fora do app.
   */
  fun automatico(ctx: Context, lb: Logbook, diario: Diario, figuras: Figuras?, agora: Long = System.currentTimeMillis()): String? {
    val prefs = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE)
    if (agora - prefs.getLong("auto", 0) < 20 * 3600_000L) return null
    if (lb.series.value.isEmpty() && diario.dias.value.isEmpty()) return null
    val onde = salvarEmDownloads(ctx, "logbook-backup-automatico.json", gerar(lb, diario, figuras).toString().toByteArray(), "application/json")
    if (onde != null) prefs.edit().putLong("auto", agora).apply()
    return onde
  }

  fun ultimoAutomatico(ctx: Context): Long? =
      ctx.getSharedPreferences("backup", Context.MODE_PRIVATE).getLong("auto", 0).takeIf { it > 0 }
}
