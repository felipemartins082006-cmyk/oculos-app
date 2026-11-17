package com.martins.logbook

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
  /** Arquivo ou texto que chegou pelo "Compartilhar" de outro app, esperando a prévia. */
  private val recebido = mutableStateOf<Recebido?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val logbook = Logbook(applicationContext)
    val figuras = Figuras(applicationContext)
    val diario = Diario(applicationContext)
    // Backup automático (uma vez por dia) em Downloads/Logbook, fora da tela principal.
    Thread { runCatching { Backup.automatico(applicationContext, logbook, diario, figuras) } }.start()
    if (savedInstanceState == null) {
      receber(intent)
      // Abrindo o app do zero: um temporizador que já tinha terminado não fica mais na tela.
      if (Temporizador.fimEm == null && (Temporizador.terminou || Temporizador.restantePausado == 0L)) Temporizador.zerar()
    }
    setContent {
      Tema {
        CompositionLocalProvider(LocalFiguras provides figuras) {
          App(logbook, diario, recebido.value, onRecebidoLido = { recebido.value = null })
        }
      }
    }
  }

  // Com o app já aberto, um novo "Compartilhar" chega aqui.
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    receber(intent)
  }

  private fun receber(intent: Intent?) {
    lerRecebido(this, intent)
        ?.onSuccess { recebido.value = it }
        ?.onFailure { Toast.makeText(this, "Não consegui ler o que foi compartilhado", Toast.LENGTH_LONG).show() }
  }
}

private enum class Aba(val titulo: String, val desenho: Desenho) {
  TREINO("Treino", Desenho.HALTER),
  DIA("Dia", Desenho.SOL),
  HISTORICO("Histórico", Desenho.CALENDARIO),
  EVOLUCAO("Evolução", Desenho.GRAFICO),
  DADOS("Dados", Desenho.NUVEM),
}

@Composable
private fun App(lb: Logbook, diario: Diario, recebido: Recebido?, onRecebidoLido: () -> Unit) {
  // Aberto pelo "Compartilhar": já começa na aba Importar.
  var aba by rememberSaveable { mutableStateOf(if (recebido != null) Aba.DADOS else Aba.TREINO) }
  var dia by rememberSaveable { mutableStateOf(LocalDate.now()) }
  var exercicio by rememberSaveable { mutableStateOf<String?>(null) }
  val series = lb.series.value
  val dias = diario.dias.value
  VigiaTemporizador()

  // Chegou algo pelo "Compartilhar": vai direto para a aba Importar.
  LaunchedEffect(recebido?.id) { if (recebido != null && aba != Aba.DADOS) aba = Aba.DADOS }

  BackHandler(enabled = exercicio != null || aba != Aba.TREINO) {
    if (aba == Aba.EVOLUCAO && exercicio != null) exercicio = null else aba = Aba.TREINO
  }

  val abrirDia: (LocalDate) -> Unit = { dia = it; aba = Aba.TREINO }
  val abrirExercicio: (String) -> Unit = { exercicio = it; aba = Aba.EVOLUCAO }

  Scaffold(
      modifier = Modifier.fundoDoApp(),
      containerColor = Color.Transparent,
      bottomBar = {
        BarraFlutuante(Aba.entries.map { ItemNav(it.titulo, it.desenho) }, aba.ordinal) { i ->
          val a = Aba.entries[i]
          if (a == Aba.EVOLUCAO && aba == Aba.EVOLUCAO) exercicio = null
          aba = a
        }
      },
  ) { pad ->
    Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()) {
      when (aba) {
        Aba.TREINO -> TelaTreino(
            lb, diario, series, dia,
            onDia = { dia = it },
            onAbrirExercicio = abrirExercicio,
            onImportar = { aba = Aba.DADOS },
        )
        Aba.DIA -> TelaDia(diario, series, dia, onDia = { dia = it }, onAbrirTreino = { aba = Aba.TREINO })
        Aba.HISTORICO -> TelaHistorico(
            series, dias, onAbrirDia = abrirDia,
            onApagarDia = { d -> lb.apagarDia(d); diario.zerarSessao(d) },
        )
        Aba.EVOLUCAO -> {
          val ex = exercicio
          if (ex == null || series.none { it.exercicio == ex }) {
            TelaExercicios(series, dias, onAbrir = { exercicio = it }, onAbrirDia = abrirDia)
          } else {
            TelaExercicio(
                lb, series, ex,
                onVoltar = { exercicio = null },
                onRenomeado = { exercicio = it },
                onAbrirDia = abrirDia,
            )
          }
        }
        Aba.DADOS -> TelaDados(lb, diario, series, recebido, onRecebidoLido)
      }
    }
  }
}
