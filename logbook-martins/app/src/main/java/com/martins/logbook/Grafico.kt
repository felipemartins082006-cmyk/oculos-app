package com.martins.logbook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Um ponto do gráfico: um dia de treino. */
data class Ponto(val data: LocalDate, val valor: Double, val rotulo: String)

/** Passo "bonito" para as linhas de grade: 1, 2, 2.5, 5 ×10ⁿ. */
private fun passoBonito(faixa: Double, linhas: Int): Double {
  val bruto = faixa / linhas
  val mag = 10.0.pow(floor(log10(bruto)))
  return listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= bruto }
}

/**
 * Gráfico de linha da evolução. Toque ou arraste para ver o valor de cada dia;
 * começa mostrando o último treino.
 */
@Composable
fun Grafico(
    pontos: List<Ponto>,
    unidade: String,
    modifier: Modifier = Modifier,
    /** Uma segunda linha (ex.: média móvel do peso), mais discreta. */
    extra: List<Ponto> = emptyList(),
    vazio: String = "Sem treinos neste período.",
) {
  var sel by remember(pontos) { mutableIntStateOf(pontos.lastIndex) }
  val medidor = rememberTextMeasurer()
  val estiloEixo = TextStyle(color = Cinza, fontSize = 11.sp)

  Column(modifier) {
    val p = pontos.getOrNull(sel)
    if (p != null) {
      Text(p.rotulo, color = Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
      Text(dataComSemana(p.data), color = Cinza, fontSize = 13.sp)
    }
    if (pontos.isEmpty()) {
      Text(vazio, color = Cinza, modifier = Modifier.height(220.dp))
      return@Column
    }

    val todos = pontos + extra
    val minV = todos.minOf { it.valor }
    val maxV = todos.maxOf { it.valor }
    val (lo, hi) = if (maxV - minV < 1e-6) (minV - 1).coerceAtLeast(0.0) to maxV + 1 else {
      val m = (maxV - minV) * 0.1
      (minV - m).coerceAtLeast(if (minV >= 0) 0.0 else minV - m) to maxV + m
    }
    val passo = passoBonito(hi - lo, 4)
    val grades = generateSequence(ceil(lo / passo) * passo) { it + passo }.takeWhile { it <= hi + 1e-9 }.toList()
    val d0 = pontos.first().data.toEpochDay()
    val dias = (pontos.last().data.toEpochDay() - d0).coerceAtLeast(1)

    Canvas(
        Modifier.fillMaxWidth().height(230.dp)
            .pointerInput(pontos) {
              detectTapGestures { o -> sel = maisPerto(pontos, o.x, 44.dp.toPx(), size.width - 54.dp.toPx(), d0, dias) }
            }
            .pointerInput(pontos) {
              detectHorizontalDragGestures { ch, _ ->
                sel = maisPerto(pontos, ch.position.x, 44.dp.toPx(), size.width - 54.dp.toPx(), d0, dias)
              }
            },
    ) {
      val esq = 44.dp.toPx()
      val dir = 10.dp.toPx()
      val topo = 14.dp.toPx()
      val base = size.height - 22.dp.toPx()
      val w = size.width - esq - dir
      fun x(d: LocalDate) =
          if (pontos.size == 1) esq + w / 2 else esq + w * ((d.toEpochDay() - d0).toFloat() / dias)
      fun y(v: Double) = (base - (base - topo) * ((v - lo) / (hi - lo))).toFloat()

      grades.forEach { g ->
        val yy = y(g)
        drawLine(Color(0x14FFFFFF), Offset(esq, yy), Offset(size.width - dir, yy), 1.dp.toPx())
        val t = medidor.measure(fmtPeso(g), estiloEixo)
        drawText(t, topLeft = Offset(esq - t.size.width - 6.dp.toPx(), yy - t.size.height / 2))
      }
      // Datas: primeira e última no eixo de baixo.
      val ini = medidor.measure(dataCurta(pontos.first().data), estiloEixo)
      drawText(ini, topLeft = Offset(esq, base + 6.dp.toPx()))
      if (pontos.size > 1) {
        val fim = medidor.measure(dataCurta(pontos.last().data), estiloEixo)
        drawText(fim, topLeft = Offset(size.width - dir - fim.size.width, base + 6.dp.toPx()))
      }

      val linha = Path()
      pontos.forEachIndexed { i, pt ->
        if (i == 0) linha.moveTo(x(pt.data), y(pt.valor)) else linha.lineTo(x(pt.data), y(pt.valor))
      }
      if (pontos.size > 1) {
        val area = Path().apply {
          addPath(linha)
          lineTo(x(pontos.last().data), base)
          lineTo(x(pontos.first().data), base)
          close()
        }
        drawPath(area, Brush.verticalGradient(listOf(Destaque.copy(alpha = 0.28f), Color.Transparent), topo, base))
        // Brilho embaixo da linha, depois a linha em degradê.
        drawPath(linha, Destaque.copy(alpha = 0.18f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(
            linha, Brush.horizontalGradient(listOf(DestaqueEscuro, Destaque, Color(0xFFFF8A7A)), esq, size.width - dir),
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
      }
      if (extra.size > 1) {
        val media = Path()
        extra.sortedBy { it.data }.forEachIndexed { i, pt ->
          if (i == 0) media.moveTo(x(pt.data), y(pt.valor)) else media.lineTo(x(pt.data), y(pt.valor))
        }
        drawPath(
            media, Color(0xB3FFFFFF),
            style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )
      }
      val raio = if (pontos.size > 60) 2.dp.toPx() else 3.5.dp.toPx()
      pontos.forEach { pt -> drawCircle(Destaque, raio, Offset(x(pt.data), y(pt.valor))) }

      pontos.getOrNull(sel)?.let { pt ->
        val c = Offset(x(pt.data), y(pt.valor))
        drawLine(Cinza.copy(alpha = 0.5f), Offset(c.x, topo), Offset(c.x, base), 1.dp.toPx())
        drawCircle(Destaque.copy(alpha = 0.25f), 12.dp.toPx(), c)
        drawCircle(Fundo, 7.dp.toPx(), c)
        drawCircle(Destaque, 5.dp.toPx(), c)
      }
    }
    Text(unidade, color = Cinza, fontSize = 11.sp)
  }
}

private fun maisPerto(pontos: List<Ponto>, px: Float, esq: Float, largura: Float, d0: Long, dias: Long): Int {
  if (pontos.size == 1) return 0
  val alvo = d0 + ((px - esq) / largura).coerceIn(0f, 1f) * dias
  return pontos.indices.minBy { abs(pontos[it].data.toEpochDay() - alvo) }
}
