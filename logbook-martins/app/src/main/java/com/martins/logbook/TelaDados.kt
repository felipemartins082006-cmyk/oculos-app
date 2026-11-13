package com.martins.logbook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** O que foi lido, pronto para conferir antes de importar. */
private class Previa(val texto: String, val leitura: Leitura, val novas: Int, val repetidas: Int, val arquivo: String?)

/**
 * Seletor de arquivos do Android (o mesmo do app Arquivos): abre na pasta Downloads,
 * mas dá para ir em qualquer pasta do celular, cartão de memória, Drive etc.
 * Mostra todos os arquivos: o app tenta ler qualquer texto (.txt, .csv, .json…).
 */
private class EscolherArquivo : ActivityResultContracts.OpenDocument() {
  override fun createIntent(context: Context, input: Array<String>): Intent =
      super.createIntent(context, input).putExtra(
          DocumentsContract.EXTRA_INITIAL_URI,
          DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download"),
      )
}


@Composable
fun TelaDados(lb: Logbook, todas: List<Serie>, recebido: Recebido? = null, onRecebidoLido: () -> Unit = {}) {
  val ctx = LocalContext.current
  val escopo = rememberCoroutineScope()
  var texto by remember { mutableStateOf("") }
  var lendo by remember { mutableStateOf(false) }
  var previa by remember { mutableStateOf<Previa?>(null) }
  var confirmandoApagar by remember { mutableStateOf(false) }
  val desfazivel = lb.desfazivel.value

  /** Lê em segundo plano: um histórico de anos pode ter dezenas de milhares de linhas. */
  fun ler(t: String, ordem: OrdemData? = null, arquivo: String? = null) {
    lendo = true
    escopo.launch {
      val p = withContext(Dispatchers.Default) {
        val r = Leitor.ler(t, LocalDate.now(), ordem = ordem)
        val (novas, repetidas) = lb.separarNovas(r.series)
        Previa(t, r, novas.size, repetidas, arquivo)
      }
      previa = p
      lendo = false
    }
  }

  /** Abre o arquivo (texto ou PDF) fora da tela principal e mostra a prévia. */
  fun lerDeArquivo(nome: String?, abrir: () -> String) {
    lendo = true
    texto = ""
    escopo.launch {
      val t = withContext(Dispatchers.IO) { runCatching(abrir) }
      t.onSuccess { ler(it, arquivo = nome) }.onFailure {
        lendo = false
        Toast.makeText(ctx, "Não consegui abrir o arquivo${nome?.let { n -> " $n" } ?: ""}", Toast.LENGTH_LONG).show()
      }
    }
  }

  // Chegou um arquivo pelo "Compartilhar": já mostra a prévia.
  LaunchedEffect(recebido?.id) {
    if (recebido != null) {
      texto = ""
      lerDeArquivo(recebido.nome ?: "texto compartilhado") { recebido.ler(ctx) }
      onRecebidoLido()
    }
  }

  val abrirArquivo = rememberLauncherForActivityResult(EscolherArquivo()) { uri ->
    if (uri != null) {
      lerDeArquivo(nomeDoArquivo(ctx, uri)) { lerArquivo(ctx, uri) }
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
      Text("Importar", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Texto)
      val dias = todas.map { it.data }.distinct()
      Text(
          if (todas.isEmpty()) "Nenhuma série salva ainda."
          else "${todas.size} séries · ${dias.size} dias · ${todas.map { it.exercicio }.distinct().size} exercícios · " +
              "desde ${dataCurta(dias.min())}",
          color = Cinza, fontSize = 14.sp,
      )
    }

    if (desfazivel > 0) item {
      Cartao {
        Text("Última importação: $desfazivel séries", color = Texto, fontWeight = FontWeight.SemiBold)
        Text("Algo veio errado? Desfaça e importe de novo.", color = Cinza, fontSize = 13.sp)
        OutlinedButton(onClick = {
          val n = lb.desfazerImportacao()
          Toast.makeText(ctx, "$n séries removidas", Toast.LENGTH_LONG).show()
        }, modifier = Modifier.padding(top = 8.dp)) { Text("Desfazer importação", color = Vermelho) }
      }
    }

    val p = previa
    if (p == null) {
      item {
        Cartao {
          Text("Importar registros", color = Texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Text(
              "Traga treinos que você já anotou em outro lugar: um arquivo do celular (PDF, .txt, " +
                  ".csv), uma exportação do Discord ou um backup deste app.",
              color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
          )
          Surface(color = Superficie2, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
              Text("Jeito mais fácil", color = Laranja, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
              Text(
                  "No app de arquivos (ou no Discord, WhatsApp, e-mail…), segure o arquivo, toque em " +
                      "Compartilhar e escolha Logbook. O app abre aqui já com a prévia.",
                  color = Texto, fontSize = 13.sp,
              )
            }
          }
          Button(
              onClick = { abrirArquivo.launch(arrayOf("*/*")) },
              enabled = !lendo,
              modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
          ) { Text("📁  Escolher arquivo do celular") }

          Text("ou cole o texto aqui:", color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
          OutlinedTextField(
              texto, { texto = it },
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text("21/7/25\nbench press -> 94kg x 4 reps\npulldown -> 38.75kg x 9 reps") },
              minLines = 5, maxLines = 12,
              textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
          )
          Row(
              Modifier.padding(top = 10.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedButton(onClick = { ler(texto) }, enabled = texto.isNotBlank() && !lendo) { Text("Ler texto colado") }
            if (lendo) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
          }
          Text(
              "Uma série por linha (“exercício -> 100kg x 8 reps”). Uma linha com data, como 21/7/25, " +
                  "vale para as séries de baixo. Antes de importar aparece uma prévia, e o que já está " +
                  "no app não entra repetido.",
              color = Cinza, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp),
          )
        }
      }
    } else {
      val r = p.leitura
      val dias = r.series.groupBy { it.data }.toSortedMap()
      item {
        Cartao {
          Text("Prévia", color = Texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
          if (r.series.isEmpty()) {
            Text("Não encontrei nenhuma série. Confira se as linhas estão como “exercício -> 100kg x 8 reps”.",
                color = Laranja, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
          } else {
            Text(
                "${r.series.size} séries em ${dias.size} dias de treino\n" +
                    "de ${dataComSemana(dias.firstKey())} a ${dataComSemana(dias.lastKey())}",
                color = Texto, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "${p.novas} novas" + if (p.repetidas > 0) " · ${p.repetidas} já estão no app (não entram de novo)" else "",
                color = Verde, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
            )
          }
          p.arquivo?.let {
            Text("Arquivo: $it", color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
          }
          if (r.formato == "texto") {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("Datas lidas como ${r.ordem.titulo}", color = Cinza, fontSize = 13.sp, modifier = Modifier.weight(1f))
              TextButton(onClick = {
                ler(p.texto, if (r.ordem == OrdemData.DIA_MES) OrdemData.MES_DIA else OrdemData.DIA_MES)
              }) { Text("Trocar") }
            }
          } else {
            Text("Formato: ${r.formato}", color = Cinza, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
          }
          if (r.naoEntendidas.isNotEmpty()) {
            Text("${r.naoEntendidas.size} linhas ignoradas (não são séries):", color = Laranja, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp))
            r.naoEntendidas.take(10).forEach {
              Text("• ${it.take(80)}", color = Cinza, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            if (r.naoEntendidas.size > 10) Text("… e mais ${r.naoEntendidas.size - 10}", color = Cinza, fontSize = 12.sp)
          }
          Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = p.novas > 0 && !lendo, onClick = {
              val res = lb.importar(r.series)
              Toast.makeText(ctx, "${res.adicionadas} séries importadas em ${res.dias} dias", Toast.LENGTH_LONG).show()
              previa = null
              texto = ""
            }) { Text(if (p.novas > 0) "Importar ${p.novas}" else "Nada novo") }
            OutlinedButton(onClick = { previa = null }) { Text("Voltar") }
          }
        }
      }
      if (dias.isNotEmpty()) {
        item { Text("Dias encontrados (do mais novo)", color = Texto, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        items(dias.entries.reversed().take(60), key = { it.key.toEpochDay() }) { (d, ss) ->
          Cartao {
            Row {
              Text(dataComSemana(d), color = Texto, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
              Text("${ss.size} séries", color = Cinza, fontSize = 13.sp)
            }
            Text(ss.map { it.exercicio }.distinct().joinToString(" · ") { nomeBonito(it) }, color = Cinza, fontSize = 13.sp)
          }
        }
        if (dias.size > 60) item { Text("… e mais ${dias.size - 60} dias", color = Cinza, fontSize = 13.sp) }
      }
    }

    item {
      Cartao {
        Text("Backup", color = Texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "Salva tudo num arquivo .txt no celular (você escolhe a pasta). Para restaurar " +
                "(ex.: celular novo), use “Escolher arquivo do celular” acima com esse arquivo.",
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
