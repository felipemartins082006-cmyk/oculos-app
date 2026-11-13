package com.martins.logbook

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/** O app aberto pelo "Compartilhar → Logbook" de outro app (Arquivos, Discord, WhatsApp…). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CompartilharTest {
  @get:Rule val regra = createEmptyComposeRule()
  private val ctx = ApplicationProvider.getApplicationContext<Context>()

  private fun esperarPrevia() =
      regra.waitUntil(30_000) { regra.onAllNodesWithText("Prévia").fetchSemanticsNodes().isNotEmpty() }

  @Test fun arquivoCompartilhadoDoAppArquivos() {
    val arquivo = File(ctx.cacheDir, "logbook-discord.txt")
    arquivo.writeText(File("src/test/resources/exemplo_discord.txt").readText())
    val intent = Intent(ctx, MainActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, Uri.fromFile(arquivo))

    ActivityScenario.launch<MainActivity>(intent).use {
      esperarPrevia()
      regra.onNodeWithText("155 séries em 22 dias", substring = true).assertExists()
      regra.onNodeWithText("Arquivo: logbook-discord.txt").assertExists()
      regra.onNodeWithText("Importar 155").performClick()
      regra.waitForIdle()
      assertTrue(ShadowToast.getTextOfLatestToast().contains("155 séries importadas"))
    }
  }

  /** Monta um PDF de verdade (várias páginas) com as linhas do texto. */
  private fun criarPdf(arquivo: File, texto: String) {
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx)
    val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
    texto.lines().chunked(45).forEach { linhas ->
      val pagina = com.tom_roush.pdfbox.pdmodel.PDPage()
      doc.addPage(pagina)
      com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, pagina).use { cs ->
        cs.beginText()
        cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11f)
        cs.setLeading(15f)
        cs.newLineAtOffset(40f, 750f)
        linhas.forEach { cs.showText(it); cs.newLine() }
        cs.endText()
      }
    }
    doc.save(arquivo)
    doc.close()
  }

  @Test fun pdfCompartilhado() {
    val arquivo = File(ctx.cacheDir, "logbook.pdf")
    criarPdf(arquivo, File("src/test/resources/exemplo_discord.txt").readText())
    assertTrue(ehPdf(arquivo.readBytes()))
    val intent = Intent(ctx, MainActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, Uri.fromFile(arquivo))
    ActivityScenario.launch<MainActivity>(intent).use {
      esperarPrevia()
      regra.onNodeWithText("155 séries em 22 dias", substring = true).assertExists()
      regra.onNodeWithText("Arquivo: logbook.pdf").assertExists()
    }
  }

  @Test fun pdfGeradoPeloScriptDoChrome() {
    // PDF feito por ferramentas/exportar-discord.js numa página que imita o Discord (300 mensagens).
    val arquivo = File(ctx.cacheDir, "logbook-discord.pdf")
    File("src/test/resources/gerado_pelo_script.pdf").copyTo(arquivo, overwrite = true)
    val texto = textoDoPdf(ctx, arquivo.readBytes())
    val r = Leitor.ler(texto, java.time.LocalDate.now())
    org.junit.Assert.assertEquals(emptyList<String>(), r.naoEntendidas)
    org.junit.Assert.assertEquals(301, r.series.size)
    assertTrue(r.series.any { it.exercicio == "elevação lateral — cabo" && it.nota == "fácil" })

    val intent = Intent(ctx, MainActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, Uri.fromFile(arquivo))
    ActivityScenario.launch<MainActivity>(intent).use {
      esperarPrevia()
      regra.onNodeWithText("301 séries em", substring = true).assertExists()
      regra.onNodeWithText("Importar 301").performClick()
      regra.waitForIdle()
      assertTrue(ShadowToast.getTextOfLatestToast().contains("301 séries importadas"))
    }
  }

  @Test fun abrirComLogbook() {
    val arquivo = File(ctx.cacheDir, "treinos.txt")
    arquivo.writeText("21/7/25\nbench press -> 94kg x 4 reps\nbench press -> 94kg x 4 reps\n")
    val intent = Intent(ctx, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setDataAndType(Uri.fromFile(arquivo), "text/plain")
    ActivityScenario.launch<MainActivity>(intent).use {
      esperarPrevia()
      regra.onNodeWithText("2 séries em 1 dias", substring = true).assertExists()
    }
  }

  @Test fun compartilharComOAppJaCheio() {
    Logbook(ctx).importar(Leitor.ler(File("src/test/resources/exemplo_discord.txt").readText(), java.time.LocalDate.now()).series)
    val arquivo = File(ctx.cacheDir, "novo.txt")
    arquivo.writeText("21/7/25\nbench press -> 94kg x 4 reps\nbench press -> 95kg x 4 reps\n")
    val intent = Intent(ctx, MainActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, Uri.fromFile(arquivo))
    ActivityScenario.launch<MainActivity>(intent).use {
      esperarPrevia()
      regra.onNodeWithText("1 novas", substring = true).assertExists()
    }
  }

  @Test fun textoCompartilhado() {
    val intent = Intent(ctx, MainActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "milkinaa — 21/7/25, 7:21\nbench press -> 94kg x 4 reps\npulldown -> 38.75kg x 9 reps")
    ActivityScenario.launch<MainActivity>(intent).use {
      esperarPrevia()
      regra.onNodeWithText("2 séries em 1 dias", substring = true).assertExists()
      regra.onNodeWithText("Arquivo: texto compartilhado").assertExists()
    }
  }
}
