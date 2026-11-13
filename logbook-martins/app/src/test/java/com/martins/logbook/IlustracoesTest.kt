package com.martins.logbook

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IlustracoesTest {
  private val todas = lerIndiceIlustracoes(File("src/main/assets/ilustracoes/indice.json").readText())
  private val b = BuscadorIlustracoes(todas)

  @Test fun todosOsApelidosApontamParaIlustracoesQueExistem() {
    val titulos = todas.map { it.titulo }.toSet()
    val faltando = BuscadorIlustracoes.APELIDOS.map { it.second }.filter { it !in titulos }.distinct()
    assertEquals(emptyList<String>(), faltando)
    // E cada ilustração tem os arquivos dentro do app.
    todas.flatMap { it.arquivos }.forEach { assertTrue(it, File("src/main/assets/ilustracoes/$it.webp").exists()) }
  }

  @Test fun exerciciosDoSeuLogbook() {
    val esperado = mapOf(
        "squat" to "Barbell Squat",
        "front squat" to "Front Squat with Barbell",
        "deadlift" to "Dead Lifts: Barbell",
        "stiff" to "Romanian Dead Lift",
        "bench press" to "Bench Press: Barbell",
        "larsen bench press" to "Bench Press: Barbell",
        "incline bench press" to "Bench Press: Barbell (Incline)",
        "incline smith bench press" to "Bench Press: Smith Machine (Incline)",
        "incline dumbell bench press" to "Bench Press: Dumbbell (Incline)",
        "pull ups" to "Pull Ups",
        "pulldown" to "Pull Down: Underhand",
        "barbell row" to "Bent Over Rowing",
        "chest supported row" to "T-Bar Rows",
        "unilateral leg press" to "Leg Press",
        "unilateral lrg press" to "Leg Press",
        "lower-back extension" to "Hyperextensions",
        "back extension" to "Hyperextensions",
        "seated bicep curl" to "Seated Bicep Curl with Dumbbell",
        "spider bicep curl" to "Spider Curl with Barbell",
        "bicep scott curl" to "Preacher Curl: Barbell",
        "cable lateral raises" to "Lateral Dumbbell Raises",
        "lateral raises" to "Lateral Dumbbell Raises",
        "bulgarian" to "One Leg Squat with Barbell",
        "adductor homs machine" to "Thigh Adductor",
        "dips" to "Chest Dips",
        "tricep pushdown" to "Triceps Pushdown: Cable",
        "abs club machine" to "Seated Ab Crunch with Cable",
        "leg extension" to "Leg Extensions",
        "leg extension homs" to "Leg Extensions",
        "shoulder smith press" to "Shoulder Press, Barbell",
        // Outros nomes comuns
        "agachamento" to "Barbell Squat",
        "supino inclinado" to "Bench Press: Barbell (Incline)",
        "hammer curl" to "Biceps Curl: Dumbbell (Hammer)",
        "skull crusher" to "Lying Triceps Press with Barbell",
        "hack squat" to "Hack Squat: Machine",
        "leg curl" to "Lying Leg Curl Machine",
        "calf raise" to "Standing Calf Raises using Machine",
        "rdl" to "Romanian Dead Lift",
        "lat pulldown" to "Pull Down: Underhand",
        "cable crossover" to "Cable Crossover",
        "arnold press" to "Shoulder Press, Dumbbells",
        "t-bar row" to "T-Bar Rows",
        "seated cable row" to "Seated Cable Rows",
        "rope pushdown" to "Triceps Pushdown: Cable (Rope)",
        "preacher curl" to "Preacher Curl: Barbell",
    )
    val errados = esperado.mapNotNull { (nome, titulo) ->
      val achou = b.achar(normalizarNome(nome))?.titulo
      if (achou != titulo) "$nome -> $achou (esperado $titulo)" else null
    }
    assertEquals(emptyList<String>(), errados)
  }

  @Test fun semIlustracaoQuandoNaoParece() {
    assertNull(b.achar("meditação"))
    assertNull(b.achar("corrida esteira"))
    assertNull(b.achar("xyz"))
  }

  @Test fun buscaParaEscolherOutra() {
    assertTrue(b.buscar("curl").take(10).all { "Curl" in it.titulo || "curl" in it.titulo.lowercase() })
    assertEquals(todas.size, b.buscar("").size)
  }
}
