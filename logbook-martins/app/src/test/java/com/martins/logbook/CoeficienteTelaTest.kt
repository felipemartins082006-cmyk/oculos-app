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
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CoeficienteTelaTest {
  @get:Rule val regra = createEmptyComposeRule()
  private val ctx = ApplicationProvider.getApplicationContext<Context>()
  private val hoje = LocalDate.now()

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

  @Test fun notaDoDiaDescansoECoeficiente() {
    abrir().use { cena ->
      regra.onNodeWithText("Dia").performClick()
      regra.onNodeWithText("😴 Dia de descanso").performClick()
      rolar(hasContentDescription("Nota do dia 8"))
      regra.onNode(hasContentDescription("Nota do dia 8")).performClick()
      regra.onNode(hasSetTextAction() and hasText("7.5")).performTextInput("8")
      rolar(hasContentDescription("Humor 4"))
      regra.onNode(hasContentDescription("Humor 4")).performClick()
      rolar(hasText("COEFICIENTE DE EFICIÊNCIA"))
      // sono 100 (25) + nota 80 (15) + humor 75 (10) + descanso 100 (10) = 5950/60 = 99,1… → 90
      regra.onNode(hasContentDescription("Coeficiente 90")).assertExists()
      foto(cena, "e1-dia-coeficiente")
      regra.onNodeWithText("Como é calculado?").performClick()
      regra.onNodeWithText("Prontidão:", substring = true).assertExists()
    }
    val d = Diario(ctx).de(hoje)
    assertEquals(true, d.descanso)
    assertEquals(8, d.notaDia10)
  }

  @Test fun marcarDescansoNoTreino() {
    abrir().use {
      regra.onNodeWithText("Agora não").performClick()
      rolar(hasText("Marcar descanso"))
      regra.onNodeWithText("Marcar descanso").performClick()
      regra.onNodeWithText("😴 Dia de descanso").assertExists()
      regra.onNodeWithText("Desfazer").performClick()
    }
    assertNull(Diario(ctx).de(hoje).descanso)
  }

  @Test fun musculosCalendarioEInsights() {
    val lb = Logbook(ctx)
    val diario = Diario(ctx)
    val linhas = mutableListOf<SerieLida>()
    for (i in 0 until 14) {
      val d = hoje.minusDays(60L - i * 4)
      val bom = i % 2 == 0
      linhas += SerieLida(d, "squat", 140.0 + i + if (bom) 4 else -4, 5, "")
      linhas += SerieLida(d, "stiff", 100.0 + i, 8, "")
      linhas += SerieLida(d, "bench press", 90.0 + i / 2.0 + if (bom) 2 else -2, 5, "")
      linhas += SerieLida(d, "tricep pushdown", 40.0, 10, "")
      diario.alterar(d) { it.copy(sonoHoras = if (bom) 8.0 else 5.0, notaDia10 = if (bom) 8 else 3, humor = if (bom) 4 else 2) }
      diario.alterar(d.plusDays(1)) { it.copy(descanso = true, sonoHoras = 8.0, notaDia10 = 7) }
    }
    lb.importar(linhas)

    abrir().use { cena ->
      regra.onNodeWithText("Evolução").performClick()
      regra.onNodeWithText("Números").performClick()
      rolar(hasText("MÚSCULOS MAIS TREINADOS"))
      regra.onNode(hasContentDescription("Músculo Quadríceps")).performScrollTo().performClick()
      regra.onNodeWithText("• Squat", substring = true).assertExists()
      foto(cena, "e2-musculos")
      regra.onNodeWithText("TREINO E DESCANSO · 16 SEMANAS").performScrollTo()
      foto(cena, "e3-calendario")
      rolar(hasText("Insights"))
      regra.onNodeWithText("Insights").performClick()
      rolar(hasText("Quando a sua prontidão sobe 10 pontos", substring = true))
      regra.onNodeWithText("Quando a sua prontidão sobe 10 pontos", substring = true).assertExists()
      foto(cena, "e4-insights-coeficiente")
    }
  }
}
