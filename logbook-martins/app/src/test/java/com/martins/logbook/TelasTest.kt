package com.martins.logbook

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasSetTextAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/** Abre o app de verdade (MainActivity) e tira fotos das telas em build/telas/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TelasTest {
  @get:Rule val regra = createAndroidComposeRule<MainActivity>()

  private fun foto(nome: String) {
    regra.waitForIdle()
    val v = regra.activity.window.decorView
    val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
    regra.runOnUiThread { v.draw(android.graphics.Canvas(bmp)) }
    val dir = File("build/telas").apply { mkdirs() }
    File(dir, "$nome.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  @Test fun fluxoCompleto() {
    // Treino de hoje vazio, com sugestões dos seus exercícios.
    foto("1-treino-vazio")

    // Registrar uma série: bench press 95kg x 4 (seu recorde era 94).
    val campo = regra.onNode(hasSetTextAction())
    campo.performTextInput("bench pr")
    foto("2-digitando")
    campo.performTextInput("ess 95kg x 4")
    campo.performImeAction()
    regra.waitForIdle()
    assertTrue(ShadowToast.getTextOfLatestToast().contains("Recorde de carga"))
    campo.performTextInput("bench press 95kg x 4, 4, 3")
    campo.performImeAction()
    campo.performTextInput("squat 160kg x 4 (1 RIR)")
    campo.performImeAction()
    regra.waitForIdle()
    assertEquals(1, regra.onAllNodesWithText("Bench Press").fetchSemanticsNodes().size)
    foto("3-treino-registrado")

    regra.onNodeWithText("Histórico").performClick()
    foto("4-historico")

    regra.onNodeWithText("Evolução").performClick()
    foto("5-evolucao-lista")
    regra.onAllNodesWithText("Bench Press")[0].performClick()
    foto("6-grafico-carga")
    regra.onNodeWithText("1RM est.").performClick()
    foto("7-grafico-1rm")

    regra.onNodeWithText("Dados").performClick()
    foto("8-dados")
  }
}
