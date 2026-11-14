package com.martins.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun TelaHistorico(
    todas: List<Serie>,
    diasDiario: Map<LocalDate, Dia>,
    onAbrirDia: (LocalDate) -> Unit,
    onApagarDia: (LocalDate) -> Unit = {},
) {
  var apagando by remember { mutableStateOf<LocalDate?>(null) }
  var busca by rememberSaveable { mutableStateOf("") }
  val filtro = normalizarNome(busca)
  val dias = remember(todas, filtro) {
    todas.groupBy { it.data }
        .filter { (_, ss) -> filtro.isEmpty() || ss.any { filtro in it.exercicio } }
        .toSortedMap(compareByDescending { it })
        .toList()
  }

  val perf = remember(todas) { performancePorDia(todas) }

  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Text("Histórico", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Texto)
      Text("${dias.size} dias de treino", color = Cinza, fontSize = 14.sp)
    }
    item {
      OutlinedTextField(
          busca, { busca = it },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text("Filtrar por exercício (ex.: squat)") },
          singleLine = true,
      )
    }
    items(dias, key = { it.first.toEpochDay() }) { (d, ss) ->
      Cartao(onClick = { onAbrirDia(d) }) {
        Row {
          Text(dataComSemana(d), fontWeight = FontWeight.Bold, color = Texto, fontSize = 16.sp, modifier = Modifier.weight(1f))
          perf[d]?.let {
            Text(fmtPct(it.variacao) + "  ", color = if (it.variacao >= 0) Verde else Destaque, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold)
          }
          Text("${ss.size} séries", color = Cinza, fontSize = 13.sp)
          Text(
              "  🗑", color = Cinza, fontSize = 15.sp,
              modifier = Modifier.clickable { apagando = d }.semantics { contentDescription = "Apagar treino de ${dataCurta(d)}" },
          )
        }
        // O contexto do dia (sono, humor, eventos…), quando você preencheu o diário.
        val ctx = contextoDoDia(diasDiario[d])
        if (ctx.isNotEmpty()) Text(ctx.joinToString(" · "), color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        ss.sortedBy { it.id }.groupBy { it.exercicio }.forEach { (ex, doEx) ->
          val destaque = filtro.isNotEmpty() && filtro in ex
          Text(
              nomeBonito(ex),
              color = if (destaque) Destaque else Texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(top = 8.dp),
          )
          Text(resumoSeries(doEx), color = Cinza, fontSize = 13.sp)
        }
      }
    }
  }

  apagando?.let { d ->
    val n = todas.count { it.data == d }
    AlertDialog(
        onDismissRequest = { apagando = null },
        title = { Text("Apagar o treino de ${dataComSemana(d)}?") },
        text = { Text("As $n séries deste dia e o tempo do cronômetro serão apagados. O diário continua. Isso não dá para desfazer.") },
        confirmButton = { TextButton(onClick = { onApagarDia(d); apagando = null }) { Text("Apagar", color = Vermelho) } },
        dismissButton = { TextButton(onClick = { apagando = null }) { Text("Cancelar") } },
    )
  }
}
