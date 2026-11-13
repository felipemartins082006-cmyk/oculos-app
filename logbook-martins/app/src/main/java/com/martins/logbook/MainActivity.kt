package com.martins.logbook

import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val logbook = Logbook(applicationContext)
    logbook.carregarInicialSePreciso()
    setContent { Tema { App(logbook) } }
  }
}

private enum class Aba(val titulo: String, val icone: String) {
  TREINO("Treino", "＋"),
  HISTORICO("Histórico", "☰"),
  EVOLUCAO("Evolução", "↗"),
  DADOS("Dados", "⇅"),
}

@Composable
private fun App(lb: Logbook) {
  var aba by rememberSaveable { mutableStateOf(Aba.TREINO) }
  var dia by rememberSaveable { mutableStateOf(LocalDate.now()) }
  var exercicio by rememberSaveable { mutableStateOf<String?>(null) }
  val series = lb.series.value

  BackHandler(enabled = exercicio != null || aba != Aba.TREINO) {
    if (aba == Aba.EVOLUCAO && exercicio != null) exercicio = null else aba = Aba.TREINO
  }

  val abrirDia: (LocalDate) -> Unit = { dia = it; aba = Aba.TREINO }
  val abrirExercicio: (String) -> Unit = { exercicio = it; aba = Aba.EVOLUCAO }

  Scaffold(
      containerColor = Fundo,
      bottomBar = {
        NavigationBar(containerColor = Superficie) {
          Aba.entries.forEach { a ->
            NavigationBarItem(
                selected = aba == a,
                onClick = {
                  if (a == Aba.EVOLUCAO && aba == Aba.EVOLUCAO) exercicio = null
                  aba = a
                },
                icon = { Text(a.icone, fontSize = 20.sp) },
                label = { Text(a.titulo) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Laranja,
                    selectedTextColor = Laranja,
                    indicatorColor = Superficie2,
                    unselectedIconColor = Cinza,
                    unselectedTextColor = Cinza,
                ),
            )
          }
        }
      },
  ) { pad ->
    Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()) {
      when (aba) {
        Aba.TREINO -> TelaTreino(lb, series, dia, onDia = { dia = it }, onAbrirExercicio = abrirExercicio)
        Aba.HISTORICO -> TelaHistorico(series, onAbrirDia = abrirDia)
        Aba.EVOLUCAO -> {
          val ex = exercicio
          if (ex == null || series.none { it.exercicio == ex }) {
            TelaExercicios(series, onAbrir = { exercicio = it })
          } else {
            TelaExercicio(
                lb, series, ex,
                onVoltar = { exercicio = null },
                onRenomeado = { exercicio = it },
                onAbrirDia = abrirDia,
            )
          }
        }
        Aba.DADOS -> TelaDados(lb, series)
      }
    }
  }
}
