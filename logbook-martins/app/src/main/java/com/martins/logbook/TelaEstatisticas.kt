package com.martins.logbook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Tons de vermelho a vinho, mais um cinza para "outros" (fatias da pizza). */
private val CORES_PIZZA = listOf(
    Color(0xFFFF5C6C), Color(0xFFC2183B), Color(0xFFFF9A8B), Color(0xFF7A0F28), Color(0xFFFFC9C0),
    Color(0xFFE0284A), Color(0xFF8A7F83),
)
private val PT = Locale.forLanguageTag("pt-BR")

private enum class PeriodoNumeros(val titulo: String, val meses: Long?) {
  M1("30 dias", 1), M3("3 meses", 3), A1("12 meses", 12), TUDO("Tudo", null),
}

/** Estatísticas: números do período, pizza dos grupos musculares, barras, tempo, recordes e progressões. */
fun LazyListScope.conteudoEstatisticas(series: List<Serie>, dias: Map<LocalDate, Dia>, onAbrirDia: (LocalDate) -> Unit) {
  item {
    var p by rememberSaveable { mutableIntStateOf(PeriodoNumeros.A1.ordinal) }
    val periodo = PeriodoNumeros.entries[p]
    val hoje = LocalDate.now()
    val e = calcularEstatisticas(series, dias, periodo.meses?.let { hoje.minusMonths(it).plusDays(1) }, hoje)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Segmentos(PeriodoNumeros.entries.map { it.titulo }, p) { p = it }
      if (e.treinos == 0) {
        Cartao { Text("Nenhum treino nesse período.", color = Cinza) }
        return@Column
      }

      // Números grandes
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Numero("Treinos", "${e.treinos}", "nos ${periodo.titulo.lowercase()}".replace("nos tudo", "no total"), Modifier.weight(1f))
        Numero("Por semana", String.format(PT, "%.1f", e.porSemana), "média de treinos", Modifier.weight(1f))
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Numero("Séries", "${e.series}", "anotadas", Modifier.weight(1f))
        Numero("Volume", fmtVolume(e.volumeKg), "kg × reps somados", Modifier.weight(1f))
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Numero("Sequência", "${e.sequenciaAtual} sem.", "seguidas treinando", Modifier.weight(1f))
        Numero("Recorde", "${e.maiorSequencia} sem.", "maior sequência", Modifier.weight(1f))
      }

      // Tempo
      Cartao {
        Rotulo("Tempo de treino")
        if (e.treinosCronometrados == 0) {
          Text(
              "Use o cronômetro no Treino (⏱ Iniciar treino / ✓ Concluir treino) para ver aqui quanto tempo você treina e descansa.",
              color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
          )
        } else {
          Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            MiniNumero("Duração média", fmtMinutos(e.duracaoMediaMs!!), Modifier.weight(1f))
            MiniNumero("Total", fmtMinutos(e.tempoTotalMs), Modifier.weight(1f))
            MiniNumero("Descanso médio", e.descansoMedioMs?.let { fmtDuracao(it) } ?: "—", Modifier.weight(1f))
          }
          if (e.tempoTotalMs > 0 && e.descansoTotalMs > 0) {
            val frac = (e.descansoTotalMs.toFloat() / e.tempoTotalMs).coerceIn(0f, 1f)
            Text("Do tempo na academia, ${(frac * 100).toInt()}% foi descanso entre séries",
                color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Superficie2)) {
              Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(50)).background(DegradeDestaque))
            }
          }
          Text("${e.treinosCronometrados} treinos com tempo marcado", color = Cinza, fontSize = 11.sp,
              modifier = Modifier.padding(top = 6.dp))
        }
      }

      // Pizza dos grupos musculares
      Cartao {
        Rotulo("O que você mais treina")
        val total = e.porGrupo.sumOf { it.second }.coerceAtLeast(1)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
          Pizza(
              e.porGrupo.map { it.second.toFloat() },
              Modifier.size(140.dp).semantics { contentDescription = "Gráfico de pizza dos grupos musculares" },
              centro = e.porGrupo.firstOrNull()?.first?.titulo ?: "",
          )
          Spacer(Modifier.width(16.dp))
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            e.porGrupo.forEachIndexed { i, (g, n) ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(CORES_PIZZA[i % CORES_PIZZA.size]))
                Spacer(Modifier.width(8.dp))
                Text(g.titulo, color = Texto, fontSize = 13.sp, maxLines = 1, modifier = Modifier.width(112.dp))
                Text("${n * 100 / total}%", color = Cinza, fontSize = 13.sp)
              }
            }
          }
        }
        Text("pela quantidade de séries", color = Cinza, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
      }

      // Músculos específicos (toque para ver os exercícios) e calendário de treino/descanso
      val desde = periodo.meses?.let { hoje.minusMonths(it).plusDays(1) }
      CartaoMusculos(series.filter { desde == null || it.data >= desde })
      CartaoCalendario(series, dias, onAbrirDia)

      // Barras: por mês e por dia da semana
      Cartao {
        Rotulo("Treinos por mês")
        Barras(
            e.porMes.map { (m, n) -> m.month.getDisplayName(TextStyle.SHORT, PT).trimEnd('.').take(3) to n },
            Modifier.padding(top = 10.dp),
        )
      }
      Cartao {
        Rotulo("Dia da semana")
        val nomes = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
        Barras(nomes.zip(e.porDiaSemana), Modifier.padding(top = 10.dp))
        val fav = e.porDiaSemana.indices.maxByOrNull { e.porDiaSemana[it] }
        if (fav != null && e.porDiaSemana[fav] > 0) {
          Text("Você treina mais às ${listOf("segundas", "terças", "quartas", "quintas", "sextas", "sábados", "domingos")[fav]}",
              color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
      }

      // Recordes
      Cartao {
        Rotulo("Recordes do período")
        e.maiorCarga?.let { Linha("Maior carga", "${fmtSerie(it)} · ${nomeBonito(it.exercicio)}", dataCurta(it.data)) }
        e.melhorUmRm?.let {
          Linha("Maior 1RM estimado", "${fmtPeso(Math.round(it.umRm * 10) / 10.0)} kg · ${nomeBonito(it.exercicio)}", dataCurta(it.data))
        }
        e.maiorVolumeDia?.let { (d, v) -> Linha("Maior volume num treino", fmtVolume(v), dataCurta(d)) }
        e.favorito?.let { (ex, n) -> Linha("Exercício mais feito", nomeBonito(ex), "$n treinos") }
      }

      // Progressões
      if (e.progressoes.isNotEmpty()) {
        Cartao {
          Rotulo("Maior progressão de carga")
          val maximo = e.progressoes.maxOf { kotlin.math.abs(it.variacao) }.coerceAtLeast(0.01)
          e.progressoes.forEach { pr ->
            Column(Modifier.padding(top = 10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nomeBonito(pr.exercicio), color = Texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fmtPct(pr.variacao), color = if (pr.variacao >= 0) Verde else Destaque, fontWeight = FontWeight.Bold)
              }
              Text("1RM est. ${fmtPeso(Math.round(pr.de).toDouble())} → ${fmtPeso(Math.round(pr.para).toDouble())} kg · ${dataCurta(pr.desde)} a ${dataCurta(pr.ate)}",
                  color = Cinza, fontSize = 12.sp)
              Box(Modifier.fillMaxWidth().height(8.dp).padding(top = 3.dp).clip(RoundedCornerShape(50)).background(Superficie2)) {
                Box(
                    Modifier.fillMaxWidth((kotlin.math.abs(pr.variacao) / maximo).toFloat().coerceIn(0.03f, 1f)).fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(if (pr.variacao >= 0) Brush.horizontalGradient(listOf(Color(0xFF2FA982), Verde)) else DegradeDestaque),
                )
              }
            }
          }
        }
      }
    }
  }

  // Melhores treinos (vs as suas últimas sessões)
  val perf = performancePorDia(series).values.sortedByDescending { it.variacao }.take(3)
  if (perf.isNotEmpty()) item { Rotulo("Seus melhores treinos", Modifier.padding(top = 6.dp)) }
  items(perf, key = { "melhor-${it.data}" }) { p -> CartaoSessao(p, dias[p.data], onAbrirDia) }
}

@Composable
private fun Numero(titulo: String, valor: String, sub: String, modifier: Modifier) {
  Cartao(modifier) {
    Rotulo(titulo)
    Text(valor, color = Texto, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    Text(sub, color = Cinza, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun MiniNumero(titulo: String, valor: String, modifier: Modifier) {
  Column(modifier) {
    Text(valor, color = Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(titulo, color = Cinza, fontSize = 11.sp)
  }
}

@Composable
private fun Linha(titulo: String, valor: String, lado: String) {
  Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(titulo, color = Cinza, fontSize = 12.sp)
      Text(valor, color = Destaque, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
    Text(lado, color = Cinza, fontSize = 12.sp)
  }
}

/** Pizza em anel (rosca), com o maior grupo escrito no meio. */
@Composable
fun Pizza(valores: List<Float>, modifier: Modifier = Modifier, centro: String = "") {
  val total = valores.sum().takeIf { it > 0 } ?: 1f
  Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
      val esp = size.minDimension * 0.18f
      val tam = Size(size.minDimension - esp, size.minDimension - esp)
      val topo = Offset((size.width - tam.width) / 2, (size.height - tam.height) / 2)
      var inicio = -90f
      valores.forEachIndexed { i, v ->
        val ang = 360f * v / total
        drawArc(CORES_PIZZA[i % CORES_PIZZA.size], inicio, (ang - 1.5f).coerceAtLeast(0.5f), false, topo, tam, style = Stroke(esp))
        inicio += ang
      }
    }
    Text(centro, color = Texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 30.dp))
  }
}

/** Barras verticais em degradê, com o número em cima de cada uma. */
@Composable
fun Barras(dados: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
  val maximo = (dados.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
  Row(modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
    dados.forEach { (rotulo, n) ->
      Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Text(if (n > 0) "$n" else "", color = Texto, fontSize = 11.sp)
        Box(
            Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.78f * n / maximo + 0.01f)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(if (n > 0) Brush.verticalGradient(listOf(Color(0xFFFF8A7A), Destaque, DestaqueEscuro)) else Brush.linearGradient(listOf(Superficie2, Superficie2))),
        )
        Text(rotulo, color = Cinza, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
      }
    }
  }
}

/** Barras por músculo; tocar num músculo mostra quais exercícios contaram. */
@Composable
private fun CartaoMusculos(series: List<Serie>) {
  val trabalho = remember(series) { trabalhoPorMusculo(series) }
  var aberto by remember { mutableStateOf<Musculo?>(null) }
  Cartao {
    Rotulo("Músculos mais treinados")
    Text("toque num músculo para ver os exercícios · ajudante conta meia série", color = Cinza, fontSize = 11.sp)
    if (trabalho.isEmpty()) Text("Nenhum exercício reconhecido.", color = Cinza, fontSize = 13.sp)
    val maximo = trabalho.maxOfOrNull { it.series }?.coerceAtLeast(1.0) ?: 1.0
    val total = trabalho.sumOf { it.series }.coerceAtLeast(1.0)
    trabalho.forEach { t ->
      val sel = aberto == t.musculo
      Column(
          Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
              .clickable { aberto = if (sel) null else t.musculo }
              .semantics { contentDescription = "Músculo ${t.musculo.titulo}" }
              .padding(vertical = 2.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(t.musculo.titulo, color = if (sel) Destaque else Texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
              modifier = Modifier.width(130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
          Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(Superficie2)) {
            Box(Modifier.fillMaxWidth((t.series / maximo).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(50)).background(DegradeDestaque))
          }
          Text("  ${(t.series * 100 / total).toInt()}%", color = Cinza, fontSize = 12.sp, modifier = Modifier.width(44.dp))
        }
        if (sel) {
          t.exercicios.entries.sortedByDescending { it.value }.take(6).forEach { (ex, n) ->
            Text("• ${nomeBonito(ex)}: ${fmtPeso(n)} séries", color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
          }
        }
      }
    }
  }
}

/** Últimas 16 semanas: cada quadrado é um dia (treino, descanso marcado ou sem registro). Toque para abrir. */
@Composable
private fun CartaoCalendario(series: List<Serie>, dias: Map<LocalDate, Dia>, onAbrirDia: (LocalDate) -> Unit) {
  val hoje = LocalDate.now()
  val semanas = 16
  val inicio = hoje.with(java.time.DayOfWeek.MONDAY).minusWeeks(semanas - 1L)
  val volume = remember(series) { series.groupBy { it.data }.mapValues { (_, l) -> l.sumOf { it.volume } } }
  val maxVol = volume.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
  val datas = generateSequence(inicio) { it.plusDays(1) }.takeWhile { it <= hoje }.toList()
  val nTreino = datas.count { ehDiaDeTreino(it, series.filter { s -> s.data == it }, dias[it]) }
  val nDescanso = datas.count { dias[it]?.descanso == true && volume[it] == null }
  Cartao {
    Rotulo("Treino e descanso · 16 semanas")
    Text("$nTreino dias de treino · $nDescanso de descanso marcados · ${datas.size - nTreino - nDescanso} sem registro",
        color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf("S", "T", "Q", "Q", "S", "S", "D").forEach {
          Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) { Text(it, color = Cinza, fontSize = 9.sp) }
        }
      }
      (0 until semanas).forEach { w ->
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          (0 until 7).forEach { dow ->
            val d = inicio.plusWeeks(w.toLong()).plusDays(dow.toLong())
            val v = volume[d]
            val descanso = v == null && dias[d]?.descanso == true
            val cor = when {
              d > hoje -> Color.Transparent
              v != null -> Destaque.copy(alpha = (0.35f + 0.65f * (v / maxVol).toFloat()).coerceIn(0.35f, 1f))
              dias[d]?.descanso == false -> Destaque.copy(alpha = 0.35f)
              descanso -> Color(0xFF8C84B8)
              else -> Superficie2
            }
            Box(
                Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(cor)
                    .let { if (d <= hoje) it.clickable { onAbrirDia(d) } else it }
                    .semantics { contentDescription = "Dia ${dataCurta(d)}" },
            )
          }
        }
      }
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Legenda(Destaque, "treino (mais forte = mais volume)")
      Spacer(Modifier.width(10.dp))
      Legenda(Color(0xFF8C84B8), "descanso")
    }
    // Descanso entre treinos (dias sem treinar entre um treino e o próximo).
    val datasTreino = series.map { it.data }.distinct().sorted()
    val intervalos = datasTreino.zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(a, b) - 1 }.filter { it in 0..14 }
    if (intervalos.isNotEmpty()) {
      Text("Em média você descansa ${String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", intervalos.average())} dia(s) entre um treino e outro",
          color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
  }
}

@Composable
private fun Legenda(cor: Color, texto: String) {
  Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(cor))
  Text(" $texto", color = Cinza, fontSize = 11.sp)
}
