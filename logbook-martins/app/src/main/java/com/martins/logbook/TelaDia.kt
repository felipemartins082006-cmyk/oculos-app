package com.martins.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/**
 * O diário do dia: peso, sono, cabeça (humor, estresse, como foi o dia, eventos) e alimentação.
 * Serve para entender a performance: não é diagnóstico, é o contexto de cada treino.
 */
@Composable
fun TelaDia(diario: Diario, series: List<Serie>, dia: LocalDate, onDia: (LocalDate) -> Unit, onAbrirTreino: () -> Unit) {
  val d = diario.de(dia)
  val hoje = LocalDate.now()
  var novaRefeicao by remember(dia) { mutableStateOf(false) }

  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onDia(dia.minusDays(1)) }) { Text("‹", fontSize = 28.sp, color = Cinza) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
          Rotulo("Diário")
          Text(if (dia == hoje) "Hoje · ${diaDaSemana(dia)}" else diaDaSemana(dia), fontSize = 22.sp,
              fontWeight = FontWeight.Bold, color = Texto)
          Text(dataLonga(dia), color = Cinza, fontSize = 14.sp)
        }
        TextButton(onClick = { onDia(dia.plusDays(1)) }, enabled = dia < hoje) {
          Text("›", fontSize = 28.sp, color = if (dia < hoje) Cinza else Color.Transparent)
        }
      }
    }

    // Corpo
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PesarHoje(onSalvar = { kg, tipo -> diario.pesar(dia, if (dia == hoje) horaAgora() else "12:00", kg, tipo) })
        if (d.pesagens.isNotEmpty()) {
          Cartao {
            Rotulo("Pesagens do dia")
            d.pesagens.forEach { p ->
              Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${fmtPeso(p.kg)} kg", color = Texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Etiqueta(p.tipo.titulo, if (p.tipo == TipoPesagem.JEJUM) Destaque else Cinza)
                Spacer(Modifier.weight(1f))
                Text(p.hora, color = Cinza, fontSize = 13.sp)
                TextButton(onClick = { diario.alterar(dia) { it.copy(pesagens = it.pesagens - p) } }) {
                  Text("✕", color = Cinza)
                }
              }
            }
          }
        }
      }
    }

    // Sono
    item {
      Cartao {
        Rotulo("Sono · noite passada")
        var horas by remember(dia) { mutableStateOf(d.sonoHoras?.let { fmtPeso(it) } ?: "") }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
              horas,
              { t ->
                horas = t
                val h = t.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..24.0 }
                diario.alterar(dia) { it.copy(sonoHoras = h) }
              },
              modifier = Modifier.width(130.dp),
              placeholder = { Text("7.5") },
              suffix = { Text("h") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          )
          Spacer(Modifier.width(12.dp))
          Text(
              d.sonoHoras?.let { "Dormiu ${fmtHoras(it)}" } ?: "Quantas horas você dormiu?",
              color = if (d.sonoHoras != null) Texto else Cinza, fontSize = 14.sp,
          )
        }
        Text("Qualidade", color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        Escala(
            valor = d.sonoQualidade,
            rotulos = listOf("😫", "😕", "😐", "🙂", "😴"),
            legenda = listOf("Péssimo", "Ruim", "Ok", "Bom", "Ótimo"),
            descricao = "Qualidade do sono",
            onEscolher = { v -> diario.alterar(dia) { it.copy(sonoQualidade = v) } },
        )
        CampoTexto(
            chave = "sono-$dia", valor = d.sonoNota, dica = "Acordou no meio da noite? Sonhou? (opcional)",
            onMudar = { t -> diario.alterar(dia) { it.copy(sonoNota = t) } },
        )
      }
    }

    // Cabeça
    item {
      Cartao {
        Rotulo("Cabeça e dia")
        Text("Humor", color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Escala(
            valor = d.humor, rotulos = listOf("😞", "🙁", "😐", "🙂", "😄"),
            legenda = listOf("Muito mal", "Mal", "Normal", "Bem", "Ótimo"), descricao = "Humor",
            onEscolher = { v -> diario.alterar(dia) { it.copy(humor = v) } },
        )
        Text("Estresse", color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        Escala(
            valor = d.estresse, rotulos = listOf("😌", "🙂", "😬", "😣", "🤯"),
            legenda = listOf("Tranquilo", "Leve", "Médio", "Alto", "Muito alto"), descricao = "Estresse",
            onEscolher = { v -> diario.alterar(dia) { it.copy(estresse = v) } },
        )
        CampoTexto(
            chave = "dia-$dia", valor = d.notaDia, linhas = 3,
            dica = "Como foi seu dia? Ex.: apresento o TCC amanhã, fiquei ansioso.",
            onMudar = { t -> diario.alterar(dia) { it.copy(notaDia = t) } },
        )
        Text("Algo marcante hoje?", color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        Eventos(d.eventos) { lista -> diario.alterar(dia) { it.copy(eventos = lista) } }
      }
    }

    // Alimentação
    item {
      Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Rotulo("Alimentação", Modifier.weight(1f))
          if (d.kcal > 0) Text("${d.kcal.toInt()} kcal no dia", color = Destaque, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        if (d.refeicoes.isEmpty() && !novaRefeicao) {
          Text("Nada registrado. Anote o que comeu, com o horário: isso ajuda a ver como a comida mexe no treino.",
              color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        d.refeicoes.sortedBy { it.hora }.forEach { r ->
          RefeicaoLinha(r, treinoHora = d.treinoHora, onApagar = { diario.alterar(dia) { it.copy(refeicoes = it.refeicoes - r) } })
        }
        if (novaRefeicao) {
          NovaRefeicao(
              horaInicial = if (dia == hoje) horaAgora() else "12:00",
              onSalvar = { tipo, hora, itens ->
                diario.alterar(dia) {
                  it.copy(refeicoes = it.refeicoes + Refeicao(diario.novoId(), hora, tipo, itens))
                }
                novaRefeicao = false
              },
              onCancelar = { novaRefeicao = false },
          )
        } else {
          OutlinedButton(onClick = { novaRefeicao = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("+ Refeição")
          }
        }
      }
    }

    // Treino do dia
    item {
      val doDia = series.filter { it.data == dia }
      val perf = performancePorDia(series)[dia]
      Cartao {
        Rotulo("Treino")
        if (doDia.isEmpty()) {
          Text("Sem treino registrado neste dia.", color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
          Text(
              "${doDia.size} séries · ${doDia.map { it.exercicio }.distinct().joinToString(", ") { nomeBonito(it) }}",
              color = Texto, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
          )
          perf?.let {
            Text(
                "Rendimento ${fmtPct(it.variacao)} em relação às suas últimas sessões",
                color = if (it.variacao >= 0) Verde else Destaque, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
          }
        }
        var hora by remember(dia) { mutableStateOf(d.treinoHora ?: "") }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("Começou às", color = Cinza, fontSize = 13.sp)
          Spacer(Modifier.width(8.dp))
          OutlinedTextField(
              hora,
              { t ->
                hora = t
                val h = horaValida(t)
                if (h != null || t.isBlank()) diario.alterar(dia) { it.copy(treinoHora = h) }
              },
              modifier = Modifier.width(110.dp),
              placeholder = { Text("18:30") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          )
          Spacer(Modifier.weight(1f))
          TextButton(onClick = onAbrirTreino) { Text("Abrir treino ›") }
        }
      }
    }
  }
}

/** "1830", "18:30", "18h30", "7:5" → "18:30" / "07:05". */
fun horaValida(t: String): String? {
  val m = Regex("""^\s*(\d{1,2})\s*[:h.]?\s*(\d{2})?\s*$""").find(t) ?: return null
  val h = m.groupValues[1].toInt()
  val min = m.groupValues[2].ifEmpty { "0" }.toInt()
  if (h > 23 || min > 59) return null
  return "%02d:%02d".format(h, min)
}

@Composable
private fun Etiqueta(texto: String, cor: Color) {
  Text(
      texto, color = cor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
      modifier = Modifier.clip(RoundedCornerShape(50)).background(cor.copy(alpha = 0.14f))
          .padding(horizontal = 8.dp, vertical = 3.dp),
  )
}

/** Escala de 1 a 5 com emojis; tocar de novo no mesmo valor limpa. */
@Composable
private fun Escala(valor: Int?, rotulos: List<String>, legenda: List<String>, descricao: String, onEscolher: (Int?) -> Unit) {
  Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    rotulos.forEachIndexed { i, emoji ->
      val n = i + 1
      val sel = valor == n
      Column(
          Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
              .clickable { onEscolher(if (sel) null else n) }
              .semantics { contentDescription = "$descricao $n" }
              .padding(vertical = 6.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(if (sel) Color(0x33FF5C6C) else Superficie)
                .border(1.dp, if (sel) Destaque else Borda, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 22.sp) }
        Text(legenda[i], color = if (sel) Destaque else Cinza, fontSize = 10.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp))
      }
    }
  }
}

@Composable
private fun CampoTexto(chave: String, valor: String, dica: String, linhas: Int = 1, onMudar: (String) -> Unit) {
  var t by remember(chave) { mutableStateOf(valor) }
  OutlinedTextField(
      t, { t = it; onMudar(it) },
      modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
      placeholder = { Text(dica, fontSize = 13.sp) },
      minLines = linhas, maxLines = 6,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Eventos(marcados: List<String>, onMudar: (List<String>) -> Unit) {
  var outro by remember { mutableStateOf("") }
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    (EVENTOS_COMUNS + marcados.filter { it !in EVENTOS_COMUNS }).forEach { e ->
      val sel = e in marcados
      FilterChip(selected = sel, onClick = { onMudar(if (sel) marcados - e else marcados + e) }, label = { Text(e) })
    }
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
        outro, { outro = it }, modifier = Modifier.weight(1f),
        placeholder = { Text("Outro (ex.: entrevista)", fontSize = 13.sp) }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    TextButton(enabled = outro.isNotBlank(), onClick = {
      val e = outro.trim()
      if (e !in marcados) onMudar(marcados + e)
      outro = ""
    }) { Text("Marcar") }
  }
}

@Composable
private fun RefeicaoLinha(r: Refeicao, treinoHora: String?, onApagar: () -> Unit) {
  Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(r.tipo.emoji, fontSize = 20.sp)
      Spacer(Modifier.width(8.dp))
      Text(r.tipo.titulo, color = Texto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
      Spacer(Modifier.width(8.dp))
      Text(r.hora, color = Cinza, fontSize = 13.sp)
      if (treinoHora != null) {
        Spacer(Modifier.width(8.dp))
        Etiqueta(if (r.hora < treinoHora) "antes do treino" else "depois do treino", if (r.hora < treinoHora) Verde else Cinza)
      }
      Spacer(Modifier.weight(1f))
      if (r.kcal > 0) Text("${r.kcal.toInt()} kcal", color = Destaque, fontSize = 13.sp)
      TextButton(onClick = onApagar) { Text("✕", color = Cinza) }
    }
    r.itens.forEach { a ->
      Row(Modifier.padding(start = 28.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emojiDoAlimento(a.nome), fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(a.nome.replaceFirstChar { it.titlecase() }, color = Texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            listOfNotNull(a.gramas?.let { "${fmtPeso(it)} g" }, a.kcal?.let { "${it.toInt()} kcal" }).joinToString(" · "),
            color = Cinza, fontSize = 13.sp,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovaRefeicao(horaInicial: String, onSalvar: (TipoRefeicao, String, List<Alimento>) -> Unit, onCancelar: () -> Unit) {
  var tipo by remember { mutableStateOf(sugerirTipo(horaInicial)) }
  var hora by remember { mutableStateOf(horaInicial) }
  var texto by remember { mutableStateOf("") }
  val itens = LeitorComida.lerVarios(texto)
  val h = horaValida(hora)
  Column(Modifier.padding(top = 12.dp)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      TipoRefeicao.entries.forEach { t ->
        FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text("${t.emoji} ${t.titulo}") })
      }
    }
    OutlinedTextField(
        hora, { hora = it }, label = { Text("Horário") }, singleLine = true, isError = h == null,
        modifier = Modifier.width(140.dp).padding(top = 6.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    OutlinedTextField(
        texto, { texto = it },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text("O que você comeu (um por linha)") },
        placeholder = { Text("arroz 150g 195kcal\nfrango 120g 190kcal\nbanana 90kcal") },
        minLines = 3, maxLines = 10,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
    )
    if (itens.isNotEmpty()) {
      Text(
          itens.joinToString("  ") { "${emojiDoAlimento(it.nome)} ${it.nome}" } +
              (itens.sumOf { it.kcal ?: 0.0 }.takeIf { it > 0 }?.let { "  ·  ${it.toInt()} kcal" } ?: ""),
          color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
      )
    }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      BotaoDegrade(onClick = { onSalvar(tipo, h!!, itens) }, enabled = itens.isNotEmpty() && h != null) {
        Text("Salvar refeição")
      }
      OutlinedButton(onClick = onCancelar, modifier = Modifier.height(48.dp)) { Text("Cancelar") }
    }
  }
}

private fun sugerirTipo(hora: String): TipoRefeicao {
  val h = hora.take(2).toIntOrNull() ?: 12
  return when (h) {
    in 4..10 -> TipoRefeicao.CAFE
    in 11..14 -> TipoRefeicao.ALMOCO
    in 15..18 -> TipoRefeicao.LANCHE
    in 19..22 -> TipoRefeicao.JANTAR
    else -> TipoRefeicao.CEIA
  }
}
