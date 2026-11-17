package com.martins.logbook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import java.io.File
import kotlin.math.max

/** Uma paleta de cores do app (tema). */
data class Paleta(
    val id: String,
    val nome: String,
    val coral: Color,
    val destaque: Color,
    val escuro: Color,
    val fundo: Color,
    val fundoTopo: Color,
    val fundoMeio: Color,
    val brilhos: List<Color>,
    val solida: Color,
) {
  /** Cor do texto em cima do degradê (branco nos temas escuros, quase preto nos claros). */
  val sobreDestaque: Color get() = if (destaque.luminance() > 0.45f) Color(0xFF14110F) else Color.White
}

private fun c(v: Long) = Color(v)

val PALETAS = listOf(
    Paleta("rubi", "Rubi", c(0xFFFF8A6B), c(0xFFFF5C7A), c(0xFFB3204F), c(0xFF0B0710), c(0xFF2A0D1F), c(0xFF140A16),
        listOf(c(0xFFFF4F7B), c(0xFF8B5CF6), c(0xFFFF8A6B)), c(0xFF1A1220)),
    Paleta("brasa", "Brasa", c(0xFFFF7A5C), c(0xFFFF4D4D), c(0xFFA3131F), c(0xFF080606), c(0xFF260A0A), c(0xFF120707),
        listOf(c(0xFFE0283A), c(0xFFFF6B3D), c(0xFF7A0F18)), c(0xFF1A0E0E)),
    Paleta("oceano", "Oceano", c(0xFF5EEAD4), c(0xFF22B8CF), c(0xFF1D4ED8), c(0xFF050B12), c(0xFF0A2233), c(0xFF07131E),
        listOf(c(0xFF22D3EE), c(0xFF3B82F6), c(0xFF14B8A6)), c(0xFF0E1822)),
    Paleta("aurora", "Aurora", c(0xFFF0ABFC), c(0xFFC084FC), c(0xFF7C3AED), c(0xFF0B0814), c(0xFF22123A), c(0xFF120C20),
        listOf(c(0xFFA855F7), c(0xFFEC4899), c(0xFF6366F1)), c(0xFF17112A)),
    Paleta("esmeralda", "Esmeralda", c(0xFFA7F3D0), c(0xFF34D399), c(0xFF047857), c(0xFF050D0A), c(0xFF0B2A1E), c(0xFF081610),
        listOf(c(0xFF10B981), c(0xFF84CC16), c(0xFF06B6D4)), c(0xFF0E1B16)),
    Paleta("ambar", "Âmbar", c(0xFFFDE68A), c(0xFFFBBF24), c(0xFFD97706), c(0xFF0D0905), c(0xFF2E1C06), c(0xFF18100A),
        listOf(c(0xFFF59E0B), c(0xFFEF4444), c(0xFFFCD34D)), c(0xFF1C150D)),
    Paleta("grafite", "Grafite", c(0xFFF3F4F6), c(0xFFC7CDD6), c(0xFF6B7280), c(0xFF0A0A0B), c(0xFF1F2023), c(0xFF121214),
        listOf(c(0xFF9CA3AF), c(0xFF6B7280), c(0xFFD1D5DB)), c(0xFF18181B)),
)

/**
 * A aparência escolhida por você: tema (paleta), foto de fundo da galeria (com escurecer e desfoque),
 * transparência dos cartões e, se quiser, as cores tiradas da própria foto. Fica salva no celular.
 */
object Aparencia {
  var paletaId by mutableStateOf("rubi")
  var fundoImagem by mutableStateOf<ImageBitmap?>(null)
  /** Paleta montada a partir das cores da foto de fundo. */
  var paletaFoto by mutableStateOf<Paleta?>(null)
  var usarCoresDaFoto by mutableStateOf(false)
  /** Quanto a foto de fundo é escurecida (0 = foto pura, 1 = preto). */
  var escurecer by mutableFloatStateOf(0.55f)
  /** Desfoque da foto, em dp (Android 12+). */
  var desfoque by mutableFloatStateOf(6f)
  /** Opacidade dos cartões de vidro. */
  var vidro by mutableFloatStateOf(0.11f)

  val paleta: Paleta
    get() = paletaFoto?.takeIf { usarCoresDaFoto && fundoImagem != null } ?: PALETAS.firstOrNull { it.id == paletaId } ?: PALETAS[0]

  private fun arquivo(ctx: Context) = File(File(ctx.filesDir, "tema").apply { mkdirs() }, "fundo.jpg")
  private fun prefs(ctx: Context) = ctx.getSharedPreferences("aparencia", Context.MODE_PRIVATE)

  fun carregar(ctx: Context) {
    val p = prefs(ctx)
    paletaId = p.getString("paleta", "rubi") ?: "rubi"
    usarCoresDaFoto = p.getBoolean("coresDaFoto", false)
    escurecer = p.getFloat("escurecer", 0.55f)
    desfoque = p.getFloat("desfoque", 6f)
    vidro = p.getFloat("vidro", 0.11f)
    val f = arquivo(ctx)
    val bmp = if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() else null
    fundoImagem = bmp?.asImageBitmap()
    paletaFoto = bmp?.let { paletaDaFoto(it) }
  }

  fun salvar(ctx: Context) {
    prefs(ctx).edit()
        .putString("paleta", paletaId).putBoolean("coresDaFoto", usarCoresDaFoto)
        .putFloat("escurecer", escurecer).putFloat("desfoque", desfoque).putFloat("vidro", vidro)
        .apply()
  }

  /** Usa uma foto da galeria como fundo (reduzida para no máximo 1440 px). */
  fun definirFoto(ctx: Context, uri: Uri): Boolean {
    val bmp = runCatching { lerFoto(ctx, uri) }.getOrNull() ?: return false
    arquivo(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    fundoImagem = bmp.asImageBitmap()
    paletaFoto = paletaDaFoto(bmp)
    salvar(ctx)
    return true
  }

  fun removerFoto(ctx: Context) {
    arquivo(ctx).delete()
    fundoImagem = null
    paletaFoto = null
    usarCoresDaFoto = false
    salvar(ctx)
  }

  fun padrao(ctx: Context) {
    paletaId = "rubi"; escurecer = 0.55f; desfoque = 6f; vidro = 0.11f; usarCoresDaFoto = false
    salvar(ctx)
  }

  private fun lerFoto(ctx: Context, uri: Uri): Bitmap {
    val maximo = 1440
    if (Build.VERSION.SDK_INT >= 28) {
      runCatching {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
          val lado = max(info.size.width, info.size.height)
          if (lado > maximo) dec.setTargetSize(info.size.width * maximo / lado, info.size.height * maximo / lado)
          dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
      }
    }
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
    var amostra = 1
    while (max(o.outWidth, o.outHeight) / (amostra * 2) >= maximo) amostra *= 2
    return ctx.contentResolver.openInputStream(uri)!!.use {
      BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })!!
    }
  }
}

/**
 * Tira uma paleta da foto: acha a cor viva mais presente (por tom, pesando saturação e brilho)
 * e monta as variações (clara, principal, escura, fundos).
 */
fun paletaDaFoto(bmp: Bitmap): Paleta {
  val p = Bitmap.createScaledBitmap(bmp, 48, 48, true)
  val pesos = DoubleArray(36)
  val hsv = FloatArray(3)
  for (x in 0 until p.width) for (y in 0 until p.height) {
    android.graphics.Color.colorToHSV(p.getPixel(x, y), hsv)
    if (hsv[1] > 0.25f && hsv[2] > 0.25f) pesos[(hsv[0] / 10).toInt().coerceIn(0, 35)] += (hsv[1] * hsv[2]).toDouble()
  }
  val melhor = pesos.indices.maxByOrNull { pesos[it] } ?: 0
  val h = if (pesos[melhor] == 0.0) 350f else melhor * 10f + 5
  fun cor(hh: Float, s: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf((hh + 360) % 360, s, v)))
  return Paleta(
      "foto", "Cores da foto",
      coral = cor(h - 12, 0.45f, 1f), destaque = cor(h, 0.62f, 0.98f), escuro = cor(h + 8, 0.8f, 0.55f),
      fundo = cor(h, 0.45f, 0.05f), fundoTopo = cor(h, 0.6f, 0.17f), fundoMeio = cor(h, 0.5f, 0.09f),
      brilhos = listOf(cor(h, 0.7f, 0.9f), cor(h + 40, 0.6f, 0.8f), cor(h - 25, 0.5f, 0.95f)),
      solida = cor(h, 0.35f, 0.11f),
  )
}
