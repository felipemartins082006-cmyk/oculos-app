package com.martins.logbook

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val Fundo = Color(0xFF0E0E0E)
val Superficie = Color(0xFF1A1A1A)
val Superficie2 = Color(0xFF242424)
val Laranja = Color(0xFFFF8A3D)
val Texto = Color(0xFFF2F2F2)
val Cinza = Color(0xFF9A9A9A)
val Verde = Color(0xFF7BD88F)
val Vermelho = Color(0xFFFF6B6B)

@Composable
fun Tema(content: @Composable () -> Unit) {
  MaterialTheme(
      colorScheme = darkColorScheme(
          primary = Laranja,
          onPrimary = Color.Black,
          secondaryContainer = Color(0xFF3A2414),
          onSecondaryContainer = Laranja,
          background = Fundo,
          surface = Superficie,
          surfaceContainer = Superficie,
          surfaceContainerHigh = Superficie2,
          surfaceContainerHighest = Superficie2,
          onSurface = Texto,
          onSurfaceVariant = Cinza,
          error = Vermelho,
      ),
      content = content,
  )
}

private val PT = Locale.forLanguageTag("pt-BR")
private val CURTA = DateTimeFormatter.ofPattern("dd/MM/yy", PT)
private val SEMANA_CURTA = DateTimeFormatter.ofPattern("EEE", PT)
private val SEMANA = DateTimeFormatter.ofPattern("EEEE", PT)
private val LONGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PT)

/** 21/07/25 */
fun dataCurta(d: LocalDate): String = d.format(CURTA)

/** Seg, 21/07/25 */
fun dataComSemana(d: LocalDate): String =
    d.format(SEMANA_CURTA).trimEnd('.').replaceFirstChar { it.titlecase(PT) } + ", " + d.format(CURTA)

/** Segunda-feira */
fun diaDaSemana(d: LocalDate): String = d.format(SEMANA).replaceFirstChar { it.titlecase(PT) }

/** 21 de julho de 2025 */
fun dataLonga(d: LocalDate): String = d.format(LONGA)

/** "94 kg × 4 (×5) · 72 kg × 8": junta séries iguais seguidas. */
fun resumoSeries(series: List<Serie>): String {
  val partes = mutableListOf<Pair<String, Int>>()
  for (s in series) {
    val t = fmtSerie(s)
    if (partes.isNotEmpty() && partes.last().first == t) partes[partes.lastIndex] = t to partes.last().second + 1
    else partes += t to 1
  }
  return partes.joinToString(" · ") { (t, n) -> if (n > 1) "$t (×$n)" else t }
}
