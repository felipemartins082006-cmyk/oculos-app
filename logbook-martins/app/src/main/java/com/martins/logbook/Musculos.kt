package com.martins.logbook

import java.util.Locale

/** Músculo específico (mais detalhado que o grupo): quadríceps, posterior, lombar, bíceps… */
enum class Musculo(val titulo: String) {
  QUADRICEPS("Quadríceps"),
  POSTERIOR("Posterior de coxa"),
  GLUTEOS("Glúteos"),
  PANTURRILHA("Panturrilha"),
  ADUTORES("Adutores e abdutores"),
  PEITO("Peitoral"),
  DORSAL("Dorsal (costas)"),
  TRAPEZIO("Trapézio"),
  LOMBAR("Lombar"),
  OMBROS("Ombros"),
  BICEPS("Bíceps"),
  TRICEPS("Tríceps"),
  ANTEBRACO("Antebraço"),
  ABDOMEN("Abdômen"),
}

/** Músculo principal e, se houver, os que ajudam (contam meia série). */
data class Alvo(val principal: Musculo, val ajudantes: List<Musculo> = emptyList())

private val REGRAS: List<Pair<List<String>, Alvo>> = listOf(
    // Mais específicos primeiro.
    listOf("leg curl", "flexora", "nordic") to Alvo(Musculo.POSTERIOR),
    listOf("stiff", "rdl", "romanian", "good morning", "romeno") to Alvo(Musculo.POSTERIOR, listOf(Musculo.GLUTEOS, Musculo.LOMBAR)),
    listOf("deadlift", "terra") to Alvo(Musculo.POSTERIOR, listOf(Musculo.GLUTEOS, Musculo.LOMBAR, Musculo.TRAPEZIO)),
    listOf("hip thrust", "glute", "glúteo", "gluteo", "elevação pélvica", "bridge", "kickback glute") to Alvo(Musculo.GLUTEOS, listOf(Musculo.POSTERIOR)),
    listOf("calf", "panturrilha", "gêmeos") to Alvo(Musculo.PANTURRILHA),
    listOf("adductor", "abductor", "adutor", "abdutor", "adutora", "abdutora") to Alvo(Musculo.ADUTORES),
    listOf("leg extension", "extensora", "sissy") to Alvo(Musculo.QUADRICEPS),
    listOf("squat", "agacha", "leg press", "hack", "bulgar", "búlgar", "lunge", "afundo", "passada", "step up") to
        Alvo(Musculo.QUADRICEPS, listOf(Musculo.GLUTEOS)),
    listOf("back extension", "hyperextension", "lombar", "lower back", "lower-back", "superman") to Alvo(Musculo.LOMBAR, listOf(Musculo.GLUTEOS)),
    listOf("abs", "abdominal", "crunch", "plank", "prancha", "leg raise", "elevação de pernas", "rollout", "ab wheel", "side bend") to
        Alvo(Musculo.ABDOMEN),
    listOf("shrug", "encolhimento") to Alvo(Musculo.TRAPEZIO),
    listOf("face pull", "rear delt", "crucifixo inverso", "upright", "remada alta") to Alvo(Musculo.OMBROS, listOf(Musculo.TRAPEZIO)),
    listOf("row", "remada", "pull up", "pull-up", "pullup", "chin", "pulldown", "pull down", "puxada", "barra fixa",
        "pullover", "cavalinho", "t-bar", "lat ") to Alvo(Musculo.DORSAL, listOf(Musculo.BICEPS)),
    listOf("shoulder", "ombro", "overhead", "ohp", "military", "militar", "arnold", "lateral", "front raise",
        "elevação frontal", "desenvolvimento", "deltoid") to Alvo(Musculo.OMBROS, listOf(Musculo.TRICEPS)),
    listOf("wrist", "forearm", "antebraço", "punho") to Alvo(Musculo.ANTEBRACO),
    listOf("tricep", "pushdown", "skull", "french", "francês", "kickback", "coice", "testa") to Alvo(Musculo.TRICEPS),
    listOf("curl", "rosca", "bicep", "scott", "preacher", "hammer", "martelo") to Alvo(Musculo.BICEPS, listOf(Musculo.ANTEBRACO)),
    listOf("bench", "supino", "chest", "peito", "fly", "crucifixo", "crossover", "cross over", "pec", "peck", "dip",
        "paralela", "push up", "pushup", "flexão", "voador", "butterfly") to Alvo(Musculo.PEITO, listOf(Musculo.TRICEPS, Musculo.OMBROS)),
)

/** O músculo que um exercício trabalha (pelo nome). Null se não reconhece. */
fun alvoDe(exercicio: String): Alvo? {
  val n = " ${exercicio.lowercase(Locale.ROOT).replace('-', ' ')} "
  return REGRAS.firstOrNull { (palavras, _) -> palavras.any { " ${it.replace('-', ' ')}" in n || it in n } }?.second
}

/** Séries por músculo: 1 para o principal, 0,5 para cada ajudante. Também guarda quais exercícios contaram. */
data class TrabalhoMusculo(val musculo: Musculo, val series: Double, val exercicios: Map<String, Double>)

fun trabalhoPorMusculo(series: List<Serie>): List<TrabalhoMusculo> {
  val total = mutableMapOf<Musculo, Double>()
  val porEx = mutableMapOf<Musculo, MutableMap<String, Double>>()
  series.forEach { s ->
    val alvo = alvoDe(s.exercicio) ?: return@forEach
    (listOf(alvo.principal to 1.0) + alvo.ajudantes.map { it to 0.5 }).forEach { (m, peso) ->
      total.merge(m, peso, Double::plus)
      porEx.getOrPut(m) { mutableMapOf() }.merge(s.exercicio, peso, Double::plus)
    }
  }
  return total.map { (m, v) -> TrabalhoMusculo(m, v, porEx.getValue(m)) }.sortedByDescending { it.series }
}
