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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun TelaHistorico(todas: List<Serie>, onAbrirDia: (LocalDate) -> Unit) {
  var busca by rememberSaveable { mutableStateOf("") }
  val filtro = normalizarNome(busca)
  val dias = remember(todas, filtro) {
    todas.groupBy { it.data }
        .filter { (_, ss) -> filtro.isEmpty() || ss.any { filtro in it.exercicio } }
        .toSortedMap(compareByDescending { it })
        .toList()
  }

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
          Text("${ss.size} séries", color = Cinza, fontSize = 13.sp)
        }
        ss.sortedBy { it.id }.groupBy { it.exercicio }.forEach { (ex, doEx) ->
          val destaque = filtro.isNotEmpty() && filtro in ex
          Text(
              nomeBonito(ex),
              color = if (destaque) Laranja else Texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(top = 8.dp),
          )
          Text(resumoSeries(doEx), color = Cinza, fontSize = 13.sp)
        }
      }
    }
  }
}
