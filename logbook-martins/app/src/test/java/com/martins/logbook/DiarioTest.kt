package com.martins.logbook

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DiarioTest {
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
  private fun campo(dica: String) = regra.onNode(hasSetTextAction() and hasText(dica))

  /** Rola a lista até o item aparecer (itens fora da tela ainda não existem). */
  private fun rolar(matcher: androidx.compose.ui.test.SemanticsMatcher) {
    regra.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).onFirst().performScrollToNode(matcher)
  }
  private fun rolarTexto(t: String, sub: Boolean = false) = rolar(hasText(t, substring = sub))

  @Test fun pesoNoInicioDoTreinoEHoraDoTreino() {
    abrir().use { cena ->
      regra.onNodeWithText("PESO DE HOJE").assertExists()
      foto(cena, "d1-treino-novo-visual")
      regra.onNodeWithText("Em jejum").performClick()
      campo("82.4").performTextInput("82,4")
      regra.onNodeWithText("Salvar").performClick()
      regra.waitForIdle()
      regra.onNodeWithText("PESO DE HOJE").assertDoesNotExist()

      regra.onAllNodes(hasSetTextAction()).onFirst().performTextInput("squat 160kg x 4")
      regra.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
      regra.waitForIdle()
    }
    val d = Diario(ctx).de(hoje)
    assertEquals(82.4, d.pesagens.single().kg, 0.0)
    assertEquals(TipoPesagem.JEJUM, d.pesagens.single().tipo)
    assertNotNull(d.treinoHora)
  }

  @Test fun abaDiaSonoCabecaComida() {
    abrir().use { cena ->
      regra.onNodeWithText("Dia").performClick()
      campo("7.5").performTextInput("7.5")
      rolar(hasContentDescription("Qualidade do sono 4")); regra.onNode(hasContentDescription("Qualidade do sono 4")).performClick()
      rolar(hasContentDescription("Humor 2")); regra.onNode(hasContentDescription("Humor 2")).performClick()
      rolar(hasContentDescription("Estresse 5")); regra.onNode(hasContentDescription("Estresse 5")).performClick()
      campo("Como foi seu dia? Ex.: apresento o TCC amanhã, fiquei ansioso.").performTextInput("Apresento o TCC amanhã")
      rolarTexto("Prova / TCC"); regra.onNodeWithText("Prova / TCC").performClick()
      foto(cena, "d2-dia-sono-cabeca")
      rolarTexto("+ Refeição"); regra.onNodeWithText("+ Refeição").performClick()
      rolarTexto("🍛 Almoço"); regra.onNodeWithText("🍛 Almoço").performClick()
      regra.onNode(hasSetTextAction() and hasText("O que você comeu (um por linha)"))
          .performTextInput("arroz 150g 195kcal\nfrango grelhado 120g 190kcal\nfeijão 100g 76kcal")
      rolarTexto("Salvar refeição"); regra.onNodeWithText("Salvar refeição").performClick()
      regra.waitForIdle()
      rolarTexto("461 kcal no dia"); regra.onNodeWithText("461 kcal no dia").assertExists()
      foto(cena, "d3-dia-alimentacao")
    }
    val d = Diario(ctx).de(hoje)
    assertEquals(7.5, d.sonoHoras!!, 0.0)
    assertEquals(4, d.sonoQualidade)
    assertEquals(2, d.humor)
    assertEquals(5, d.estresse)
    assertEquals("Apresento o TCC amanhã", d.notaDia)
    assertEquals(listOf("Prova / TCC"), d.eventos)
    assertEquals(TipoRefeicao.ALMOCO, d.refeicoes.single().tipo)
    assertEquals(3, d.refeicoes.single().itens.size)
  }

  @Test fun pesoEInsightsComDados() {
    // 12 treinos de squat/bench: dias com sono bom rendem mais, dias de prova rendem menos.
    val lb = Logbook(ctx)
    val diario = Diario(ctx)
    val inicio = hoje.minusDays(40)
    val linhas = mutableListOf<SerieLida>()
    for (i in 0 until 12) {
      val d = inicio.plusDays(i * 3L)
      val bom = i % 2 == 0
      val carga = 140.0 + i + if (bom) 4 else -4
      linhas += SerieLida(d, "squat", carga, 5, "")
      linhas += SerieLida(d, "bench press", 90.0 + i / 2.0 + if (bom) 2 else -2, 5, "")
      diario.alterar(d) {
        it.copy(
            sonoHoras = if (bom) 8.0 else 5.5, sonoQualidade = if (bom) 4 else 2, humor = if (bom) 4 else 2,
            estresse = if (bom) 1 else 5, eventos = if (bom) emptyList() else listOf("Prova / TCC"), treinoHora = "18:00",
            refeicoes = listOf(Refeicao(diario.novoId(), if (bom) "12:30" else "20:30", if (bom) TipoRefeicao.ALMOCO else TipoRefeicao.JANTAR,
                listOf(Alimento("arroz", 150.0, 195.0)))),
        )
      }
      diario.pesar(d, "07:00", 82.0 + i * 0.1, TipoPesagem.JEJUM)
      if (i % 3 == 0) diario.pesar(d, "18:00", 83.5 + i * 0.1, TipoPesagem.SESSAO)
    }
    lb.importar(linhas)

    abrir().use { cena ->
      regra.onNodeWithText("Evolução").performClick()
      regra.onNodeWithText("Peso").performClick()
      rolarTexto("Na sessão você costuma pesar", true)
      regra.onNodeWithText("Na sessão você costuma pesar", substring = true).assertExists()
      foto(cena, "d4-peso")
      regra.onNodeWithText("Insights").performClick()
      rolarTexto("Dormiu 7h ou mais"); regra.onNodeWithText("Dormiu 7h ou mais").assertExists()
      foto(cena, "d5-insights")
      rolarTexto("Dia com evento marcado"); regra.onNodeWithText("Dia com evento marcado").assertExists()
      rolarTexto("TREINOS ABAIXO DO NORMAL"); regra.onNodeWithText("TREINOS ABAIXO DO NORMAL").assertExists()
      rolarTexto("Prova / TCC")
      foto(cena, "d6-insights-quedas")
      regra.onNodeWithText("Histórico").performClick()
      foto(cena, "d7-historico")
    }
  }

  @Test fun backupCompletoVaiEVolta() {
    val lb = Logbook(ctx)
    val diario = Diario(ctx)
    lb.adicionar(Leitor.ler("squat 160kg x 4", hoje).series)
    diario.pesar(hoje, "07:00", 82.4, TipoPesagem.JEJUM)
    diario.alterar(hoje) { it.copy(sonoHoras = 7.0, notaDia = "teste") }
    val backup = org.json.JSONObject().put("logbook", 2).put("series", lb.paraJson()).put("diario", diario.paraJson()).toString()
    lb.apagarTudo(); diario.apagarTudo()
    val arquivo = File(ctx.cacheDir, "logbook-backup.json").apply { writeText(backup) }
    val intent = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(arquivo))
    ActivityScenario.launch<MainActivity>(intent).use {
      regra.waitUntil(30_000) { regra.onAllNodesWithText("Prévia").fetchSemanticsNodes().isNotEmpty() }
      regra.onNodeWithText("Restaurar backup").performClick()
      regra.waitForIdle()
    }
    assertEquals(1, Logbook(ctx).series.value.size)
    val d = Diario(ctx).de(hoje)
    assertEquals(82.4, d.pesagens.single().kg, 0.0)
    assertEquals("teste", d.notaDia)
  }
}
