package com.martins.logbook

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstatisticasTest {
  private val hoje = LocalDate.of(2026, 11, 13) // sexta
  private var id = 0L
  private fun s(d: LocalDate, ex: String, kg: Double, reps: Int, t: Long? = null) = Serie(id++, d, ex, kg, reps, momento = t)

  @Test fun gruposMusculares() {
    val esperado = mapOf(
        "squat" to Grupo.PERNAS, "leg curl" to Grupo.PERNAS, "stiff" to Grupo.PERNAS, "deadlift" to Grupo.PERNAS,
        "adductor homs machine" to Grupo.PERNAS, "bulgarian" to Grupo.PERNAS, "unilateral leg press" to Grupo.PERNAS,
        "bench press" to Grupo.PEITO, "incline smith bench press" to Grupo.PEITO, "dips" to Grupo.PEITO,
        "pull ups" to Grupo.COSTAS, "chest supported row" to Grupo.COSTAS, "pulldown" to Grupo.COSTAS, "barbell row" to Grupo.COSTAS,
        "shoulder smith press" to Grupo.OMBROS, "cable lateral raises" to Grupo.OMBROS, "lateral raises" to Grupo.OMBROS,
        "seated bicep curl" to Grupo.BRACOS, "tricep pushdown" to Grupo.BRACOS, "bicep scott curl" to Grupo.BRACOS,
        "spider bicep curl" to Grupo.BRACOS,
        "abs club machine" to Grupo.CORE, "back extension" to Grupo.CORE, "lower-back extension" to Grupo.CORE,
        "farmer walk" to Grupo.OUTROS,
    )
    assertEquals(emptyMap<String, Grupo>(), esperado.filter { (ex, g) -> grupoDe(ex) != g }.mapValues { grupoDe(it.key) })
  }

  @Test fun tempoEDescanso() {
    val d = hoje
    val t0 = 1_000_000_000_000L
    val ss = listOf(
        s(d, "squat", 100.0, 5, t0), s(d, "squat", 100.0, 5, t0 + 120_000), // 2 min de descanso
        s(d, "squat", 100.0, 5, t0 + 210_000),                               // 1min30
        s(d, "bench", 80.0, 5, t0 + 210_000),                                // mesma hora (várias de uma vez): ignora
        s(d, "bench", 80.0, 5, t0 + 210_000 + 40 * 60_000),                  // 40 min: pausa, não descanso
    )
    assertEquals(listOf(120_000L, 90_000L), descansos(ss))
    assertEquals(40 * 60_000L + 210_000L, duracaoTreino(null, ss))
    assertEquals(3_600_000L, duracaoTreino(Dia(d, treinoInicio = t0, treinoFim = t0 + 3_600_000L), ss))
    assertNull(duracaoTreino(null, ss.take(1)))
    assertEquals("1:05:09", fmtDuracao(3_909_000))
    assertEquals("0:07", fmtDuracao(7_000))
    assertEquals("00:00:00".replace("00:00:00", "0:00:00"), fmtDuracao(0, sempreHoras = true))
    assertEquals("1h12", fmtMinutos(72 * 60_000L))
    assertEquals("45 min", fmtMinutos(45 * 60_000L))
  }

  @Test fun temporizador() {
    Temporizador.zerar()
    Temporizador.iniciar(90_000, agora = 0)
    assertEquals(60_000L, Temporizador.restante(30_000))
    Temporizador.pausar(agora = 30_000)
    assertEquals(60_000L, Temporizador.restante(999_999))
    Temporizador.mais(15_000)
    assertEquals(75_000L, Temporizador.restante(999_999))
    Temporizador.continuar(agora = 100_000)
    assertEquals(0L, Temporizador.restante(500_000))
    Temporizador.zerar()
    assertNull(Temporizador.restante(0))
  }

  @Test fun numerosDoPeriodo() {
    // 3 semanas seguidas treinando seg e qui, squat subindo 10 kg por semana, bench parado.
    val segundas = listOf(hoje.minusDays(18), hoje.minusDays(11), hoje.minusDays(4))
    val series = segundas.flatMapIndexed { i, seg ->
      listOf(
          s(seg, "squat", 100.0 + i * 10, 5), s(seg, "squat", 100.0 + i * 10, 5),
          s(seg.plusDays(3), "bench press", 80.0, 5), s(seg.plusDays(3), "pull ups", 10.0, 8),
      )
    } + s(hoje.minusMonths(14), "squat", 60.0, 5) // fora dos 12 meses
    val e = calcularEstatisticas(series, emptyMap(), hoje.minusMonths(12).plusDays(1), hoje)
    assertEquals(6, e.treinos)
    assertEquals(12, e.series)
    assertEquals(3, e.sequenciaAtual)
    assertEquals(3, e.maiorSequencia)
    assertEquals(Grupo.PERNAS, e.porGrupo.first().first)
    assertEquals(3, e.porDiaSemana[0]) // segundas
    assertEquals(3, e.porDiaSemana[3]) // quintas
    assertEquals(12, e.porMes.size)
    assertEquals(YearMonth.from(hoje), e.porMes.last().first)
    assertEquals(120.0, e.maiorCarga!!.peso, 0.0)
    assertEquals("squat", e.progressoes.first().exercicio)
    assertTrue(e.progressoes.first().variacao > 0.19)
    assertEquals("squat", e.favorito!!.first)
    // Tudo: inclui o treino antigo.
    assertEquals(7, calcularEstatisticas(series, emptyMap(), null, hoje).treinos)
  }
}
