package com.martins.logbook

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/** Abre o app de verdade (MainActivity), começando vazio, e tira fotos das telas em build/telas/. */
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

  private fun dialogo(nome: String) {
    // O diálogo fica numa janela própria: pega a última janela aberta.
    regra.waitForIdle()
    val wmg = Class.forName("android.view.WindowManagerGlobal")
    val inst = wmg.getMethod("getInstance").invoke(null)
    val views = wmg.getDeclaredField("mViews").apply { isAccessible = true }.get(inst) as List<*>
    val v = views.last() as android.view.View
    val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
    regra.runOnUiThread { v.draw(android.graphics.Canvas(bmp)) }
    File(File("build/telas").apply { mkdirs() }, "$nome.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun campoPrincipal() = regra.onAllNodes(hasSetTextAction()).onFirst()

  @Test fun appComecaVazioEUsuarioInsere() {
    // 1. App novo: nenhum dado, só as boas-vindas.
    regra.onNodeWithText("Seu logbook está vazio").assertExists()
    foto("1-app-vazio")

    // 2. Treinando agora: série por série.
    campoPrincipal().performTextInput("squat 160kg x 4")
    campoPrincipal().performImeAction()
    campoPrincipal().performTextInput("squat 150kg x 6 (1 RIR)")
    campoPrincipal().performImeAction()
    campoPrincipal().performTextInput("bench press 90kg x 5")
    campoPrincipal().performImeAction()
    regra.waitForIdle()
    assertEquals(1, regra.onAllNodesWithText("Squat").fetchSemanticsNodes().size)
    foto("2-treino-de-hoje")

    // 3. Esqueceu de registrar ontem: volta um dia e cola o treino inteiro.
    regra.onNodeWithText("‹").performClick()
    regra.onNodeWithText("📋 Colar treino").performClick()
    campoPrincipal().performTextInput("deadlift 140kg x 4\ndeadlift 140kg x 4\nleg extension 80 x 10, 10, 8\nesqueci o resto")
    regra.onNodeWithText("Adicionar").performClick()
    // A linha que não deu para entender fica no campo para corrigir.
    regra.onNode(hasText("esqueci o resto") and hasSetTextAction()).assertExists()
    regra.onNodeWithText("5 séries adicionadas", substring = true).assertExists()
    foto("3-colar-treino-linha-ruim")
    regra.onNodeWithText("Fechar").performClick()
    regra.onNodeWithText("Registrando em", substring = true).assertExists()
    regra.onNodeWithText("Deadlift").assertExists()
    foto("4-dia-passado")

    // 4. Importar todo o histórico do Discord de uma vez.
    regra.onNodeWithText("Importar").performClick()
    foto("5-importar")
    val discord = File("src/test/resources/exemplo_discord.txt").readText()
    campoPrincipal().performTextInput(discord)
    regra.onNodeWithText("Ler texto colado").performClick()
    regra.waitUntil(30_000) { regra.onAllNodesWithText("Prévia").fetchSemanticsNodes().isNotEmpty() }
    regra.onNodeWithText("155 séries em 22 dias", substring = true).assertExists()
    foto("6-previa")
    regra.onNodeWithText("Importar 155").performClick()
    regra.waitForIdle()
    assertTrue(ShadowToast.getTextOfLatestToast().contains("155 séries importadas"))
    foto("7-importado")

    // Importar o mesmo texto de novo não duplica nada.
    campoPrincipal().performTextInput(discord)
    regra.onNodeWithText("Ler texto colado").performClick()
    regra.waitUntil(30_000) { regra.onAllNodesWithText("Prévia").fetchSemanticsNodes().isNotEmpty() }
    regra.onNodeWithText("0 novas", substring = true).assertExists()
    regra.onNodeWithText("Voltar").performClick()

    // 5. Gráfico com tudo junto (importado + digitado).
    regra.onNodeWithText("Evolução").performClick()
    regra.onAllNodesWithText("Bench Press")[0].performClick()
    foto("8-grafico")

    regra.onNodeWithText("Histórico").performClick()
    foto("9-historico")
  }

  @Test fun importarArquivoDoCelular() {
    regra.onNodeWithText("Importar").performClick()
    regra.onNodeWithText("📁  Escolher arquivo do celular").performClick()
    regra.waitForIdle()

    // Abre o seletor de arquivos do próprio Android, começando em Downloads e mostrando tudo.
    val pedido = org.robolectric.Shadows.shadowOf(regra.activity).nextStartedActivityForResult
    assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, pedido.intent.action)
    assertEquals("*/*", pedido.intent.type)
    assertEquals(
        "content://com.android.externalstorage.documents/document/primary%3ADownload",
        pedido.intent.getParcelableExtra<android.net.Uri>(android.provider.DocumentsContract.EXTRA_INITIAL_URI).toString(),
    )

    // A pessoa escolhe um .txt da pasta Downloads.
    val arquivo = File(regra.activity.cacheDir, "meu-logbook.txt")
    arquivo.writeText(File("src/test/resources/exemplo_discord.txt").readText())
    regra.runOnUiThread {
      regra.activity.activityResultRegistry.dispatchResult(
          pedido.requestCode, android.app.Activity.RESULT_OK,
          android.content.Intent().setData(android.net.Uri.fromFile(arquivo)),
      )
    }
    regra.waitUntil(30_000) { regra.onAllNodesWithText("Prévia").fetchSemanticsNodes().isNotEmpty() }
    regra.onNodeWithText("155 séries em 22 dias", substring = true).assertExists()
    regra.onNodeWithText("Importar 155").performClick()
    regra.waitForIdle()
    assertTrue(ShadowToast.getTextOfLatestToast().contains("155 séries importadas"))
  }

  @Test fun desfazerImportacao() {
    val lb = Logbook(ApplicationProvider.getApplicationContext())
    lb.adicionar(Leitor.ler("squat 100 x 5", LocalDate.now()).series)
    lb.importar(Leitor.ler(File("src/test/resources/exemplo_discord.txt").readText(), LocalDate.now()).series)
    assertEquals(156, lb.series.value.size)
    assertEquals(155, lb.desfazerImportacao())
    assertEquals(listOf("squat"), lb.series.value.map { it.exercicio })
  }

  @Test fun quemTinhaAVersaoComExemploFicaSoComOQueRegistrou() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    // Simula a versão 1.0: 155 séries de exemplo (ids 1..155) + uma registrada pela pessoa.
    val antigo = Logbook(ctx)
    antigo.apagarTudo()
    antigo.adicionar(Leitor.ler(File("src/test/resources/exemplo_discord.txt").readText(), LocalDate.now()).series)
    antigo.adicionar(Leitor.ler("squat 100 x 5", LocalDate.now()).series)
    ctx.getSharedPreferences("logbook", Context.MODE_PRIVATE).edit().putBoolean("inicial_importado", true).commit()

    val novo = Logbook(ctx)
    assertEquals(listOf("squat"), novo.series.value.map { it.exercicio })
  }
}
