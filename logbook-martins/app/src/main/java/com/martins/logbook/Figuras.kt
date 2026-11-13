package com.martins.logbook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** O que aparece para um exercício: uma ilustração (automática ou escolhida) ou uma foto sua. */
sealed interface Figura {
  data class DeIlustracao(val ilustracao: Ilustracao, val automatica: Boolean) : Figura
  data class Foto(val arquivo: File) : Figura
}

/** Cor de fundo das ilustrações (é a mesma do fundo das imagens). */
val FundoIlustracao = Color(0xFFEEEEEE)

/**
 * Guarda qual figura cada exercício usa. Sem escolha: a ilustração achada pelo nome.
 * As fotos que você escolhe são copiadas (reduzidas) para dentro do app.
 */
class Figuras(private val ctx: Context) {
  val buscador = BuscadorIlustracoes(
      lerIndiceIlustracoes(ctx.assets.open("ilustracoes/indice.json").bufferedReader().use { it.readText() }),
  )
  private val prefs = ctx.getSharedPreferences("figuras", Context.MODE_PRIVATE)
  private val pasta = File(ctx.filesDir, "fotos").apply { mkdirs() }

  /** Muda a cada escolha, para a tela se redesenhar. */
  var versao by mutableIntStateOf(0)
    private set

  fun de(exercicio: String): Figura? {
    if (versao < 0) return null // lê a versão: quem chama se atualiza quando a escolha muda
    val v = prefs.getString(exercicio, null)
    return when {
      v == null -> buscador.achar(exercicio)?.let { Figura.DeIlustracao(it, automatica = true) }
      v == NENHUMA -> null
      v.startsWith("il:") -> buscador.porId(v.removePrefix("il:"))?.let { Figura.DeIlustracao(it, automatica = false) }
      v.startsWith("foto:") -> File(pasta, v.removePrefix("foto:")).takeIf { it.exists() }?.let { Figura.Foto(it) }
      else -> null
    }
  }

  /** Se o exercício está com uma escolha sua (e não a automática). */
  fun temEscolha(exercicio: String): Boolean {
    if (versao < 0) return false
    return prefs.contains(exercicio)
  }

  fun usarIlustracao(exercicio: String, il: Ilustracao) = guardar(exercicio, "il:${il.id}")

  fun semFigura(exercicio: String) = guardar(exercicio, NENHUMA)

  /** Volta para a ilustração automática (apaga a foto, se tinha). */
  fun automatica(exercicio: String) = guardar(exercicio, null)

  /** Copia a foto escolhida (reduzida para no máximo 1024 px) e passa a usá-la. */
  fun usarFoto(exercicio: String, uri: Uri): Boolean {
    val bmp = runCatching { lerFoto(uri) }.getOrNull() ?: return false
    val nome = "${UUID.randomUUID()}.jpg"
    File(pasta, nome).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    guardar(exercicio, "foto:$nome")
    return true
  }

  /** Quando um exercício é renomeado (ou juntado com outro), a figura vai junto. */
  fun renomear(de: String, para: String) {
    val v = prefs.getString(de, null) ?: return
    val e = prefs.edit().remove(de)
    if (!prefs.contains(para)) e.putString(para, v) else apagarFoto(v)
    e.apply()
    versao++
  }

  private fun guardar(exercicio: String, valor: String?) {
    prefs.getString(exercicio, null)?.let { if (it != valor) apagarFoto(it) }
    prefs.edit().apply { if (valor == null) remove(exercicio) else putString(exercicio, valor) }.apply()
    versao++
  }

  private fun apagarFoto(valor: String) {
    if (valor.startsWith("foto:")) File(pasta, valor.removePrefix("foto:")).delete()
  }

  private fun lerFoto(uri: Uri): Bitmap {
    val maximo = 1024
    if (Build.VERSION.SDK_INT >= 28) {
      // ImageDecoder já desvira a foto conforme o celular estava (EXIF). Se falhar, vai pelo jeito antigo.
      runCatching {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
          val lado = max(info.size.width, info.size.height)
          if (lado > maximo) dec.setTargetSize(info.size.width * maximo / lado, info.size.height * maximo / lado)
          dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
      }
    }
    val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opcoes) }
    var amostra = 1
    while (max(opcoes.outWidth, opcoes.outHeight) / (amostra * 2) >= maximo) amostra *= 2
    return ctx.contentResolver.openInputStream(uri)!!.use {
      BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })!!
    }
  }

  companion object {
    private const val NENHUMA = "nenhuma"
  }
}

val LocalFiguras = staticCompositionLocalOf<Figuras> { error("Figuras não configuradas") }

/** Guarda as imagens já abertas, para a lista rolar lisa. */
private object CacheImagens {
  private val cache = LruCache<String, ImageBitmap>(120)

  fun ilustracao(ctx: Context, nome: String): ImageBitmap? = abrir("il:$nome") {
    ctx.assets.open("ilustracoes/$nome.webp").use { BitmapFactory.decodeStream(it) }
  }

  fun foto(arquivo: File): ImageBitmap? = abrir("foto:${arquivo.name}") { BitmapFactory.decodeFile(arquivo.path) }

  private fun abrir(chave: String, ler: () -> Bitmap?): ImageBitmap? {
    cache.get(chave)?.let { return it }
    val img = runCatching { ler() }.getOrNull()?.asImageBitmap() ?: return null
    cache.put(chave, img)
    return img
  }

  fun jaAberta(chave: String): ImageBitmap? = cache.get(chave)
}

/**
 * A figura do exercício. Com [animar], a ilustração alterna entre o início e o fim do movimento.
 * Sem figura: um quadrado com a inicial (ou nada, se [vazioSemFigura] for falso).
 */
@Composable
fun FiguraExercicio(
    exercicio: String,
    modifier: Modifier = Modifier,
    animar: Boolean = false,
    vazioSemFigura: Boolean = true,
) {
  val figura = LocalFiguras.current.de(exercicio)
  when (figura) {
    is Figura.DeIlustracao -> FiguraIlustracao(figura.ilustracao, modifier, animar)
    is Figura.Foto -> FiguraFoto(figura.arquivo, modifier)
    null -> if (vazioSemFigura) {
      Box(
          modifier.clip(RoundedCornerShape(10.dp)).background(Superficie2),
          contentAlignment = Alignment.Center,
      ) {
        Text(exercicio.take(1).uppercase(), color = Cinza, fontWeight = FontWeight.Bold, fontSize = 18.sp)
      }
    }
  }
}

@Composable
fun FiguraIlustracao(il: Ilustracao, modifier: Modifier = Modifier, animar: Boolean = false) {
  val ctx = LocalContext.current
  var quadro by androidx.compose.runtime.remember(il.id) { mutableIntStateOf(0) }
  if (animar && il.arquivos.size > 1) {
    LaunchedEffect(il.id) {
      while (true) {
        delay(1100)
        quadro = (quadro + 1) % il.arquivos.size
      }
    }
  }
  val nome = il.arquivos[quadro.coerceAtMost(il.arquivos.lastIndex)]
  val img by produceState(CacheImagens.jaAberta("il:$nome"), nome) {
    value = withContext(Dispatchers.IO) { CacheImagens.ilustracao(ctx, nome) }
  }
  Box(
      modifier.clip(RoundedCornerShape(10.dp)).background(FundoIlustracao)
          .semantics { contentDescription = "Ilustração: ${il.titulo}" },
      contentAlignment = Alignment.Center,
  ) {
    img?.let {
      Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
    }
  }
}

@Composable
private fun FiguraFoto(arquivo: File, modifier: Modifier) {
  val img by produceState(CacheImagens.jaAberta("foto:${arquivo.name}"), arquivo) {
    value = withContext(Dispatchers.IO) { CacheImagens.foto(arquivo) }
  }
  Box(
      modifier.clip(RoundedCornerShape(10.dp)).background(Superficie2).semantics { contentDescription = "Sua foto" },
  ) {
    img?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
  }
}
