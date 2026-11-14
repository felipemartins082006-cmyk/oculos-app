package com.martins.logbook

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoeficienteTest {
  private val d0 = LocalDate.of(2026, 1, 1)
  private var id = 0L
  private fun s(dia: Long, ex: String, kg: Double) = Serie(id++, d0.plusDays(dia), ex, kg, 5)

  @Test fun diaPerfeitoEDiaRuim() {
    // Treinos nos dias 0, 2, 4 (dia 4 rendeu +10%: 110 vs mediana 100).
    val series = listOf(s(0, "squat", 100.0), s(2, "squat", 100.0), s(4, "squat", 110.0))
    val bom = Dia(d0.plusDays(4), sonoHoras = 8.5, sonoQualidade = 5, notaDia10 = 10, humor = 5, estresse = 1,
        treinoHora = "18:00", refeicoes = listOf(Refeicao(1, "12:00", TipoRefeicao.ALMOCO, listOf(Alimento("arroz", 100.0, 130.0)))))
    val c = coeficienteDoDia(d0.plusDays(4), series, mapOf(bom.data to bom))
    assertTrue(c.diaDeTreino)
    assertEquals(100.0, c.prontidao!!, 1e-9) // tudo no máximo; recuperação: 1 dia de descanso = 100
    assertEquals(100.0, c.desempenho!!, 1e-9)
    assertEquals(100.0, c.valor!!, 1e-9)
    assertTrue(c.faltando.isEmpty())

    val ruim = Dia(d0.plusDays(2), sonoHoras = 4.0, sonoQualidade = 1, notaDia10 = 0, humor = 1, estresse = 5)
    val r = coeficienteDoDia(d0.plusDays(2), series, mapOf(ruim.data to ruim))
    // Tudo zero, menos a recuperação (1 dia de descanso = 100, peso 10 de 85).
    assertEquals(100.0 * 10 / 85, r.prontidao!!, 1e-9)
    assertEquals(listOf("alimentação"), r.faltando)
    assertEquals("muito baixo", faixaCoeficiente(r.valor!!))
  }

  @Test fun diaDeDescanso() {
    val dia = Dia(d0, descanso = true, sonoHoras = 8.0, notaDia10 = 7)
    val c = coeficienteDoDia(d0, emptyList(), mapOf(d0 to dia))
    assertTrue(!c.diaDeTreino)
    assertNull(c.desempenho)
    // sono 100 (25) + nota 70 (15) + recuperação 100 (10) → (2500 + 1050 + 1000) / 50 = 91
    assertEquals(91.0, c.valor!!, 1e-9)
    // Sem nada preenchido: sem coeficiente.
    assertNull(coeficienteDoDia(d0.plusDays(9), emptyList(), emptyMap()).valor)
  }

  @Test fun relacaoEntreProntidaoERendimento() {
    // Quanto mais sono, mais rendimento: a correlação tem que ser positiva e forte.
    val series = mutableListOf<Serie>()
    val dias = mutableMapOf<LocalDate, Dia>()
    for (i in 0 until 14) {
      val bom = i % 2 == 0
      series += s(i * 2L, "squat", if (i < 2) 100.0 else if (bom) 106.0 else 96.0)
      dias[d0.plusDays(i * 2L)] = Dia(d0.plusDays(i * 2L), sonoHoras = if (bom) 8.0 else 5.0, humor = if (bom) 5 else 2)
    }
    val r = relacaoProntidaoRendimento(series, dias)!!
    assertTrue("r=${r.r}", r.r > 0.7)
    assertTrue(r.inclinacao > 0)
    assertTrue(coeficientes(series, dias).isNotEmpty())
  }

  @Test fun musculosDoSeuLogbook() {
    val esperado = mapOf(
        "squat" to Musculo.QUADRICEPS, "front squat" to Musculo.QUADRICEPS, "leg extension" to Musculo.QUADRICEPS,
        "unilateral leg press" to Musculo.QUADRICEPS, "bulgarian" to Musculo.QUADRICEPS,
        "stiff" to Musculo.POSTERIOR, "deadlift" to Musculo.POSTERIOR, "leg curl" to Musculo.POSTERIOR,
        "adductor homs machine" to Musculo.ADUTORES, "back extension" to Musculo.LOMBAR, "lower-back extension" to Musculo.LOMBAR,
        "bench press" to Musculo.PEITO, "incline smith bench press" to Musculo.PEITO, "dips" to Musculo.PEITO,
        "larsen bench press" to Musculo.PEITO, "incline dumbell bench press" to Musculo.PEITO,
        "pull ups" to Musculo.DORSAL, "pulldown" to Musculo.DORSAL, "chest supported row" to Musculo.DORSAL, "barbell row" to Musculo.DORSAL,
        "shoulder smith press" to Musculo.OMBROS, "cable lateral raises" to Musculo.OMBROS, "lateral raises" to Musculo.OMBROS,
        "seated bicep curl" to Musculo.BICEPS, "spider bicep curl" to Musculo.BICEPS, "bicep scott curl" to Musculo.BICEPS,
        "tricep pushdown" to Musculo.TRICEPS, "abs club machine" to Musculo.ABDOMEN, "calf raise" to Musculo.PANTURRILHA,
        "hip thrust" to Musculo.GLUTEOS, "shrugs" to Musculo.TRAPEZIO,
    )
    assertEquals(emptyMap<String, Musculo?>(), esperado.filter { (ex, m) -> alvoDe(ex)?.principal != m }.mapValues { alvoDe(it.key)?.principal })
    assertNull(alvoDe("farmer walk"))
    val t = trabalhoPorMusculo(listOf(s(0, "squat", 100.0), s(0, "squat", 100.0), s(0, "stiff", 80.0)))
    assertEquals(Musculo.QUADRICEPS, t.first().musculo)
    assertEquals(2.0, t.first().series, 0.0)
    assertEquals(1.5, t.first { it.musculo == Musculo.GLUTEOS }.series, 0.0) // 0,5 de cada squat + 0,5 do stiff
  }
}
