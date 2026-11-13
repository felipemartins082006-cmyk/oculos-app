package com.martins.logbook

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnaliseTest {
  private val d0 = LocalDate.of(2026, 1, 1)
  private var id = 0L
  private fun s(dia: Int, ex: String, kg: Double, reps: Int) = Serie(id++, d0.plusDays(dia.toLong()), ex, kg, reps)

  @Test fun comida() {
    assertEquals(Alimento("arroz", 150.0, 195.0), LeitorComida.ler("arroz 150g 195kcal"))
    assertEquals(Alimento("frango grelhado", 120.0, 190.0), LeitorComida.ler("frango grelhado 120 g 190 kcal"))
    assertEquals(Alimento("banana", null, 90.0), LeitorComida.ler("banana 90kcal"))
    assertEquals(Alimento("whey", 30.0, null), LeitorComida.ler("whey 30g"))
    assertEquals(Alimento("pasta de amendoim", 15.5, 92.0), LeitorComida.ler("pasta de amendoim 15,5g - 92 kcal"))
    assertNull(LeitorComida.ler("150g"))
    assertEquals(3, LeitorComida.lerVarios("arroz 150g\nfeijão 100g\n\novo 2 un 140kcal").size)
    assertEquals("🍚", emojiDoAlimento("Arroz branco"))
    assertEquals("🍗", emojiDoAlimento("peito de frango"))
    assertEquals("🍠", emojiDoAlimento("batata doce"))
    assertEquals("🍽️", emojiDoAlimento("quibe"))
  }

  @Test fun performanceComparaComAsUltimasSessoes() {
    val series = listOf(
        s(0, "squat", 100.0, 5), s(2, "squat", 100.0, 5), s(4, "squat", 110.0, 5), // +10% vs mediana(100,100)
        s(0, "bench", 80.0, 5), s(2, "bench", 80.0, 5), s(4, "bench", 72.0, 5),     // −10%
        s(6, "squat", 95.0, 5),                                                     // vs mediana(100,100,110) = −5%
    )
    val p = performancePorDia(series)
    assertNull(p[d0]) // sem sessões antes para comparar
    assertEquals(0.0, p.getValue(d0.plusDays(4)).variacao, 1e-9)
    assertEquals(0.10, p.getValue(d0.plusDays(4)).porExercicio.getValue("squat"), 1e-9)
    assertEquals(-0.05, p.getValue(d0.plusDays(6)).variacao, 1e-9)
    assertEquals("+10,0%", fmtPct(0.10))
    assertEquals("−5,0%", fmtPct(-0.05))
  }

  @Test fun comparaSonoComidaEEstresse() {
    val perf = (1..8).associate { i ->
      val d = d0.plusDays(i.toLong())
      d to Performance(d, if (i <= 4) 0.04 else -0.03, emptyMap())
    }
    val dias = (1..8).associate { i ->
      val d = d0.plusDays(i.toLong())
      val bom = i <= 4
      d to Dia(
          d,
          sonoHoras = if (bom) 8.0 else 5.5,
          estresse = if (bom) 1 else 5,
          treinoHora = "18:00",
          refeicoes = if (bom) listOf(Refeicao(i.toLong(), "12:30", TipoRefeicao.ALMOCO, listOf(Alimento("arroz", 150.0, 195.0))))
          else listOf(Refeicao(i.toLong(), "20:00", TipoRefeicao.JANTAR, listOf(Alimento("arroz", 150.0, 195.0)))),
          eventos = if (bom) emptyList() else listOf("Prova / TCC"),
      )
    }
    val c = comparacoes(perf, dias).associateBy { it.fator }
    val sono = c.getValue("Sono")
    assertEquals(0.04, sono.mediaA, 1e-9); assertEquals(-0.03, sono.mediaB, 1e-9); assertEquals(4, sono.nA)
    assertTrue(c.getValue("Comida antes do treino").diferenca > 0)
    assertTrue(c.getValue("Estresse").diferenca > 0)
    assertTrue(c.getValue("Eventos do dia").diferenca > 0)
    // Poucos dados: não compara.
    assertTrue(comparacoes(perf.entries.take(4).associate { it.toPair() }, dias).isEmpty())

    val ctx = contextoDoDia(dias.getValue(d0.plusDays(6)))
    assertTrue("dormiu 5h30" in ctx)
    assertTrue("treinou sem comer antes" in ctx)
    assertTrue("Prova / TCC" in ctx)
  }

  @Test fun diarioVaiEVoltaEmJson() {
    val dia = Dia(
        d0, pesagens = listOf(Pesagem(1, "07:10", 82.4, TipoPesagem.JEJUM), Pesagem(2, "18:20", 84.1, TipoPesagem.SESSAO)),
        sonoHoras = 7.5, sonoQualidade = 4, humor = 3, estresse = 4, notaDia = "Apresento o TCC amanhã",
        eventos = listOf("Prova / TCC"), treinoHora = "18:30",
        refeicoes = listOf(Refeicao(3, "12:00", TipoRefeicao.ALMOCO, listOf(Alimento("arroz", 150.0, 195.0), Alimento("frango", 120.0, null)))),
    )
    assertEquals(dia, Diario.diaDe(d0, Diario.diaJson(dia)))
  }

  @Test fun mediaMovelDoPeso() {
    val m = mediaMovel(listOf(d0 to 80.0, d0.plusDays(1) to 82.0, d0.plusDays(10) to 81.0))
    assertEquals(listOf(80.0, 81.0, 81.0), m.map { it.second })
  }
}
