package com.martins.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.abs

/** Chave de seleção em pílula: Exercícios | Peso | Insights. */
@Composable
fun Segmentos(opcoes: List<String>, selecionado: Int, onEscolher: (Int) -> Unit) {
  val forma = RoundedCornerShape(50)
  Row(
      Modifier.fillMaxWidth().clip(forma).background(Superficie).border(1.dp, Borda, forma).padding(4.dp),
  ) {
    opcoes.forEachIndexed { i, o ->
      val sel = i == selecionado
      Box(
          Modifier.weight(1f).clip(forma)
              .background(if (sel) DegradeDestaque else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
              .clickable { onEscolher(i) }
              .padding(vertical = 9.dp),
          contentAlignment = Alignment.Center,
      ) {
        Text(o, color = if (sel) Color.White else Cinza, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
      }
    }
  }
}

/** Peso corporal: gráfico por tipo de pesagem com a média dos últimos 7 dias. */
fun LazyListScope.conteudoPeso(dias: Map<LocalDate, Dia>, onAbrirDia: (LocalDate) -> Unit) {
  item {
    var tipo by rememberSaveable { mutableStateOf(TipoPesagem.JEJUM) }
    val pontos = dias.values.flatMap { d -> d.pesagens.filter { it.tipo == tipo }.take(1).map { d.data to it.kg } }
        .sortedBy { it.first }
    val media = mediaMovel(pontos)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Cartao {
        Rotulo("Peso corporal")
        val ultimo = pontos.lastOrNull()
        if (ultimo == null) {
          Text("Nenhuma pesagem ${tipo.titulo.lowercase()} ainda. Registre no início do treino ou na aba Dia.",
              color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
          Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text("${fmtPeso(ultimo.second)}", color = Texto, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(" kg", color = Cinza, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
              Text("média 7 dias", color = Cinza, fontSize = 11.sp)
              Text("${fmtPeso(Math.round(media.last().second * 10) / 10.0)} kg", color = Destaque, fontWeight = FontWeight.SemiBold)
            }
          }
          val mes = pontos.filter { it.first >= ultimo.first.minusDays(30) }
          if (mes.size >= 2) {
            val dif = mes.last().second - mes.first().second
            Text(
                "${if (dif >= 0) "+" else "−"}${fmtPeso(Math.round(abs(dif) * 10) / 10.0)} kg em 30 dias",
                color = Cinza, fontSize = 13.sp,
            )
          }
        }
      }
      Segmentos(TipoPesagem.entries.map { it.titulo }, tipo.ordinal) { tipo = TipoPesagem.entries[it] }
      Cartao {
        Grafico(
            pontos.map { (d, kg) -> Ponto(d, kg, "${fmtPeso(kg)} kg") },
            "pontos: ${tipo.titulo.lowercase()} (${tipo.descricao}) · tracejado: média dos últimos 7 dias",
            extra = media.map { (d, kg) -> Ponto(d, kg, "") },
            vazio = "Sem pesagens ${tipo.titulo.lowercase()}.",
        )
      }
      // Diferença típica entre as duas pesagens (roupa, comida e água do dia).
      val ambos = dias.values.mapNotNull { d ->
        val j = d.pesagens.firstOrNull { it.tipo == TipoPesagem.JEJUM }
        val s = d.pesagens.firstOrNull { it.tipo == TipoPesagem.SESSAO }
        if (j != null && s != null) s.kg - j.kg else null
      }
      Cartao {
        Rotulo("Jejum × sessão")
        Text(
            if (ambos.isEmpty()) "Na academia você pesa mais que em jejum (roupa, comida e água do dia). " +
                "Compare sempre o mesmo tipo. Se num dia você pesar dos dois jeitos, o app calcula a diferença típica."
            else "Na sessão você costuma pesar ${fmtPeso(Math.round(ambos.average() * 10) / 10.0)} kg a mais " +
                "que em jejum (média de ${ambos.size} dias).",
            color = Texto, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
        )
      }
    }
  }
  val recentes = dias.values.filter { it.pesagens.isNotEmpty() }.sortedByDescending { it.data }.take(20)
  if (recentes.isNotEmpty()) item { Rotulo("Pesagens recentes") }
  items(recentes, key = { "peso-${it.data}" }) { d ->
    Cartao(onClick = { onAbrirDia(d.data) }) {
      Row {
        Text(dataComSemana(d.data), color = Cinza, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(d.pesagens.joinToString("  ·  ") { "${fmtPeso(it.kg)} kg (${it.tipo.titulo.lowercase()})" }, color = Texto, fontSize = 13.sp)
      }
    }
  }
}

/** Insights: como sono, comida, cabeça e eventos andam junto com a sua performance. */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.conteudoInsights(series: List<Serie>, dias: Map<LocalDate, Dia>, onAbrirDia: (LocalDate) -> Unit) {
  val perf = performancePorDia(series)
  val comp = comparacoes(perf, dias)
  val comDiario = perf.keys.count { dias[it] != null }
  item {
    Cartao {
      Rotulo("Como ler")
      Text(
          "Cada treino é comparado com as suas últimas sessões dos mesmos exercícios (1RM estimado). " +
              "Aqui aparece como você rendeu em dias com e sem cada fator. São padrões nos seus registros, " +
              "não diagnóstico: quanto mais dias você anotar, mais confiável fica.",
          color = Texto, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
      )
      Text("${perf.size} treinos comparáveis · $comDiario com diário preenchido", color = Cinza, fontSize = 12.sp,
          modifier = Modifier.padding(top = 6.dp))
    }
  }
  if (comp.isEmpty()) {
    item {
      Cartao {
        Rotulo("Ainda sem padrões")
        Text(
            "Para comparar, cada lado precisa de pelo menos 3 treinos (ex.: 3 dias dormindo 7h ou mais e 3 com menos). " +
                "Preencha a aba Dia nos dias de treino: sono, humor, estresse, comida com horário.",
            color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
        )
      }
    }
  }
  items(comp, key = { "comp-${it.fator}" }) { c -> CartaoComparacao(c) }

  val quedas = perf.values.filter { it.variacao <= -0.03 }.sortedByDescending { it.data }.take(8)
  val picos = perf.values.filter { it.variacao >= 0.03 }.sortedByDescending { it.data }.take(8)
  if (quedas.isNotEmpty()) item { Rotulo("Treinos abaixo do normal", Modifier.padding(top = 6.dp)) }
  items(quedas, key = { "q-${it.data}" }) { p -> CartaoSessao(p, dias[p.data], onAbrirDia) }
  if (picos.isNotEmpty()) item { Rotulo("Treinos acima do normal", Modifier.padding(top = 6.dp)) }
  items(picos, key = { "p-${it.data}" }) { p -> CartaoSessao(p, dias[p.data], onAbrirDia) }
}

@Composable
private fun CartaoComparacao(c: Comparacao) {
  Cartao {
    Rotulo(c.fator)
    val maximo = maxOf(abs(c.mediaA), abs(c.mediaB), 0.01)
    Barra(c.grupoA, c.mediaA, c.nA, maximo)
    Barra(c.grupoB, c.mediaB, c.nB, maximo)
    val melhor = if (c.diferenca >= 0) c.grupoA else c.grupoB
    Text(
        "$melhor: ${fmtPct(abs(c.diferenca)).removePrefix("+")} a mais de rendimento, em média.",
        color = Texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp),
    )
  }
}

@Composable
private fun Barra(rotulo: String, valor: Double, n: Int, maximo: Double) {
  Column(Modifier.padding(top = 8.dp)) {
    Row {
      Text(rotulo, color = Texto, fontSize = 13.sp, modifier = Modifier.weight(1f))
      Text("${fmtPct(valor)}  ·  $n treinos", color = if (valor >= 0) Verde else Destaque, fontSize = 13.sp)
    }
    Box(Modifier.fillMaxWidth().height(8.dp).padding(top = 3.dp).clip(RoundedCornerShape(50)).background(Superficie2)) {
      Box(
          Modifier.fillMaxWidth((abs(valor) / maximo).toFloat().coerceIn(0.03f, 1f)).fillMaxHeight()
              .clip(RoundedCornerShape(50))
              .background(if (valor >= 0) Brush.horizontalGradient(listOf(Color(0xFF2FA982), Verde)) else DegradeDestaque),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartaoSessao(p: Performance, dia: Dia?, onAbrirDia: (LocalDate) -> Unit) {
  Cartao(onClick = { onAbrirDia(p.data) }) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(dataComSemana(p.data), color = Texto, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
      Text(fmtPct(p.variacao), color = if (p.variacao >= 0) Verde else Destaque, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
    val ctx = contextoDoDia(dia)
    if (ctx.isEmpty()) {
      Text("Sem diário neste dia.", color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    } else {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(top = 6.dp)) {
        ctx.forEach { c ->
          Text(c, color = Texto, fontSize = 12.sp,
              modifier = Modifier.clip(RoundedCornerShape(50)).background(Superficie2).padding(horizontal = 10.dp, vertical = 4.dp))
        }
      }
    }
  }
}
