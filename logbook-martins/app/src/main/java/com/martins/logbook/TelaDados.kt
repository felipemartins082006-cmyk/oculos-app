package com.martins.logbook

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun TelaDados(lb: Logbook, todas: List<Serie>) {
  val ctx = LocalContext.current
  var texto by remember { mutableStateOf("") }
  var leitura by remember { mutableStateOf<Leitura?>(null) }
  var confirmandoApagar by remember { mutableStateOf(false) }

  fun ler(t: String) {
    leitura = Leitor.ler(t, LocalDate.now())
  }

  val abrirArquivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      runCatching { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
          .onSuccess { ler(it) }
          .onFailure { Toast.makeText(ctx, "Não consegui abrir o arquivo", Toast.LENGTH_LONG).show() }
    }
  }
  val salvarArquivo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
    if (uri != null) {
      runCatching {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(Leitor.escrever(todas)) }
      }.onSuccess { Toast.makeText(ctx, "Backup salvo (${todas.size} séries)", Toast.LENGTH_LONG).show() }
          .onFailure { Toast.makeText(ctx, "Não consegui salvar o backup", Toast.LENGTH_LONG).show() }
    }
  }

  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text("Dados", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Texto)
      val dias = todas.map { it.data }.distinct()
      Text(
          if (todas.isEmpty()) "Nenhuma série salva."
          else "${todas.size} séries · ${dias.size} dias · ${todas.map { it.exercicio }.distinct().size} exercícios · " +
              "desde ${dataCurta(dias.min())}",
          color = Cinza, fontSize = 14.sp,
      )
    }

    item {
      Cartao {
        Text("Importar do Discord", color = Texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "Cole as mensagens do canal (com as linhas de data, ex.: “milkinaa — 21/7/25, 7:21”) " +
                "ou escolha um arquivo .txt. O que já estiver no app não entra repetido.",
            color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp),
        )
        OutlinedTextField(
            texto, { texto = it; leitura = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("milkinaa — 21/7/25, 7:21\nbench press -> 94kg x 4 reps") },
            minLines = 4, maxLines = 10,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = { ler(texto) }, enabled = texto.isNotBlank()) { Text("Ler texto") }
          OutlinedButton(onClick = { abrirArquivo.launch(arrayOf("text/*", "application/octet-stream")) }) {
            Text("Escolher arquivo")
          }
        }

        leitura?.let { r ->
          val dias = r.series.map { it.data }.distinct().sorted()
          Text(
              if (r.series.isEmpty()) "Não encontrei nenhuma série no texto."
              else "Encontrei ${r.series.size} séries em ${dias.size} dias " +
                  "(${dataCurta(dias.first())} a ${dataCurta(dias.last())}).",
              color = Texto, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp),
          )
          if (r.naoEntendidas.isNotEmpty()) {
            Text("${r.naoEntendidas.size} linhas não entendi (ficam de fora):", color = Laranja, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp))
            r.naoEntendidas.take(15).forEach {
              Text("• $it", color = Cinza, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            if (r.naoEntendidas.size > 15) Text("… e mais ${r.naoEntendidas.size - 15}", color = Cinza, fontSize = 12.sp)
          }
          if (r.series.isNotEmpty()) {
            Button(onClick = {
              val res = lb.importar(r.series)
              Toast.makeText(
                  ctx,
                  "${res.adicionadas} séries novas" + if (res.repetidas > 0) " · ${res.repetidas} já estavam no app" else "",
                  Toast.LENGTH_LONG,
              ).show()
              leitura = null
              texto = ""
            }, modifier = Modifier.padding(top = 10.dp)) { Text("Importar ${r.series.size} séries") }
          }
        }
      }
    }

    item {
      Cartao {
        Text("Backup", color = Texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "Salva tudo num arquivo .txt no mesmo formato do Discord. Para restaurar (ex.: celular novo), " +
                "use “Escolher arquivo” acima com esse arquivo.",
            color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp),
        )
        Button(onClick = { salvarArquivo.launch("logbook-${LocalDate.now()}.txt") }, enabled = todas.isNotEmpty()) {
          Text("Salvar backup")
        }
      }
    }

    item {
      Cartao {
        Text("Apagar tudo", color = Texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Remove todas as séries deste celular. Faça um backup antes.", color = Cinza, fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 6.dp))
        OutlinedButton(onClick = { confirmandoApagar = true }, enabled = todas.isNotEmpty()) {
          Text("Apagar todas as séries", color = Vermelho)
        }
      }
    }
  }

  if (confirmandoApagar) {
    AlertDialog(
        onDismissRequest = { confirmandoApagar = false },
        title = { Text("Apagar tudo?") },
        text = { Text("As ${todas.size} séries serão apagadas deste celular. Isso não dá para desfazer.") },
        confirmButton = {
          TextButton(onClick = { lb.apagarTudo(); confirmandoApagar = false }) { Text("Apagar", color = Vermelho) }
        },
        dismissButton = { TextButton(onClick = { confirmandoApagar = false }) { Text("Cancelar") } },
    )
  }
}
