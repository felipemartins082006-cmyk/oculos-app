package com.martins.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Paleta: preto suave com brilho vermelho, vidro translúcido e vermelho-coral em degradê.
val Fundo = Color(0xFF07070A)
val FundoTopo = Color(0xFF1C0A10)
/** Cartões de vidro: branco bem transparente sobre o fundo. */
val Superficie = Color(0x10FFFFFF)
val Superficie2 = Color(0x1CFFFFFF)
/** Para o que precisa ser sólido (diálogos, menus). */
val SuperficieSolida = Color(0xFF16101A)
val Borda = Color(0x24FFFFFF)
val Destaque = Color(0xFFFF5C6C)
val DestaqueEscuro = Color(0xFFC2183B)
val Texto = Color(0xFFF4EEF0)
val Cinza = Color(0xFFA89FA3)
val Verde = Color(0xFF6EE7B7)
val Vermelho = Color(0xFFFF6B6B)

/** O degradê principal (botões, linha do gráfico, destaques). */
val DegradeDestaque = Brush.linearGradient(listOf(Color(0xFFFF7A6B), Destaque, DestaqueEscuro))
/** Borda dos cartões de vidro: clara em cima, puxando para o vermelho. */
val DegradeBorda = Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF), Color(0x40FF5C6C)))

@Composable
fun Tema(content: @Composable () -> Unit) {
  MaterialTheme(
      colorScheme = darkColorScheme(
          primary = Destaque,
          onPrimary = Color.White,
          secondaryContainer = Color(0x33FF5C6C),
          onSecondaryContainer = Destaque,
          background = Fundo,
          surface = SuperficieSolida,
          surfaceContainer = SuperficieSolida,
          surfaceContainerLow = SuperficieSolida,
          surfaceContainerHigh = Color(0xFF1F1724),
          surfaceContainerHighest = Color(0xFF2A1F2E),
          onSurface = Texto,
          onSurfaceVariant = Cinza,
          outline = Color(0x40FFFFFF),
          outlineVariant = Color(0x22FFFFFF),
          error = Vermelho,
      ),
      content = content,
  )
}

/** Fundo do app: preto com um brilho vermelho suave no alto e outro bem fraco embaixo. */
fun Modifier.fundoDoApp(): Modifier = this
    .background(Brush.verticalGradient(0f to FundoTopo, 0.45f to Fundo, 1f to Color(0xFF050507)))
    .drawBehind {
      drawCircle(
          Brush.radialGradient(listOf(Color(0x38E0284A), Color.Transparent), center = Offset(size.width * 0.9f, 0f), radius = size.width * 0.9f),
          radius = size.width * 0.9f, center = Offset(size.width * 0.9f, 0f),
      )
      drawCircle(
          Brush.radialGradient(listOf(Color(0x16FF5C6C), Color.Transparent), center = Offset(0f, size.height), radius = size.width * 0.8f),
          radius = size.width * 0.8f, center = Offset(0f, size.height),
      )
    }

/** Cartão de vidro: translúcido, cantos arredondados e borda em degradê. */
@Composable
fun Cartao(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
  val forma = RoundedCornerShape(20.dp)
  Column(
      modifier.fillMaxWidth()
          .clip(forma)
          .background(Superficie)
          .border(1.dp, DegradeBorda, forma)
          .let { if (onClick != null) it.clickable(onClick = onClick) else it }
          .padding(16.dp),
      content = content,
  )
}

/** Botão principal: vermelho em degradê. */
@Composable
fun BotaoDegrade(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
  val forma = RoundedCornerShape(16.dp)
  Row(
      modifier
          .defaultMinSize(minHeight = 48.dp)
          .clip(forma)
          .background(if (enabled) DegradeDestaque else Brush.linearGradient(listOf(Superficie2, Superficie2)))
          .clickable(enabled = enabled, onClick = onClick)
          .padding(horizontal = 20.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    CompositionLocalProvider(LocalContentColor provides if (enabled) Color.White else Cinza) {
      ProvideTextStyle(TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)) { content() }
    }
  }
}

/** Rótulo pequeno de seção, em maiúsculas e espaçado (o toque "tech"). */
@Composable
fun Rotulo(texto: String, modifier: Modifier = Modifier) {
  Text(
      texto.uppercase(), color = Cinza, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
      letterSpacing = 1.6.sp, modifier = modifier,
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
