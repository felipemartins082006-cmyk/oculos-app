package com.martins.logbook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Ícones desenhados à mão (traço arredondado), para não depender de biblioteca de ícones. */
enum class Desenho { HALTER, SOL, CALENDARIO, GRAFICO, NUVEM }

@Composable
fun Icone(desenho: Desenho, cor: Color, modifier: Modifier = Modifier.size(22.dp)) {
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    val traco = Stroke(w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (desenho) {
      Desenho.HALTER -> {
        drawLine(cor, Offset(w * 0.25f, h / 2), Offset(w * 0.75f, h / 2), w * 0.09f, StrokeCap.Round)
        listOf(0.12f, 0.70f).forEach { x ->
          drawRoundRect(cor, Offset(w * x, h * 0.25f), Size(w * 0.18f, h * 0.5f), CornerRadius(w * 0.05f))
        }
        listOf(0.04f, 0.88f).forEach { x ->
          drawRoundRect(cor, Offset(w * x, h * 0.36f), Size(w * 0.08f, h * 0.28f), CornerRadius(w * 0.04f))
        }
      }
      Desenho.SOL -> {
        drawCircle(cor, w * 0.18f, Offset(w / 2, h / 2), style = traco)
        repeat(8) { i ->
          val a = Math.toRadians(i * 45.0)
          drawLine(
              cor, Offset(w / 2 + cos(a).toFloat() * w * 0.32f, h / 2 + sin(a).toFloat() * h * 0.32f),
              Offset(w / 2 + cos(a).toFloat() * w * 0.44f, h / 2 + sin(a).toFloat() * h * 0.44f), w * 0.08f, StrokeCap.Round,
          )
        }
      }
      Desenho.CALENDARIO -> {
        drawRoundRect(cor, Offset(w * 0.12f, h * 0.2f), Size(w * 0.76f, h * 0.68f), CornerRadius(w * 0.14f), style = traco)
        drawLine(cor, Offset(w * 0.12f, h * 0.42f), Offset(w * 0.88f, h * 0.42f), w * 0.08f)
        listOf(0.33f, 0.67f).forEach { x -> drawLine(cor, Offset(w * x, h * 0.1f), Offset(w * x, h * 0.28f), w * 0.09f, StrokeCap.Round) }
        drawCircle(cor, w * 0.06f, Offset(w * 0.38f, h * 0.64f))
        drawCircle(cor, w * 0.06f, Offset(w * 0.62f, h * 0.64f))
      }
      Desenho.GRAFICO -> {
        val p = Path().apply {
          moveTo(w * 0.1f, h * 0.78f); lineTo(w * 0.38f, h * 0.5f); lineTo(w * 0.56f, h * 0.64f); lineTo(w * 0.88f, h * 0.26f)
        }
        drawPath(p, cor, style = traco)
        val seta = Path().apply { moveTo(w * 0.64f, h * 0.24f); lineTo(w * 0.9f, h * 0.22f); lineTo(w * 0.88f, h * 0.48f) }
        drawPath(seta, cor, style = traco)
      }
      Desenho.NUVEM -> {
        val p = Path().apply {
          moveTo(w * 0.28f, h * 0.72f)
          cubicTo(w * 0.02f, h * 0.72f, w * 0.04f, h * 0.38f, w * 0.3f, h * 0.42f)
          cubicTo(w * 0.34f, h * 0.12f, w * 0.72f, h * 0.12f, w * 0.74f, h * 0.4f)
          cubicTo(w * 0.98f, h * 0.38f, w * 1.0f, h * 0.72f, w * 0.74f, h * 0.72f)
        }
        drawPath(p, cor, style = traco)
        drawLine(cor, Offset(w / 2, h * 0.46f), Offset(w / 2, h * 0.9f), w * 0.09f, StrokeCap.Round)
        val seta = Path().apply { moveTo(w * 0.38f, h * 0.78f); lineTo(w / 2, h * 0.9f); lineTo(w * 0.62f, h * 0.78f) }
        drawPath(seta, cor, style = traco)
      }
    }
  }
}

data class ItemNav(val titulo: String, val desenho: Desenho)

/** Barra de navegação flutuante: pílula de vidro, item escolhido em degradê. */
@Composable
fun BarraFlutuante(itens: List<ItemNav>, selecionado: Int, onEscolher: (Int) -> Unit) {
  val forma = RoundedCornerShape(32.dp)
  Row(
      Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth()
          .clip(forma)
          .background(DegradePainel)
          .border(0.7.dp, DegradeBorda, forma)
          .padding(horizontal = 6.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    itens.forEachIndexed { i, item ->
      val sel = i == selecionado
      Column(
          Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
              .clickable(remember { MutableInteractionSource() }, indication = null) { onEscolher(i) }
              .padding(vertical = 4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
            Modifier.width(48.dp).height(30.dp).clip(RoundedCornerShape(50))
                .background(
                    when {
                      !sel -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                      Aparencia.minimalista -> Brush.linearGradient(listOf(Destaque.copy(alpha = 0.16f), Destaque.copy(alpha = 0.16f)))
                      else -> DegradeDestaque
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { Icone(item.desenho, if (!sel) Cinza else if (Aparencia.minimalista) Destaque else SobreDestaque) }
        Text(
            item.titulo, color = if (sel) Texto else Cinza, fontSize = 11.sp, maxLines = 1,
            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 3.dp),
        )
      }
    }
  }
}
