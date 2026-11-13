package com.martins.logbook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class FigurasTest {
  @get:Rule val regra = createAndroidComposeRule<MainActivity>()

  private fun foto(nome: String) {
    regra.waitForIdle()
    val v = regra.activity.window.decorView
    val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
    regra.runOnUiThread { v.draw(Canvas(bmp)) }
    File(File("build/telas").apply { mkdirs() }, "$nome.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun campo() = regra.onAllNodes(hasSetTextAction()).onFirst()
  private fun figura(titulo: String) = regra.onAllNodes(hasContentDescription("Ilustração: $titulo"))

  @Test fun digitarSquatMostraOAgachamento() {
    campo().performTextInput("squat")
    regra.waitForIdle()
    assertEquals(1, figura("Barbell Squat").fetchSemanticsNodes().size)
    foto("f1-digitando-squat")

    campo().performTextInput(" 160kg x 4")
    campo().performImeAction()
    campo().performTextInput("abs club machine 100kg x 10")
    campo().performImeAction()
    campo().performTextInput("farmer walk 40kg x 30")
    campo().performImeAction()
    campo().performTextInput("incline smith bench press 72.5kg x 7")
    campo().performImeAction()
    regra.waitForIdle()
    // Cada cartão do dia tem a figura do exercício (o farmer walk não tem ilustração: mostra a inicial).
    assertEquals(1, figura("Barbell Squat").fetchSemanticsNodes().size)
    assertEquals(1, figura("Seated Ab Crunch with Cable").fetchSemanticsNodes().size)
    assertEquals(1, figura("Bench Press: Smith Machine (Incline)").fetchSemanticsNodes().size)
    regra.onNodeWithText("F").assertExists()
    foto("f2-treino-com-figuras")
  }

  @Test fun semIlustracaoEscolherOutraOuPorFoto() {
    campo().performTextInput("farmer walk 40kg x 30")
    campo().performImeAction()
    campo().performTextInput("squat 160kg x 4")
    campo().performImeAction()

    regra.onNodeWithText("Evolução").performClick()
    foto("f3-lista-evolucao")
    regra.onNodeWithText("Farmer Walk").performClick()
    regra.onNodeWithText("Não achei uma ilustração", substring = true).assertExists()
    foto("f4-sem-ilustracao")

    // Escolher uma ilustração pela busca.
    regra.onNodeWithText("🔍 Ilustração").performClick()
    campo().performTextReplacement("deadlift")
    regra.waitForIdle()
    foto("f5-escolher-ilustracao")
    figura("Dead Lifts: Barbell").onFirst().performClick()
    regra.waitForIdle()
    regra.onNodeWithText("Dead Lifts: Barbell").assertExists()
    regra.onNodeWithText("Voltar à automática").assertExists()
    foto("f6-ilustracao-escolhida")

    // Colocar uma foto da galeria (ex.: do aparelho).
    regra.onNodeWithText("📷 Minha foto").performClick()
    regra.waitForIdle()
    val pedido = shadowOf(regra.activity).nextStartedActivityForResult
    val arquivo = File(regra.activity.cacheDir, "aparelho.jpg")
    Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply {
      Canvas(this).apply { drawColor(Color.rgb(70, 90, 120)); drawCircle(300f, 200f, 120f, Paint().apply { color = Color.YELLOW }) }
    }.let { b -> arquivo.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
    regra.runOnUiThread {
      regra.activity.activityResultRegistry.dispatchResult(
          pedido.requestCode, android.app.Activity.RESULT_OK,
          android.content.Intent().setData(android.net.Uri.fromFile(arquivo)),
      )
    }
    regra.waitForIdle()
    regra.onNodeWithText("Sua foto").assertExists()
    // A foto foi copiada (reduzida) para dentro do app e abre como imagem.
    val salva = File(regra.activity.filesDir, "fotos").listFiles()!!.single()
    val inicio = salva.readBytes().take(2).map { it.toInt() and 0xFF }
    assertEquals(listOf(0xFF, 0xD8), inicio) // é um JPEG
    // Espera a imagem carregar (é lida fora da tela principal) antes da captura.
    repeat(10) { Thread.sleep(100); regra.mainClock.advanceTimeBy(100) }
    foto("f7-sua-foto")

    // A foto aparece também na lista e no treino.
    regra.onNodeWithText("‹").performClick()
    assertEquals(1, regra.onAllNodes(hasContentDescription("Sua foto")).fetchSemanticsNodes().size)

    // Voltar à automática: farmer walk volta a não ter imagem.
    regra.onNodeWithText("Farmer Walk").performClick()
    regra.onNodeWithText("Voltar à automática").performClick()
    regra.onNodeWithText("Não achei uma ilustração", substring = true).assertExists()
  }

  @Test fun renomearLevaAFigura() {
    campo().performTextInput("farmer walk 40kg x 30")
    campo().performImeAction()
    regra.onNodeWithText("Evolução").performClick()
    regra.onNodeWithText("Farmer Walk").performClick()
    regra.onNodeWithText("🔍 Ilustração").performClick()
    campo().performTextReplacement("shrug")
    regra.waitForIdle()
    figura("Barbell Shrugs").onFirst().performClick()
    regra.waitForIdle()
    val figuras = Figuras(regra.activity.applicationContext)
    regra.runOnUiThread { figuras.renomear("farmer walk", "farmers walk") }
    assertEquals("Barbell Shrugs", (figuras.de("farmers walk") as Figura.DeIlustracao).ilustracao.titulo)
    assertEquals(null, figuras.de("farmer walk"))
  }
}
