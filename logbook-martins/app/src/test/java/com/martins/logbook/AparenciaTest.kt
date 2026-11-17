package com.martins.logbook

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class AparenciaTest {
  @get:Rule val regra = createEmptyComposeRule()
  private val ctx = ApplicationProvider.getApplicationContext<Context>()

  @After fun limpar() {
    Aparencia.removerFoto(ctx); Aparencia.padrao(ctx)
  }

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
  private fun rolar(t: String) = regra.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(t))
  private fun rolarDesc(t: String) = regra.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasContentDescription(t))

  @Test fun escolherTemaPronto() {
    abrir().use { cena ->
      regra.onNode(hasContentDescription("Aparência")).performClick()
      regra.onNodeWithText("Aparência").assertExists()
      foto(cena, "g1-aparencia")
      rolarDesc("Tema Oceano")
      regra.onNode(hasContentDescription("Tema Oceano")).performClick()
      assertEquals("oceano", Aparencia.paletaId)
      assertEquals(PALETAS.first { it.id == "oceano" }.destaque, Destaque)
      regra.onNodeWithText("Treino").performClick()
      regra.onNodeWithText("Agora não").performClick()
      foto(cena, "g2-tema-oceano")
      regra.onNodeWithText("Dados").performClick()
      regra.onNodeWithText("Aparência e tema").performClick()
      rolarDesc("Tema Âmbar")
      regra.onNode(hasContentDescription("Tema Âmbar")).performClick()
      regra.onNodeWithText("Treino").performClick()
      foto(cena, "g3-tema-ambar")
    }
    // Fica salvo.
    Aparencia.paletaId = "rubi"
    Aparencia.carregar(ctx)
    assertEquals("ambar", Aparencia.paletaId)
  }

  @Test fun fotoDaGaleriaComCoresEOpacidade() {
    // Uma "foto" verde-azulada da galeria.
    val arquivo = File(ctx.cacheDir, "praia.jpg")
    Bitmap.createBitmap(400, 700, Bitmap.Config.ARGB_8888).apply {
      Canvas(this).drawPaint(Paint().apply {
        shader = LinearGradient(0f, 0f, 400f, 700f, AColor.rgb(10, 160, 170), AColor.rgb(250, 200, 120), Shader.TileMode.CLAMP)
      })
    }.let { b -> arquivo.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) } }

    abrir().use { cena ->
      regra.onNode(hasContentDescription("Aparência")).performClick()
      rolar("🖼  Escolher da galeria")
      regra.onNodeWithText("🖼  Escolher da galeria").performClick()
      regra.waitForIdle()
      cena.onActivity { a ->
        val pedido = shadowOf(a).nextStartedActivityForResult
        a.activityResultRegistry.dispatchResult(pedido.requestCode, android.app.Activity.RESULT_OK,
            Intent().setData(android.net.Uri.fromFile(arquivo)))
      }
      regra.waitForIdle()
      assertNotNull(Aparencia.fundoImagem)
      rolarDesc("Usar as cores da foto")
      regra.onNode(hasContentDescription("Usar as cores da foto")).performClick()
      assertEquals("foto", Aparencia.paleta.id)
      // A cor principal tirada da foto é verde-azulada (tom entre 160° e 200°).
      val hsv = FloatArray(3)
      AColor.colorToHSV(Destaque.toArgb(), hsv)
      assertTrue("tom ${hsv[0]}", hsv[0] in 160f..200f)
      rolarDesc("Escurecer a foto")
      regra.onNode(hasContentDescription("Escurecer a foto")).performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
      assertEquals(0.3f, Aparencia.escurecer, 0.01f)
      rolarDesc("Opacidade do vidro")
      regra.onNode(hasContentDescription("Opacidade do vidro")).performSemanticsAction(SemanticsActions.SetProgress) { it(0.2f) }
      assertEquals(0.2f, Aparencia.vidro, 0.01f)
      foto(cena, "g4-aparencia-foto")
      regra.onNodeWithText("Treino").performClick()
      regra.onNodeWithText("Agora não").performClick()
      foto(cena, "g5-treino-com-foto")
    }
    // Ao abrir o app de novo, a foto e os ajustes continuam.
    Aparencia.fundoImagem = null; Aparencia.escurecer = 0.55f
    Aparencia.carregar(ctx)
    assertNotNull(Aparencia.fundoImagem)
    assertEquals(0.3f, Aparencia.escurecer, 0.01f)
    assertTrue(Aparencia.usarCoresDaFoto)
    Aparencia.removerFoto(ctx)
    assertNull(Aparencia.fundoImagem)
    assertEquals("rubi", Aparencia.paleta.id)
  }
}
