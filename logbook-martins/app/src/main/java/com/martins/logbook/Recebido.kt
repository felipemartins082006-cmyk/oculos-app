package com.martins.logbook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** Algo que chegou de outro app pelo "Compartilhar" ou "Abrir com": vira uma prévia de importação. */
data class Recebido(
    /** Texto compartilhado direto (sem arquivo). */
    val texto: String?,
    /** Arquivos compartilhados: lidos em segundo plano (um PDF grande demora). */
    val arquivos: List<Uri>,
    val nome: String?,
    val id: Long = System.nanoTime(),
) {
  fun ler(ctx: Context): String = texto ?: arquivos.joinToString("\n\n") { lerArquivo(ctx, it) }
}

fun nomeDoArquivo(ctx: Context, uri: Uri): String? = runCatching {
  if (uri.scheme == "file") return@runCatching uri.lastPathSegment
  ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
    if (c.moveToFirst()) c.getString(0) else null
  }
}.getOrNull()

/** Lê um arquivo de texto (.txt, .csv, .json…) ou o texto de dentro de um PDF. */
fun lerArquivo(ctx: Context, uri: Uri): String {
  val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
  return if (ehPdf(bytes)) textoDoPdf(ctx, bytes) else bytes.toString(Charsets.UTF_8)
}

fun ehPdf(bytes: ByteArray): Boolean =
    bytes.size > 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()

/** Texto de um PDF, na ordem em que aparece na página (linha por linha). */
fun textoDoPdf(ctx: Context, bytes: ByteArray): String {
  PDFBoxResourceLoader.init(ctx.applicationContext)
  return PDDocument.load(bytes).use { doc ->
    PDFTextStripper().apply { sortByPosition = true }.getText(doc)
  }
}

@Suppress("DEPRECATION")
private fun Intent.anexos(): List<Uri> = when (action) {
  Intent.ACTION_SEND ->
    listOfNotNull(
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableExtra(Intent.EXTRA_STREAM),
    ).ifEmpty { (0 until (clipData?.itemCount ?: 0)).mapNotNull { clipData!!.getItemAt(it).uri } }
  Intent.ACTION_SEND_MULTIPLE ->
    (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
    else getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
  Intent.ACTION_VIEW -> listOfNotNull(data)
  else -> emptyList()
}

/**
 * Lê o que veio de outro app: arquivo(s) anexado(s) ou um texto compartilhado.
 * Devolve null se não veio nada (o app foi aberto normalmente).
 */
fun lerRecebido(ctx: Context, intent: Intent?): Result<Recebido>? {
  if (intent == null || intent.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW)) {
    return null
  }
  return runCatching {
    val arquivos = intent.anexos()
    if (arquivos.isNotEmpty()) {
      val nomes = arquivos.mapNotNull { nomeDoArquivo(ctx, it) }
      Recebido(null, arquivos, if (nomes.size <= 1) nomes.firstOrNull() else "${nomes.size} arquivos")
    } else {
      val texto = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
      require(!texto.isNullOrBlank()) { "nada para importar" }
      Recebido(texto, emptyList(), null)
    }
  }
}
