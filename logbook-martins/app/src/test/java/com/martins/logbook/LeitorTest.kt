package com.martins.logbook

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeitorTest {
  private val hoje = LocalDate.of(2026, 11, 13)

  private fun uma(t: String) = Leitor.ler(t, hoje, hoje).series.single()

  @Test fun logbookDoDiscordInteiro() {
    val texto = File("src/main/assets/logbook_inicial.txt").readText()
    val r = Leitor.ler(texto, hoje, hoje)
    assertEquals(emptyList<String>(), r.naoEntendidas)
    // 32 cabeçalhos e 153 linhas de série; "3 sets x 8" vira 3 séries.
    assertEquals(155, r.series.size)
    val dias = r.series.map { it.data }.distinct()
    assertEquals(LocalDate.of(2025, 6, 7), dias.first())
    assertEquals(LocalDate.of(2025, 7, 23), dias.last())
    assertEquals(22, dias.size)
    // Linha sem cabeçalho ("3. chest supported row") fica no dia de antes.
    assertEquals(LocalDate.of(2025, 6, 7), r.series.first { it.exercicio == "chest supported row" }.data)
    // Erro de digitação "Lrg" vira "leg".
    assertEquals(2, r.series.count { it.exercicio == "unilateral leg press" })
    val back = r.series.filter { it.exercicio == "lower-back extension" }
    assertEquals(3, back.size); assertTrue(back.all { it.peso == 20.0 && it.reps == 8 })
  }

  @Test fun formatos() {
    uma("pull ups -> 7.5kg x 7 reps").let { assertEquals("pull ups", it.exercicio); assertEquals(7.5, it.peso, 0.0); assertEquals(7, it.reps) }
    uma("chest supported row -> 91.25 x 12 reps").let { assertEquals(91.25, it.peso, 0.0); assertEquals(12, it.reps) }
    uma("Stiff -> 140kg x 7 reps (3 RIR)").let { assertEquals("stiff", it.exercicio); assertEquals("3 RIR", it.nota) }
    uma("pulldown -> 35kg x 9 reps (3x3 - cluster)").let { assertEquals("3x3 - cluster", it.nota); assertEquals(9, it.reps) }
    uma("Squat 160kg x 4").let { assertEquals("squat", it.exercicio); assertEquals(160.0, it.peso, 0.0); assertEquals(hoje, it.data) }
    uma("squat 160 x 4").let { assertEquals(160.0, it.peso, 0.0) }
    uma("squat 160x4").let { assertEquals(4, it.reps) }
    uma("leg press 45 -> 200kg x 10").let { assertEquals("leg press 45", it.exercicio) }
    uma("pull ups bw x 10").let { assertEquals(0.0, it.peso, 0.0); assertEquals(10, it.reps) }
    uma("supino 62,5 x 8").let { assertEquals(62.5, it.peso, 0.0) }
    val lista = Leitor.ler("squat 160kg x 4, 4, 3", hoje, hoje).series
    assertEquals(listOf(4, 4, 3), lista.map { it.reps })
    assertEquals(3, Leitor.ler("squat 160kg x 5 reps x 3 sets", hoje, hoje).series.size)
  }

  @Test fun datasRelativasDoDiscord() {
    val r = Leitor.ler("milkinaa — Yesterday at 18:00\nsquat 100kg x 5\nmilkinaa — Hoje às 10:00\nsquat 105kg x 5", hoje, hoje)
    assertEquals(listOf(hoje.minusDays(1), hoje), r.series.map { it.data })
  }

  @Test fun backupVoltaIgual() {
    val r = Leitor.ler(File("src/main/assets/logbook_inicial.txt").readText(), hoje, hoje)
    val series = r.series.mapIndexed { i, l -> Serie(i.toLong(), l.data, l.exercicio, l.peso, l.reps, l.nota) }
    val de = Leitor.ler(Leitor.escrever(series), hoje, hoje)
    assertEquals(emptyList<String>(), de.naoEntendidas)
    assertEquals(r.series.sortedBy { it.data }, de.series)
  }

  @Test fun linhaEstranha() {
    val r = Leitor.ler("milkinaa — 7/6/25, 15:56\nhoje treinei muito\nsquat 100 x 5", hoje, hoje)
    assertEquals(listOf("hoje treinei muito"), r.naoEntendidas)
    assertEquals(LocalDate.of(2025, 6, 7), r.series.single().data)
  }
}
