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
import androidx.compose.material3.Shapes
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
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
// Cores do tema: vêm da aparência escolhida (paleta pronta ou cores da foto de fundo).
val Fundo: Color get() = Aparencia.paleta.fundo
val FundoTopo: Color get() = Aparencia.paleta.fundoTopo
/** Cartões de vidro: branco transparente (a transparência é ajustável). */
val Superficie: Color get() = Color.White.copy(alpha = Aparencia.vidro)
val Superficie2: Color get() = Color.White.copy(alpha = (Aparencia.vidro + 0.07f).coerceAtMost(0.5f))
/** Para o que precisa ser sólido (diálogos, menus, pop-ups). */
val SuperficieSolida: Color get() = Aparencia.paleta.solida
val Borda = Color(0x1AFFFFFF)
val Coral: Color get() = Aparencia.paleta.coral
val Destaque: Color get() = Aparencia.paleta.destaque
val DestaqueEscuro: Color get() = Aparencia.paleta.escuro
/** Cor do texto em cima do degradê principal. */
val SobreDestaque: Color get() = Aparencia.paleta.sobreDestaque
val Violeta = Color(0xFF8B5CF6)
val Texto = Color(0xFFF7F1F4)
val Cinza = Color(0xFFB3A6AE)
val Verde = Color(0xFF6EE7B7)
val Vermelho = Color(0xFFFF6B7D)

/** O degradê principal (botões, linha do gráfico, destaques). */
val DegradeDestaque: Brush get() = Brush.linearGradient(listOf(Coral, Destaque, DestaqueEscuro))
/** Borda bem leve dos cartões de vidro, puxando para a cor do tema. */
val DegradeBorda: Brush get() = Brush.linearGradient(listOf(Color(0x26FFFFFF), Color(0x0AFFFFFF), Destaque.copy(alpha = 0.16f)))
/** Preenchimento dos cartões: um pouco mais claro em cima (efeito de vidro). */
val DegradeCartao: Brush
  get() = Brush.verticalGradient(listOf(Color.White.copy(alpha = Aparencia.vidro + 0.05f), Color.White.copy(alpha = Aparencia.vidro * 0.55f)))
/** Fundo dos pop-ups e da barra flutuante (sólido, na cor do tema). */
val DegradePainel: Brush
  get() = Brush.verticalGradient(listOf(SuperficieSolida.copy(alpha = 0.97f), Fundo.copy(alpha = 0.97f)))

@Composable
fun Tema(content: @Composable () -> Unit) {
  MaterialTheme(
      colorScheme = darkColorScheme(
          primary = Destaque,
          onPrimary = SobreDestaque,
          secondaryContainer = Destaque.copy(alpha = 0.24f),
          onSecondaryContainer = Destaque,
          background = Fundo,
          surface = SuperficieSolida,
          surfaceContainer = SuperficieSolida,
          surfaceContainerLow = SuperficieSolida,
          surfaceContainerHigh = SuperficieSolida,
          surfaceContainerHighest = SuperficieSolida,
          onSurface = Texto,
          onSurfaceVariant = Cinza,
          outline = Color(0x33FFFFFF),
          outlineVariant = Color(0x1AFFFFFF),
          error = Vermelho,
      ),
      shapes = Shapes(
          extraSmall = RoundedCornerShape(16.dp),
          small = RoundedCornerShape(18.dp),
          medium = RoundedCornerShape(22.dp),
          large = RoundedCornerShape(28.dp),
          extraLarge = RoundedCornerShape(32.dp),
      ),
      content = content,
  )
}

/** Fundo do app (sem foto): degradê do tema com brilhos suaves nas cores da paleta. */
fun Modifier.fundoDoApp(): Modifier = this
    .background(Brush.verticalGradient(0f to FundoTopo, 0.35f to Aparencia.paleta.fundoMeio, 1f to Fundo))
    .drawBehind {
      fun brilho(cor: Color, x: Float, y: Float, raio: Float) = drawCircle(
          Brush.radialGradient(listOf(cor, Color.Transparent), center = Offset(x, y), radius = raio),
          radius = raio, center = Offset(x, y),
      )
      val b = Aparencia.paleta.brilhos
      brilho(b[0].copy(alpha = 0.28f), size.width * 0.95f, size.height * 0.02f, size.width * 0.95f)
      brilho(b[1].copy(alpha = 0.14f), size.width * -0.1f, size.height * 0.45f, size.width * 0.85f)
      brilho(b[2].copy(alpha = 0.12f), size.width * 1.05f, size.height * 0.95f, size.width * 0.8f)
    }

/** Camada de fundo do app: a foto escolhida (escurecida e desfocada) ou o degradê do tema. */
@Composable
fun FundoApp() {
  val img = Aparencia.fundoImagem
  if (img == null) {
    Box(Modifier.fillMaxSize().fundoDoApp())
    return
  }
  Box(Modifier.fillMaxSize().background(Fundo)) {
    androidx.compose.foundation.Image(
        img, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur(Aparencia.desfoque.dp),
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        FundoTopo.copy(alpha = (Aparencia.escurecer * 0.6f)), Color.Black.copy(alpha = Aparencia.escurecer),
        Fundo.copy(alpha = (Aparencia.escurecer + 0.15f).coerceAtMost(1f)),
    ))))
  }
}

/** Cartão de vidro: translúcido, cantos arredondados e borda em degradê. */
@Composable
fun Cartao(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
  val forma = RoundedCornerShape(28.dp)
  Column(
      modifier.fillMaxWidth()
          .clip(forma)
          .background(DegradeCartao)
          .border(0.7.dp, DegradeBorda, forma)
          .let { if (onClick != null) it.clickable(onClick = onClick) else it }
          .padding(horizontal = 20.dp, vertical = 18.dp),
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
  val forma = RoundedCornerShape(50)
  Row(
      modifier
          .defaultMinSize(minHeight = 50.dp)
          .clip(forma)
          .background(if (enabled) DegradeDestaque else Brush.linearGradient(listOf(Superficie2, Superficie2)))
          .clickable(enabled = enabled, onClick = onClick)
          .padding(horizontal = 20.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    CompositionLocalProvider(LocalContentColor provides if (enabled) SobreDestaque else Cinza) {
      ProvideTextStyle(TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)) { content() }
    }
  }
}

/** Rótulo pequeno de seção, em maiúsculas e espaçado (o toque "tech"). */
@Composable
fun Rotulo(texto: String, modifier: Modifier = Modifier) {
  Text(
      texto.uppercase(), color = Cinza, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
      letterSpacing = 1.3.sp, modifier = modifier,
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
