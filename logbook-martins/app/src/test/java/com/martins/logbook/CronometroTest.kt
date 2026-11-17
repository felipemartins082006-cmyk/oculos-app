package com.martins.logbook

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CronometroTest {
  @get:Rule val regra = createEmptyComposeRule()
  private val ctx = ApplicationProvider.getApplicationContext<Context>()
  private val hoje = LocalDate.now()

  @Before fun limpar() = Temporizador.zerar()
  @org.junit.After fun fechar() { Temporizador.zerar(); Temporizador.fechar(); Temporizador.aoMudar = null }

  private fun foto(cena: ActivityScenario<MainActivity>, nome: String) {
    regra.waitForIdle()
    cena.onActivity { a ->
      val v = a.window.decorView
      val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
      v.draw(Canvas(bmp))
      File(File("build/telas").apply { mkdirs() }, "$nome.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
  }

  private fun abrir() = ActivityScenario.launch<MainActivity>(Intent(ctx, MainActivity::class.java))
  private fun rolar(m: SemanticsMatcher) = regra.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(m)

  @Test fun cronometroDaSessaoEDescanso() {
    abrir().use { cena ->
      regra.onNodeWithText("Agora não").performClick()
      regra.onNodeWithText("⏱ Iniciar treino").performClick()
      regra.waitForIdle()
      regra.onNodeWithText("TREINO EM ANDAMENTO", substring = true).assertExists()
      val campo = regra.onAllNodes(hasSetTextAction()).onFirst()
      campo.performTextInput("squat 160kg x 4")
      campo.performImeAction()
      regra.waitForIdle()
      regra.onNodeWithText("Descansando", substring = true).assertExists()
      foto(cena, "c1-treino-rodando")
      regra.onNodeWithText("✓ Concluir treino").performClick()
      regra.waitForIdle()
      regra.onNodeWithText("TREINO CONCLUÍDO").assertExists()
    }
    val d = Diario(ctx).de(hoje)
    assertNotNull(d.treinoInicio)
    assertNotNull(d.treinoFim)
    assertNotNull(Logbook(ctx).series.value.single().momento)
  }

  @Test fun primeiraSerieLigaOCronometro() {
    abrir().use {
      regra.onNodeWithText("Agora não").performClick()
      val campo = regra.onAllNodes(hasSetTextAction()).onFirst()
      campo.performTextInput("bench press 90kg x 5")
      campo.performImeAction()
      regra.waitForIdle()
      regra.onNodeWithText("TREINO EM ANDAMENTO", substring = true).assertExists()
    }
  }

  @Test fun temporizador() {
    abrir().use { cena ->
      regra.onNodeWithText("Agora não").performClick()
      regra.onNodeWithText("⏲ Timer").performClick()
      regra.onNodeWithText("TEMPORIZADOR DE DESCANSO").assertExists()
      foto(cena, "c2-timer-escolher")
      regra.onAllNodesWithText("1:30").onLast().performClick() // o atalho (o visor também mostra 1:30)
      regra.waitForIdle()
      regra.onNodeWithText("⏸ Pausar").assertExists()
      foto(cena, "c3-timer-rodando")
      regra.onNodeWithText("⏸ Pausar").performClick()
      regra.onNodeWithText("pausado").assertExists()
      regra.onNodeWithText("+15s").performClick()
      assertEquals(105_000L, Temporizador.duracaoMs)
      regra.onNodeWithText("Zerar").performClick()
      regra.waitForIdle()

      // Escolher 0:05 com os botões − / + e iniciar; fechar o pop-up: a bolha continua mostrando o tempo.
      regra.onNode(hasContentDescription("Minutos menos")).performClick()
      repeat(8) { regra.onNode(hasContentDescription("Segundos menos")).performClick() }
      assertEquals(5_000L, Temporizador.duracaoMs)
      regra.onNodeWithText("▶  Iniciar 0:05").performClick()
      regra.onNodeWithText("Fechar").performClick()
      regra.waitForIdle()
      regra.onNode(hasContentDescription("Abrir temporizador")).assertExists()
      foto(cena, "c3b-bolha")
      // Troca de aba: continua contando.
      regra.onNodeWithText("Histórico").performClick()
      regra.onNode(hasContentDescription("Abrir temporizador")).assertExists()
      regra.mainClock.advanceTimeBy(6_000)
      regra.waitUntil(10_000) { Temporizador.terminou }
      // No fim, o pop-up abre sozinho com "Tempo!".
      regra.onAllNodesWithText("Tempo!").onFirst().assertExists()
      foto(cena, "c4-timer-acabou")
    }
  }

  @Test fun temporizadorNaoZeraSeOAppFechar() {
    SistemaTemporizador.configurar(ctx)
    Temporizador.iniciar(120_000, agora = System.currentTimeMillis())
    val fim = Temporizador.fimEm
    // O Android fecha o app: a memória some…
    Temporizador.fimEm = null; Temporizador.duracaoMs = 1; Temporizador.restantePausado = null
    // …e ao abrir de novo, o temporizador volta de onde estava.
    SistemaTemporizador.carregar(ctx)
    assertEquals(fim, Temporizador.fimEm)
    assertEquals(120_000L, Temporizador.duracaoMs)
    // Se terminou enquanto o app estava fechado, aparece como "acabou".
    SistemaTemporizador.carregar(ctx, agora = fim!! + 1)
    assertTrue(Temporizador.terminou)
    // O alarme do sistema foi agendado para o fim.
    val am = ctx.getSystemService(android.app.AlarmManager::class.java)
    assertNotNull(org.robolectric.Shadows.shadowOf(am).nextScheduledAlarm)
    Temporizador.zerar()
  }

  @Test fun telaDeNumeros() {
    val lb = Logbook(ctx)
    val diario = Diario(ctx)
    val exs = listOf("squat", "bench press", "pull ups", "barbell row", "shoulder smith press", "seated bicep curl",
        "tricep pushdown", "leg extension", "abs club machine")
    val linhas = mutableListOf<SerieLida>()
    for (i in 0 until 40) {
      val d = hoje.minusDays(300L - i * 7 - (i % 3))
      exs.filterIndexed { j, _ -> (i + j) % 3 != 0 }.forEachIndexed { j, ex ->
        repeat(if (j < 2) 4 else 3) { linhas += SerieLida(d, ex, 60.0 + j * 10 + i * (1.0 + j % 3), 6 + (i % 4), "") }
      }
      if (i % 2 == 0) diario.alterar(d) { it.copy(treinoInicio = 1_000_000L, treinoFim = 1_000_000L + (60 + i % 30) * 60_000L) }
    }
    lb.importar(linhas)
    // Um treino de hoje com tempo de descanso marcado.
    val t0 = System.currentTimeMillis() - 30 * 60_000
    lb.adicionar(listOf(SerieLida(hoje, "squat", 140.0, 5, "")), momento = t0)
    lb.adicionar(listOf(SerieLida(hoje, "squat", 140.0, 5, "")), momento = t0 + 150_000)
    lb.adicionar(listOf(SerieLida(hoje, "squat", 140.0, 5, "")), momento = t0 + 290_000)

    abrir().use { cena ->
      regra.onNodeWithText("Evolução").performClick()
      regra.onNodeWithText("Números").performClick()
      regra.waitForIdle()
      regra.onNodeWithText("TREINOS").assertExists()
      foto(cena, "c5-numeros")
      rolar(hasContentDescription("Gráfico de pizza dos grupos musculares"))
      foto(cena, "c6-numeros-pizza")
      rolar(hasText("MAIOR PROGRESSÃO DE CARGA"))
      regra.onNodeWithText("MAIOR PROGRESSÃO DE CARGA").assertExists()
      foto(cena, "c7-numeros-progressao")
      regra.onNodeWithText("Tudo").performClick()
      regra.waitForIdle()
    }
  }

  @Test fun pausarConcluirContinuarECancelar() {
    abrir().use { cena ->
      regra.onNodeWithText("Agora não").performClick()
      regra.onNodeWithText("⏱ Iniciar treino").performClick()
      regra.onNodeWithText("⏸ Pausar").performClick()
      regra.onNodeWithText("CRONÔMETRO PAUSADO").assertExists()
      assertTrue(Diario(ctx).de(hoje).sessaoPausada)
      foto(cena, "c8-pausado")
      regra.onNodeWithText("▶ Retomar").performClick()
      regra.onNodeWithText("✓ Concluir treino").performClick()
      regra.onNodeWithText("TREINO CONCLUÍDO").assertExists()
      assertTrue(Diario(ctx).de(hoje).sessaoConcluida)
      regra.onNodeWithText("▶ Continuar treino").performClick()
      regra.onNodeWithText("TREINO EM ANDAMENTO", substring = true).assertExists()

      // Cancelar pede confirmação; "Voltar" não apaga nada.
      regra.onNodeWithText("Cancelar cronômetro").performClick()
      regra.onNodeWithText("Deseja mesmo cancelar o cronômetro?").assertExists()
      regra.onNodeWithText("Voltar").performClick()
      assertNotNull(Diario(ctx).de(hoje).treinoInicio)
      regra.onNodeWithText("Cancelar cronômetro").performClick()
      regra.onAllNodesWithText("Cancelar cronômetro").onLast().performClick() // o botão do aviso
      regra.waitForIdle()
      regra.onNodeWithText("⏱ Iniciar treino").assertExists()
    }
    assertEquals(null, Diario(ctx).de(hoje).treinoInicio)
  }

  @Test fun apagarExercicioEDiaNoTreino() {
    abrir().use {
      regra.onNodeWithText("Agora não").performClick()
      val campo = regra.onAllNodes(hasSetTextAction()).onFirst()
      for (t in listOf("squat 160kg x 4, 4", "bench press 90kg x 5")) { campo.performTextInput(t); campo.performImeAction() }
      regra.waitForIdle()
      rolar(hasText("⋮"))
      regra.onAllNodesWithText("⋮").onFirst().performClick()
      regra.onNodeWithText("Apagar Squat deste dia").performClick()
      regra.onNodeWithText("Apagar Squat?").assertExists()
      regra.onNodeWithText("Apagar").performClick()
      regra.waitForIdle()
      assertEquals(listOf("bench press"), Logbook(ctx).series.value.map { it.exercicio })

      rolar(hasText("🗑 Apagar treino deste dia"))
      regra.onNodeWithText("🗑 Apagar treino deste dia").performClick()
      regra.onNodeWithText("Apagar o treino de", substring = true).assertExists()
      regra.onNodeWithText("Apagar").performClick()
      regra.waitForIdle()
    }
    assertTrue(Logbook(ctx).series.value.isEmpty())
    assertEquals(null, Diario(ctx).de(hoje).treinoInicio)
  }

  @Test fun apagarDiaNoHistorico() {
    val lb = Logbook(ctx)
    val ontem = hoje.minusDays(1)
    lb.importar(listOf(SerieLida(ontem, "squat", 100.0, 5, ""), SerieLida(hoje.minusDays(3), "squat", 95.0, 5, "")))
    abrir().use {
      regra.onNodeWithText("Histórico").performClick()
      regra.onNode(hasContentDescription("Apagar treino de ${dataCurta(ontem)}")).performClick()
      regra.onNodeWithText("Apagar").performClick()
      regra.waitForIdle()
    }
    assertEquals(listOf(hoje.minusDays(3)), Logbook(ctx).series.value.map { it.data })
  }
}
