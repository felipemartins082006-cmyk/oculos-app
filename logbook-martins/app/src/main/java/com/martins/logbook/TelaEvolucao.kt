package com.martins.logbook

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun TelaExercicios(
    todas: List<Serie>,
    dias: Map<LocalDate, Dia>,
    onAbrir: (String) -> Unit,
    onAbrirDia: (LocalDate) -> Unit,
) {
  var busca by rememberSaveable { mutableStateOf("") }
  var secao by rememberSaveable { mutableIntStateOf(0) }
  val filtro = normalizarNome(busca)
  val lista = remember(todas) { resumos(todas) }.filter { filtro.isEmpty() || filtro in it.exercicio }

  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Rotulo("Evolução")
      Text(
          listOf("Seus exercícios", "Seus números", "Peso corporal", "O que mexe no seu treino")[secao],
          fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Texto,
      )
    }
    item { Segmentos(listOf("Exercícios", "Números", "Peso", "Insights"), secao) { secao = it } }
    if (secao == 1) {
      conteudoEstatisticas(todas, dias, onAbrirDia)
      return@LazyColumn
    }
    if (secao == 2) {
      conteudoPeso(dias, onAbrirDia)
      return@LazyColumn
    }
    if (secao == 3) {
      conteudoInsights(todas, dias, onAbrirDia)
      return@LazyColumn
    }
    item {
      OutlinedTextField(
          busca, { busca = it }, modifier = Modifier.fillMaxWidth(),
          placeholder = { Text("Buscar exercício") }, singleLine = true,
      )
    }
    if (lista.isEmpty()) item { Text("Nenhum exercício ainda.", color = Cinza) }
    items(lista, key = { it.exercicio }) { r ->
      Cartao(onClick = { onAbrir(r.exercicio) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          FiguraExercicio(r.exercicio, Modifier.size(52.dp))
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(nomeBonito(r.exercicio), color = Texto, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.treinos} ${if (r.treinos == 1) "treino" else "treinos"} · último ${dataCurta(r.ultimo)}",
                color = Cinza, fontSize = 13.sp)
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(fmtSerie(r.maiorCarga), color = Destaque, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("recorde", color = Cinza, fontSize = 11.sp)
          }
        }
      }
    }
  }
}

private enum class Metrica(val titulo: String, val unidade: String) {
  CARGA("Carga", "kg da série mais pesada do dia"),
  REPS("Reps", "repetições na série mais pesada do dia"),
  RM("1RM est.", "1RM estimado (Epley): junta carga e reps"),
  VOLUME("Volume", "volume do dia: soma de kg × reps"),
}

private enum class Periodo(val titulo: String, val meses: Long?) {
  M1("1M", 1), M3("3M", 3), M6("6M", 6), A1("1A", 12), TUDO("Tudo", null),
}

@Composable
fun TelaExercicio(
    lb: Logbook,
    todas: List<Serie>,
    exercicio: String,
    onVoltar: () -> Unit,
    onRenomeado: (String) -> Unit,
    onAbrirDia: (LocalDate) -> Unit,
) {
  BackHandler(onBack = onVoltar)
  var metrica by rememberSaveable { mutableStateOf(Metrica.CARGA) }
  var periodo by rememberSaveable { mutableStateOf(Periodo.TUDO) }
  var renomeando by remember { mutableStateOf(false) }
  var escolhendoIlustracao by rememberSaveable { mutableStateOf(false) }
  val figuras = LocalFiguras.current
  val ctx = LocalContext.current
  val escolherFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null && !figuras.usarFoto(exercicio, uri)) {
      Toast.makeText(ctx, "Não consegui abrir essa foto", Toast.LENGTH_LONG).show()
    }
  }

  if (escolhendoIlustracao) {
    BackHandler { escolhendoIlustracao = false }
    TelaEscolherIlustracao(
        exercicio,
        onEscolher = { figuras.usarIlustracao(exercicio, it); escolhendoIlustracao = false },
        onVoltar = { escolhendoIlustracao = false },
    )
    return
  }

  val hist = remember(todas, exercicio) { historicoDe(todas, exercicio) }
  val series = hist.flatMap { it.series }
  val limite = periodo.meses?.let { LocalDate.now().minusMonths(it) }
  val pontos = hist.filter { limite == null || it.data >= limite }.map { d ->
    when (metrica) {
      Metrica.CARGA -> Ponto(d.data, d.topo.peso, fmtSerie(d.topo))
      Metrica.REPS -> Ponto(d.data, d.topo.reps.toDouble(), "${d.topo.reps} reps · ${fmtPeso(d.topo.peso)} kg")
      Metrica.RM -> Ponto(d.data, d.melhorUmRm, "${fmtPeso(Math.round(d.melhorUmRm * 10) / 10.0)} kg (1RM est.)")
      Metrica.VOLUME -> Ponto(d.data, d.volume, "${fmtPeso(d.volume)} kg · ${d.series.size} séries")
    }
  }

  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onVoltar) { Text("‹", fontSize = 28.sp, color = Cinza) }
        Text(nomeBonito(exercicio), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Texto,
            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = { renomeando = true }) { Text("Renomear") }
      }
    }
    item {
      Cartao {
        val figura = figuras.de(exercicio)
        if (figura != null) {
          FiguraExercicio(exercicio, Modifier.fillMaxWidth().height(220.dp), animar = true)
          Text(
              when (figura) {
                is Figura.Foto -> "Sua foto"
                is Figura.DeIlustracao ->
                  figura.ilustracao.titulo + if (figura.automatica) " · achada pelo nome" else ""
              },
              color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
          )
        } else {
          Text(
              if (figuras.temEscolha(exercicio)) "Sem imagem para este exercício."
              else "Não achei uma ilustração para “${nomeBonito(exercicio)}”. Coloque uma foto sua " +
                  "(do aparelho, por exemplo) ou escolha uma ilustração.",
              color = Cinza, fontSize = 13.sp,
          )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
              onClick = { escolherFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
              modifier = Modifier.weight(1f),
          ) { Text("📷 Minha foto", maxLines = 1) }
          OutlinedButton(onClick = { escolhendoIlustracao = true }, modifier = Modifier.weight(1f)) {
            Text("🔍 Ilustração", maxLines = 1)
          }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          if (figuras.temEscolha(exercicio)) {
            TextButton(onClick = { figuras.automatica(exercicio) }) { Text("Voltar à automática") }
          }
          if (figura != null) {
            TextButton(onClick = { figuras.semFigura(exercicio) }) { Text("Sem imagem", color = Cinza) }
          }
        }
      }
    }
    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(Metrica.entries) { m ->
          FilterChip(selected = metrica == m, onClick = { metrica = m }, label = { Text(m.titulo) })
        }
      }
    }
    item {
      Cartao {
        Grafico(pontos, metrica.unidade)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          Periodo.entries.forEach { p ->
            TextButton(onClick = { periodo = p }) {
              Text(p.titulo, color = if (periodo == p) Destaque else Cinza,
                  fontWeight = if (periodo == p) FontWeight.Bold else FontWeight.Normal)
            }
          }
        }
      }
    }
    if (series.isNotEmpty()) item {
      val maiorCarga = series.maxWith(compareBy<Serie>({ it.peso }, { it.reps }))
      val melhorRm = series.maxBy { it.umRm }
      val maisReps = series.maxWith(compareBy<Serie>({ it.reps }, { it.peso }))
      val maiorVolume = hist.maxBy { it.volume }
      Cartao {
        Text("Recordes", color = Texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Recorde("Maior carga", fmtSerie(maiorCarga), maiorCarga.data)
        Recorde("Melhor 1RM estimado",
            "${fmtPeso(Math.round(melhorRm.umRm * 10) / 10.0)} kg (${fmtSerie(melhorRm)})", melhorRm.data)
        Recorde("Mais reps numa série", fmtSerie(maisReps), maisReps.data)
        Recorde("Maior volume num dia", "${fmtPeso(maiorVolume.volume)} kg", maiorVolume.data)
        Text("${hist.size} treinos · ${series.size} séries · desde ${dataCurta(hist.first().data)}",
            color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
      }
    }
    item { Text("Todos os treinos", color = Texto, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    items(hist.reversed(), key = { it.data.toEpochDay() }) { d ->
      Cartao(onClick = { onAbrirDia(d.data) }) {
        Text(dataComSemana(d.data), color = Cinza, fontSize = 13.sp)
        Text(resumoSeries(d.series), color = Texto, fontSize = 15.sp)
      }
    }
  }

  if (renomeando) {
    var nome by remember { mutableStateOf(exercicio) }
    val novo = normalizarNome(nome)
    val existe = novo != exercicio && todas.any { it.exercicio == novo }
    AlertDialog(
        onDismissRequest = { renomeando = false },
        title = { Text("Renomear exercício") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(nome, { nome = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(
                if (existe) "“${nomeBonito(novo)}” já existe: os dois vão virar um só exercício, com o histórico junto."
                else "Muda o nome em todas as ${series.size} séries.",
                color = if (existe) Destaque else Cinza, fontSize = 13.sp,
            )
          }
        },
        confirmButton = {
          TextButton(enabled = novo.isNotEmpty() && novo != exercicio, onClick = {
            lb.renomear(exercicio, novo)
            figuras.renomear(exercicio, novo)
            renomeando = false
            onRenomeado(novo)
          }) { Text(if (existe) "Juntar" else "Salvar") }
        },
        dismissButton = { TextButton(onClick = { renomeando = false }) { Text("Cancelar") } },
    )
  }
}

@Composable
private fun Recorde(titulo: String, valor: String, data: LocalDate) {
  Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(titulo, color = Cinza, fontSize = 12.sp)
      Text(valor, color = Destaque, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
    Text(dataCurta(data), color = Cinza, fontSize = 12.sp)
  }
}

/** Escolher outra ilustração: busca pelo nome (em inglês) e toca na figura. */
@Composable
private fun TelaEscolherIlustracao(exercicio: String, onEscolher: (Ilustracao) -> Unit, onVoltar: () -> Unit) {
  val buscador = LocalFiguras.current.buscador
  var busca by rememberSaveable { mutableStateOf(exercicio) }
  val lista = remember(busca) { buscador.buscar(busca) }
  LazyVerticalGrid(
      columns = GridCells.Fixed(3),
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(span = { GridItemSpan(maxLineSpan) }) {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          TextButton(onClick = onVoltar) { Text("‹", fontSize = 28.sp, color = Cinza) }
          Text("Escolher ilustração", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Texto)
        }
        OutlinedTextField(
            busca, { busca = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar (em inglês): squat, row, curl…") },
            singleLine = true,
        )
        Text("${lista.size} ilustrações", color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
      }
    }
    gridItems(lista, key = { it.id }) { il ->
      Column(Modifier.clickable { onEscolher(il) }) {
        FiguraIlustracao(il, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(il.titulo, color = Texto, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp, modifier = Modifier.padding(top = 4.dp))
      }
    }
    item(span = { GridItemSpan(maxLineSpan) }) {
      Text("Ilustrações: Everkinetic e wger (CC BY-SA)", color = Cinza, fontSize = 11.sp)
    }
  }
}
