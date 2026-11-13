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
    val texto = File("src/test/resources/exemplo_discord.txt").readText()
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
    val r = Leitor.ler(File("src/test/resources/exemplo_discord.txt").readText(), hoje, hoje)
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

  @Test fun ordemDasDatas() {
    // Só dias até 12: ambíguo, lê como dia/mês e avisa.
    val a = Leitor.ler("x — 7/6/25, 15:56\nsquat 100 x 5", hoje, hoje)
    assertEquals(LocalDate.of(2025, 6, 7), a.series.single().data)
    assertTrue(a.ordemAmbigua)
    // Formato americano detectado pelo 6/21.
    val b = Leitor.ler("x — 6/7/25\nsquat 100 x 5\nx — 6/21/25\nsquat 105 x 5", hoje, hoje)
    assertEquals(OrdemData.MES_DIA, b.ordem)
    assertEquals(listOf(LocalDate.of(2025, 6, 7), LocalDate.of(2025, 6, 21)), b.series.map { it.data })
    // Trocar à mão.
    val c = Leitor.ler("x — 7/6/25\nsquat 100 x 5", hoje, hoje, OrdemData.MES_DIA)
    assertEquals(LocalDate.of(2025, 7, 6), c.series.single().data)
  }

  @Test fun exportacoesDoDiscord() {
    val txt = "[07-Jun-25 3:56 PM] milkinaa\npull ups -> 7.5kg x 7 reps\n\n[10-Jun-25 2:22 PM] milkinaa\nsquat 100 x 5\n"
    assertEquals(listOf(LocalDate.of(2025, 6, 7), LocalDate.of(2025, 6, 10)), Leitor.ler(txt, hoje, hoje).series.map { it.data })

    val csv = "AuthorID,Author,Date,Content,Attachments,Reactions\n" +
        "\"1\",\"milkinaa\",\"2025-06-07T15:56:00.0000000-03:00\",\"pull ups -> 7.5kg x 7 reps\nincline smith bench press -> 62.5kg x 6 reps\",\"\",\"\"\n" +
        "\"1\",\"milkinaa\",\"2025-06-10T14:22:00.0000000-03:00\",\"incline smith bench press -> 70kg x 9 reps (1 RIR)\",\"\",\"\"\n"
    val rc = Leitor.ler(csv, hoje, hoje)
    assertEquals("CSV do Discord", rc.formato)
    assertEquals(3, rc.series.size)
    assertEquals(LocalDate.of(2025, 6, 10), rc.series.last().data)
    assertEquals("1 RIR", rc.series.last().nota)

    val json = """{"guild":{},"messages":[
      {"timestamp":"2025-06-07T15:56:00.000-03:00","content":"pull ups -> 7.5kg x 7 reps"},
      {"timestamp":"2025-06-14T14:07:00.000-03:00","content":"pull ups -> 8.75kg x 8 reps\nlol"}]}"""
    val rj = Leitor.ler(json, hoje, hoje)
    assertEquals("JSON do Discord", rj.formato)
    assertEquals(listOf(7.5, 8.75), rj.series.map { it.peso })
    assertEquals(listOf("lol"), rj.naoEntendidas)
  }
}
