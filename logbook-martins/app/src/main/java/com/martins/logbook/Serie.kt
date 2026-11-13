package com.martins.logbook

import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Uma série feita: um item da "nota fiscal" do treino.
 * Ex.: squat -> 160kg x 4 reps (1 RIR)
 */
data class Serie(
    val id: Long,
    val data: LocalDate,
    /** Nome do exercício, sempre minúsculo e com espaços normalizados (ex.: "bench press"). */
    val exercicio: String,
    /** Carga em kg (0 = só o peso do corpo). */
    val peso: Double,
    val reps: Int,
    /** O que vinha entre parênteses: RIR, cluster set, etc. */
    val nota: String = "",
) {
  /** 1RM estimado (fórmula de Epley). Junta carga e repetições num número só. */
  val umRm: Double get() = umRmEstimado(peso, reps)
  val volume: Double get() = peso * reps
}

fun umRmEstimado(peso: Double, reps: Int): Double =
    if (reps <= 1) peso else peso * (1 + reps / 30.0)

/** "62.5", "70", "8.75": o mesmo jeito que você escreve no Discord. */
fun fmtPeso(kg: Double): String {
  val r = (kg * 100).roundToLong() / 100.0
  return if (r == r.toLong().toDouble()) r.toLong().toString()
  else String.format(Locale.US, "%.2f", r).trimEnd('0').trimEnd('.')
}

fun fmtSerie(s: Serie): String =
    if (s.peso == 0.0) "bw × ${s.reps}" else "${fmtPeso(s.peso)} kg × ${s.reps}"

/** "bench press" -> "Bench Press" (só para mostrar; o nome salvo continua minúsculo). */
fun nomeBonito(exercicio: String): String =
    exercicio.split(' ').joinToString(" ") { p -> p.replaceFirstChar { it.titlecase(Locale.ROOT) } }

/** Normaliza o nome digitado para que "Squat", "squat " e "SQUAT" virem o mesmo exercício. */
fun normalizarNome(nome: String): String {
  val base = nome.lowercase(Locale.ROOT)
      .replace(Regex("\\s+"), " ")
      .trim()
      .trimEnd('-', '>', ':', '=', '→', ' ')
      .trim()
  // Erros de digitação conhecidos do seu logbook.
  return base.split(' ').joinToString(" ") { CORRECOES[it] ?: it }
}

private val CORRECOES = mapOf("lrg" to "leg")

/** Tudo de um exercício num dia (para os gráficos). */
data class DiaExercicio(val data: LocalDate, val series: List<Serie>) {
  /** A série mais pesada do dia (empate: a com mais reps). */
  val topo: Serie = series.maxWith(compareBy<Serie>({ it.peso }, { it.reps }))
  val melhorUmRm: Double = series.maxOf { it.umRm }
  val volume: Double = series.sumOf { it.volume }
  val repsTotais: Int = series.sumOf { it.reps }
}

fun historicoDe(todas: List<Serie>, exercicio: String): List<DiaExercicio> =
    todas.filter { it.exercicio == exercicio }
        .groupBy { it.data }
        .map { (d, ss) -> DiaExercicio(d, ss.sortedBy { it.id }) }
        .sortedBy { it.data }

/** Resumo de um exercício para a lista. */
data class ResumoExercicio(
    val exercicio: String,
    val treinos: Int,
    val ultimo: LocalDate,
    val maiorCarga: Serie,
    val melhorUmRm: Serie,
)

fun resumos(todas: List<Serie>): List<ResumoExercicio> =
    todas.groupBy { it.exercicio }.map { (ex, ss) ->
      ResumoExercicio(
          exercicio = ex,
          treinos = ss.map { it.data }.distinct().size,
          ultimo = ss.maxOf { it.data },
          maiorCarga = ss.maxWith(compareBy<Serie>({ it.peso }, { it.reps })),
          melhorUmRm = ss.maxBy { it.umRm },
      )
    }.sortedByDescending { it.ultimo }

/**
 * Diz se a série nova bate um recorde do exercício (carga ou 1RM estimado),
 * comparando com tudo que já estava salvo antes dela.
 */
fun recordeBatido(antes: List<Serie>, nova: Serie): String? {
  val doEx = antes.filter { it.exercicio == nova.exercicio }
  if (doEx.isEmpty()) return null
  val maxPeso = doEx.maxOf { it.peso }
  val max1rm = doEx.maxOf { it.umRm }
  return when {
    nova.peso > maxPeso -> "Recorde de carga: ${nomeBonito(nova.exercicio)} ${fmtSerie(nova)}"
    nova.umRm > max1rm + 0.01 -> "Recorde de 1RM estimado: ${nomeBonito(nova.exercicio)} ${fmtSerie(nova)}"
    else -> null
  }
}
