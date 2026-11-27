package com.martins.grava

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Opções escolhidas na tela do app. */
class Config(ctx: Context) {
  private val p: SharedPreferences = ctx.getSharedPreferences("config", Context.MODE_PRIVATE)

  var microfone: Boolean
    get() = p.getBoolean("microfone", false)
    set(v) = p.edit().putBoolean("microfone", v).apply()

  var contagem: Boolean
    get() = p.getBoolean("contagem", true)
    set(v) = p.edit().putBoolean("contagem", v).apply()

  var bolha: Boolean
    get() = p.getBoolean("bolha", true)
    set(v) = p.edit().putBoolean("bolha", v).apply()
}

/** Um vídeo ou print salvo pelo app. */
data class Arquivo(
    val uri: Uri,
    val nome: String,
    val video: Boolean,
    val data: Long,
    val tamanho: Long,
    val duracao: Long,
) {
  val mime: String
    get() = if (video) "video/mp4" else "image/png"
}

/** Onde e como os arquivos ficam na Galeria (Filmes/GravaTela e Imagens/GravaTela). */
object Arquivos {
  const val PASTA = "GravaTela"
  private val PASTA_VIDEOS = Environment.DIRECTORY_MOVIES + "/" + PASTA
  private val PASTA_PRINTS = Environment.DIRECTORY_PICTURES + "/" + PASTA

  private val colecaoVideos = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
  private val colecaoPrints = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

  private fun agora(): String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

  /** Cria o vídeo vazio na Galeria (fica escondido até [publicar]). */
  fun novoVideo(ctx: Context): Uri? =
      criar(ctx, colecaoVideos, "Gravacao_${agora()}.mp4", "video/mp4", PASTA_VIDEOS)

  fun novoPrint(ctx: Context): Uri? =
      criar(ctx, colecaoPrints, "Print_${agora()}.png", "image/png", PASTA_PRINTS)

  private fun criar(ctx: Context, colecao: Uri, nome: String, mime: String, pasta: String): Uri? {
    val v =
        ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, nome)
          put(MediaStore.MediaColumns.MIME_TYPE, mime)
          put(MediaStore.MediaColumns.RELATIVE_PATH, pasta)
          put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    return try {
      ctx.contentResolver.insert(colecao, v)
    } catch (e: Exception) {
      null
    }
  }

  /** Arquivo terminado: aparece na Galeria. */
  fun publicar(ctx: Context, uri: Uri) {
    val v = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
    try {
      ctx.contentResolver.update(uri, v, null, null)
    } catch (_: Exception) {}
  }

  fun descartar(ctx: Context, uri: Uri) {
    try {
      ctx.contentResolver.delete(uri, null, null)
    } catch (_: Exception) {}
  }

  /** Últimos vídeos e prints feitos pelo app, do mais novo para o mais velho. */
  fun listar(ctx: Context, limite: Int = 40): List<Arquivo> {
    val todos = buscar(ctx, colecaoVideos, PASTA_VIDEOS, true) + buscar(ctx, colecaoPrints, PASTA_PRINTS, false)
    return todos.sortedByDescending { it.data }.take(limite)
  }

  private fun buscar(ctx: Context, colecao: Uri, pasta: String, video: Boolean): List<Arquivo> {
    val colunas =
        mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE)
    if (video) colunas += MediaStore.Video.VideoColumns.DURATION
    val lista = mutableListOf<Arquivo>()
    try {
      ctx.contentResolver
          .query(
              colecao,
              colunas.toTypedArray(),
              "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
              arrayOf("$pasta%"),
              "${MediaStore.MediaColumns.DATE_ADDED} DESC")
          ?.use { c ->
            while (c.moveToNext()) {
              val id = c.getLong(0)
              lista +=
                  Arquivo(
                      uri = ContentUris.withAppendedId(colecao, id),
                      nome = c.getString(1) ?: "",
                      video = video,
                      data = c.getLong(2) * 1000,
                      tamanho = c.getLong(3),
                      duracao = if (video) c.getLong(4) else 0,
                  )
            }
          }
    } catch (_: Exception) {}
    return lista
  }
}
