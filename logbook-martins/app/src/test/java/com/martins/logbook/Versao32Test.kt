package com.martins.logbook

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class Versao32Test {
  @get:Rule val regra = createEmptyComposeRule()
  private val ctx = ApplicationProvider.getApplicationContext<Context>()
  private val hoje = LocalDate.now()

  @After fun limpar() { Temporizador.zerar(); Temporizador.fechar(); Temporizador.aoMudar = null; Aparencia.padrao(ctx) }

  private fun foto(cena: ActivityScenario<*>, nome: String) {
    regra.waitForIdle()
    cena.onActivity { a ->
      val v = a.window.decorView
      val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
      v.draw(Canvas(bmp))
      File(File("build/telas").apply { mkdirs() }, "$nome.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
  }

  @Test fun backupAgendadoACada24Horas() {
    WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
    BackupDiario.agendar(ctx)
    val infos = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork("backup-diario").get()
    assertEquals(1, infos.size)
    assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    assertEquals(24 * 3600_000L, infos.single().periodicityInfo!!.repeatIntervalMillis)
    // Agendar de novo não duplica.
    BackupDiario.agendar(ctx)
    assertEquals(1, WorkManager.getInstance(ctx).getWorkInfosForUniqueWork("backup-diario").get().size)
    // E a tarefa roda com sucesso.
    Logbook(ctx).adicionar(Leitor.ler("squat 100kg x 5", hoje).series)
    val r = TestListenableWorkerBuilder<BackupDiario>(ctx).build().doWork()
    assertTrue(r is ListenableWorker.Result.Success)
  }

  @Test fun diaSemTreinoEhDescanso() {
    val series = listOf(Serie(1, hoje.minusDays(2), "squat", 100.0, 5))
    assertFalse(ehDiaDeDescanso(hoje.minusDays(2), series, null))   // treinou
    assertTrue(ehDiaDeDescanso(hoje.minusDays(1), series, null))    // passou sem treino: descanso
    assertFalse(ehDiaDeDescanso(hoje, series, null))                // hoje ainda pode treinar
    assertTrue(ehDiaDeDescanso(hoje, series, Dia(hoje, descanso = true)))
    // No coeficiente, o dia sem treino conta como descanso (recuperação 100).
    val c = coeficienteDoDia(hoje.minusDays(1), series, mapOf(hoje.minusDays(1) to Dia(hoje.minusDays(1), sonoHoras = 8.0)))
    assertFalse(c.diaDeTreino)
    assertTrue(c.componentes.any { it.nome == "Recuperação" && it.nota == 100.0 })
  }

  @Test fun graficoDeCaloriasEPeso() {
    val diario = Diario(ctx)
    for (i in 0 until 10) {
      val d = hoje.minusDays(i.toLong())
      diario.alterar(d) {
        it.copy(refeicoes = listOf(
            Refeicao(diario.novoId(), "08:00", TipoRefeicao.CAFE, listOf(Alimento("aveia", 60.0, 230.0 + i * 10))),
            Refeicao(diario.novoId(), "12:30", TipoRefeicao.ALMOCO, listOf(Alimento("arroz", 200.0, 260.0), Alimento("frango", 150.0, 240.0))),
        ))
      }
      diario.pesar(d, "07:00", 82.0 - i * 0.1, TipoPesagem.JEJUM)
    }
    ActivityScenario.launch<MainActivity>(Intent(ctx, MainActivity::class.java)).use { cena ->
      regra.onNodeWithText("Evolução").performClick()
      regra.onNodeWithText("Peso").performClick()
      regra.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("CALORIAS POR DIA"))
      regra.onNodeWithText("kcal/dia · 7 dias").assertExists()
      regra.onNodeWithText("Nos últimos 30 dias", substring = true).assertExists()
      foto(cena, "h1-calorias")
    }
  }

  @Test fun avisoDoFimForaDoApp() {
    shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    SistemaTemporizador.configurar(ctx)
    Temporizador.iniciar(60_000)
    AlarmeTemporizador().onReceive(ctx, Intent())
    assertTrue(Temporizador.terminou)
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val n = shadowOf(nm).allNotifications.single()
    assertNotNull("tela cheia", n.fullScreenIntent)
    assertEquals(listOf("+30s", "Fechar"), n.actions.map { it.title.toString() })
    // A tela "Tempo!" (aparece por cima de tudo, até na tela bloqueada).
    ActivityScenario.launch(AlarmeActivity::class.java).use { cena ->
      regra.onNodeWithText("Tempo!").assertExists()
      foto(cena, "h2-alarme")
      regra.onNodeWithText("+30s").performClick()
      regra.waitForIdle()
    }
    assertNotNull(Temporizador.fimEm) // começou mais 30 s
    // Botão "Fechar" da notificação.
    AcaoTemporizador().onReceive(ctx, Intent(AcaoTemporizador.FECHAR))
    assertEquals(null, Temporizador.fimEm)
  }

  @Test fun estiloMinimalistaPadraoEVibrante() {
    ActivityScenario.launch<MainActivity>(Intent(ctx, MainActivity::class.java)).use { cena ->
      assertTrue(Aparencia.minimalista)
      regra.onNodeWithText("Agora não").performClick()
      foto(cena, "h3-minimalista")
      regra.onNode(hasContentDescription("Aparência")).performClick()
      regra.onNode(hasContentDescription("Estilo minimalista")).performClick()
      assertFalse(Aparencia.minimalista)
      regra.onNodeWithText("Treino").performClick()
      foto(cena, "h4-vibrante")
    }
    Aparencia.minimalista = true
    Aparencia.carregar(ctx)
    assertFalse(Aparencia.minimalista) // ficou salvo
  }
}
