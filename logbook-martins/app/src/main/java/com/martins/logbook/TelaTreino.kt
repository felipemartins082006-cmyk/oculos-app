package com.martins.logbook

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun Cartao(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
  Surface(
      color = Superficie,
      shape = RoundedCornerShape(14.dp),
      modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
  ) { Column(Modifier.padding(14.dp), content = content) }
}

/** Nome digitado antes do primeiro número: "bench pr" em "bench pr 90". */
private fun nomeDigitado(texto: String): String =
    normalizarNome(texto.takeWhile { !it.isDigit() }.replace("->", ""))

@Composable
fun TelaTreino(
    lb: Logbook,
    todas: List<Serie>,
    dia: LocalDate,
    onDia: (LocalDate) -> Unit,
    onAbrirExercicio: (String) -> Unit,
    onImportar: () -> Unit,
) {
  val ctx = LocalContext.current
  var campo by remember { mutableStateOf(TextFieldValue("")) }
  var erro by remember { mutableStateOf<String?>(null) }
  var editando by remember { mutableStateOf<Serie?>(null) }
  var escolhendoData by remember { mutableStateOf(false) }
  var colando by remember { mutableStateOf(false) }
  val hoje = LocalDate.now()

  val doDia = todas.filter { it.data == dia }.sortedBy { it.id }
  val porExercicio = doDia.groupBy { it.exercicio }
  val nomes = remember(todas) { resumos(todas).map { it.exercicio } }
  val digitado = nomeDigitado(campo.text)

  fun colar(texto: String): Leitura {
    val r = Leitor.ler(texto, dia)
    if (r.series.isNotEmpty()) {
      val antes = todas
      val novas = lb.adicionar(r.series)
      val dias = novas.map { it.data }.distinct()
      Toast.makeText(
          ctx,
          "${novas.size} séries adicionadas" + if (dias.size == 1) " em ${dataCurta(dias[0])}" else " em ${dias.size} dias",
          Toast.LENGTH_LONG,
      ).show()
      novas.firstNotNullOfOrNull { recordeBatido(antes.filter { s -> s.data <= it.data }, it) }
          ?.let { Toast.makeText(ctx, "🏆 $it", Toast.LENGTH_LONG).show() }
    }
    return r
  }

  fun adicionar(texto: String) {
    val r = Leitor.ler(texto, dia)
    if (r.series.isEmpty()) {
      erro = "Não entendi. Escreva assim: squat 160kg x 4"
      return
    }
    val antes = todas
    val novas = lb.adicionar(r.series)
    erro = null
    campo = TextFieldValue("")
    novas.firstNotNullOfOrNull { recordeBatido(antes.filter { s -> s.data <= dia }, it) }
        ?.let { Toast.makeText(ctx, "🏆 $it", Toast.LENGTH_LONG).show() }
  }

  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onDia(dia.minusDays(1)) }) { Text("‹", fontSize = 28.sp, color = Cinza) }
        Column(
            Modifier.weight(1f).clickable { escolhendoData = true },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
              if (dia == LocalDate.now()) "Hoje · ${diaDaSemana(dia)}" else diaDaSemana(dia),
              fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Texto,
          )
          Text(dataLonga(dia), color = Cinza, fontSize = 14.sp)
        }
        TextButton(onClick = { onDia(dia.plusDays(1)) }, enabled = dia < LocalDate.now()) {
          Text("›", fontSize = 28.sp, color = if (dia < LocalDate.now()) Cinza else Superficie2)
        }
      }
      Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { escolhendoData = true }, modifier = Modifier.weight(1f)) { Text("📅 Outro dia") }
        OutlinedButton(onClick = { colando = true }, modifier = Modifier.weight(1f)) { Text("📋 Colar treino") }
      }
      if (dia != hoje) {
        Surface(
            color = Color(0xFF3A2414), shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
          Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Registrando em ${dataComSemana(dia)}", color = Laranja, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onDia(hoje) }) { Text("Ir para hoje") }
          }
        }
      }
    }

    if (colando) item { ColarTreino(dia, onFechar = { colando = false }, onAdicionar = ::colar) }

    if (todas.isEmpty() && !colando) item { BoasVindas(onImportar) }

    if (!colando) item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
              value = campo,
              onValueChange = { campo = it; erro = null },
              modifier = Modifier.weight(1f),
              placeholder = { Text("squat 160kg x 4") },
              singleLine = true,
              isError = erro != null,
              keyboardOptions = KeyboardOptions(
                  capitalization = KeyboardCapitalization.None,
                  autoCorrectEnabled = false,
                  keyboardType = KeyboardType.Text,
                  imeAction = ImeAction.Done,
              ),
              keyboardActions = KeyboardActions(onDone = { adicionar(campo.text) }),
          )
          Spacer(Modifier.width(8.dp))
          Button(onClick = { adicionar(campo.text) }, modifier = Modifier.height(56.dp)) {
            Text("+", fontSize = 22.sp)
          }
        }
        erro?.let { Text(it, color = Vermelho, fontSize = 13.sp) }

        // Sugestões de nome: os seus exercícios, do mais recente para o mais antigo.
        val temNumero = campo.text.any { it.isDigit() }
        val sugestoes = when {
          temNumero -> emptyList()
          digitado.isEmpty() -> nomes.take(10)
          else -> (nomes.filter { it.startsWith(digitado) } + nomes.filter { digitado in it })
              .distinct().filter { it != digitado }.take(10)
        }
        if (sugestoes.isNotEmpty()) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sugestoes) { n ->
              SuggestionChip(
                  onClick = { val t = "$n "; campo = TextFieldValue(t, TextRange(t.length)) },
                  label = { Text(n) },
              )
            }
          }
        }

        // Dica: o que você fez da última vez nesse exercício.
        if (digitado in nomes) UltimaVez(todas, digitado, dia)
      }
    }

    if (porExercicio.isEmpty() && todas.isNotEmpty()) {
      item {
        Text(
            if (dia == hoje) "Nada registrado hoje ainda.\nDigite uma série por vez:\nsquat 160kg x 4"
            else "Nada registrado em ${dataCurta(dia)}.\nDigite as séries que você lembra,\nou use “Colar treino” para lançar tudo de uma vez.",
            color = Cinza, modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp,
        )
      }
    }

    items(porExercicio.entries.toList(), key = { it.key }) { (ex, ss) ->
      Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
              nomeBonito(ex), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Texto,
              modifier = Modifier.weight(1f).clickable { onAbrirExercicio(ex) },
              maxLines = 1, overflow = TextOverflow.Ellipsis,
          )
          TextButton(onClick = {
            val u = ss.last()
            adicionar("${u.exercicio} -> ${if (u.peso == 0.0) "bw" else fmtPeso(u.peso)} x ${u.reps}" +
                if (u.nota.isNotBlank()) " (${u.nota})" else "")
          }) { Text("+ repetir") }
        }
        ss.forEachIndexed { i, s ->
          Row(
              Modifier.fillMaxWidth().clickable { editando = s }.padding(vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("${i + 1}", color = Cinza, modifier = Modifier.width(26.dp), fontFamily = FontFamily.Monospace)
            Text(fmtSerie(s), color = Texto, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (s.nota.isNotBlank()) Text(s.nota, color = Cinza, fontSize = 13.sp)
          }
        }
      }
    }
  }

  editando?.let { s ->
    EditarSerie(
        s,
        onSalvar = { lb.atualizar(it); editando = null },
        onApagar = { lb.apagar(s.id); editando = null },
        onFechar = { editando = null },
    )
  }

  if (escolhendoData) EscolherData(dia, onEscolher = { onDia(it); escolhendoData = false }, onFechar = { escolhendoData = false })
}

@Composable
private fun UltimaVez(todas: List<Serie>, exercicio: String, dia: LocalDate) {
  val hist = historicoDe(todas, exercicio)
  val anterior = hist.lastOrNull { it.data < dia } ?: hist.lastOrNull { it.data != dia } ?: return
  val recorde = todas.filter { it.exercicio == exercicio }.maxWith(compareBy<Serie>({ it.peso }, { it.reps }))
  Surface(color = Superficie2, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(10.dp)) {
      Text("Última vez · ${dataComSemana(anterior.data)}", color = Cinza, fontSize = 12.sp)
      Text(resumoSeries(anterior.series), color = Texto, fontSize = 14.sp)
      Text("Recorde: ${fmtSerie(recorde)} (${dataCurta(recorde.data)})", color = Laranja, fontSize = 12.sp)
    }
  }
}

@Composable
private fun EditarSerie(s: Serie, onSalvar: (Serie) -> Unit, onApagar: () -> Unit, onFechar: () -> Unit) {
  var nome by remember { mutableStateOf(s.exercicio) }
  var peso by remember { mutableStateOf(if (s.peso == 0.0) "0" else fmtPeso(s.peso)) }
  var reps by remember { mutableStateOf(s.reps.toString()) }
  var nota by remember { mutableStateOf(s.nota) }
  val p = peso.replace(',', '.').toDoubleOrNull()
  val r = reps.toIntOrNull()
  val ok = p != null && p >= 0 && r != null && r > 0 && normalizarNome(nome).isNotEmpty()

  AlertDialog(
      onDismissRequest = onFechar,
      title = { Text("Editar série") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(nome, { nome = it }, label = { Text("Exercício") }, singleLine = true)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(peso, { peso = it }, label = { Text("Carga (kg)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(reps, { reps = it }, label = { Text("Reps") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
          }
          OutlinedTextField(nota, { nota = it }, label = { Text("Nota (RIR, cluster…)") }, singleLine = true)
          TextButton(onClick = onApagar) { Text("Apagar esta série", color = Vermelho) }
        }
      },
      confirmButton = {
        TextButton(enabled = ok, onClick = {
          onSalvar(s.copy(exercicio = normalizarNome(nome), peso = p!!, reps = r!!, nota = nota.trim()))
        }) { Text("Salvar") }
      },
      dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EscolherData(dia: LocalDate, onEscolher: (LocalDate) -> Unit, onFechar: () -> Unit) {
  val limite = LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
  val estado = rememberDatePickerState(
      initialSelectedDateMillis = dia.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
      selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis < limite
      },
  )
  DatePickerDialog(
      onDismissRequest = onFechar,
      confirmButton = {
        TextButton(onClick = {
          estado.selectedDateMillis?.let { onEscolher(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
              ?: onFechar()
        }) { Text("OK") }
      },
      dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
  ) { DatePicker(state = estado) }
}

@Composable
private fun BoasVindas(onImportar: () -> Unit) {
  Cartao {
    Text("Seu logbook está vazio", color = Texto, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    Passo("Treinando agora?", "Digite cada série no campo abaixo, do jeito que você já escreve: squat 160kg x 4")
    Passo("Esqueceu de registrar um dia?", "Toque em “Outro dia”, escolha a data e lance as séries, uma por uma ou todas com “Colar treino”.")
    Passo("Já tem tudo anotado no Discord?", "Traga o histórico inteiro de uma vez:")
    Button(onClick = onImportar, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
      Text("Importar do Discord")
    }
  }
}

@Composable
private fun Passo(titulo: String, texto: String) {
  Text(titulo, color = Laranja, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
  Text(texto, color = Cinza, fontSize = 13.sp)
}

/** Lançar um treino inteiro de uma vez num dia (útil quando você esqueceu de registrar). */
@Composable
private fun ColarTreino(dia: LocalDate, onFechar: () -> Unit, onAdicionar: (String) -> Leitura) {
  var texto by remember { mutableStateOf("") }
  var aviso by remember { mutableStateOf<String?>(null) }
  Cartao {
    Text("Treino de ${dataComSemana(dia)}", color = Texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Text("Uma série por linha:", color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
    OutlinedTextField(
        texto, { texto = it; aviso = null },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("squat 160kg x 4\nsquat 150kg x 6\nleg extension 80kg x 10, 10, 8") },
        minLines = 6, maxLines = 14,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
    )
    aviso?.let { Text(it, color = Laranja, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(enabled = texto.isNotBlank(), onClick = {
        val r = onAdicionar(texto)
        if (r.naoEntendidas.isEmpty() && r.series.isNotEmpty()) onFechar()
        else {
          // Deixa no campo só o que não deu para entender, para você corrigir.
          texto = r.naoEntendidas.joinToString("\n")
          aviso = (if (r.series.isNotEmpty()) "${r.series.size} séries adicionadas. " else "") +
              "Não entendi ${if (r.naoEntendidas.size == 1) "esta linha" else "estas linhas"}: corrija e toque em Adicionar."
        }
      }) { Text("Adicionar") }
      OutlinedButton(onClick = onFechar) { Text("Fechar") }
    }
  }
}
