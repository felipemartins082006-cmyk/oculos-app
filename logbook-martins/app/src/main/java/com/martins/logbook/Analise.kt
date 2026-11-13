package com.martins.logbook

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * Performance de uma sessão: para cada exercício do dia, o melhor 1RM estimado comparado com a
 * mediana das últimas sessões desse exercício. A média disso é o "você rendeu +3% / −5%" do dia.
 */
data class Performance(val data: LocalDate, val variacao: Double, val porExercicio: Map<String, Double>)

fun performancePorDia(series: List<Serie>, sessoesBase: Int = 5, minimoBase: Int = 2): Map<LocalDate, Performance> {
  val porEx = series.groupBy { it.exercicio }.mapValues { (_, ss) ->
    ss.groupBy { it.data }.mapValues { (_, dia) -> dia.maxOf { it.umRm } }.toSortedMap()
  }
  val porDia = mutableMapOf<LocalDate, MutableMap<String, Double>>()
  porEx.forEach { (ex, dias) ->
    val lista = dias.entries.toList()
    lista.forEachIndexed { i, (d, rm) ->
      val antes = lista.subList(maxOf(0, i - sessoesBase), i).map { it.value }.filter { it > 0 }
      if (antes.size >= minimoBase && rm > 0) {
        val base = mediana(antes)
        porDia.getOrPut(d) { mutableMapOf() }[ex] = rm / base - 1
      }
    }
  }
  return porDia.mapValues { (d, m) -> Performance(d, m.values.average(), m) }
}

private fun mediana(v: List<Double>): Double {
  val s = v.sorted()
  return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

/** "+3,2%" / "−4,0%" */
fun fmtPct(v: Double): String {
  val p = v * 100
  val txt = String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", abs(p))
  return when {
    p >= 0.05 -> "+$txt"
    p <= -0.05 -> "−$txt"
    else -> "0,0%"
  }
}

/** Uma comparação entre dois grupos de treinos (ex.: dormiu 7h+ vs menos de 7h). */
data class Comparacao(
    val fator: String,
    val grupoA: String,
    val mediaA: Double,
    val nA: Int,
    val grupoB: String,
    val mediaB: Double,
    val nB: Int,
) {
  val diferenca: Double get() = mediaA - mediaB
}

/**
 * Compara a sua performance em dias com e sem cada fator. Só mostra quando os dois grupos têm
 * pelo menos [minimo] treinos: são padrões nos seus registros, não diagnóstico.
 */
fun comparacoes(perf: Map<LocalDate, Performance>, dias: Map<LocalDate, Dia>, minimo: Int = 3): List<Comparacao> {
  val pontos = perf.mapNotNull { (d, p) -> dias[d]?.let { it to p.variacao } }
  val out = mutableListOf<Comparacao>()

  fun dividir(fator: String, a: String, b: String, ehA: (Dia) -> Boolean?) {
    val grupos = pontos.mapNotNull { (dia, v) -> ehA(dia)?.let { it to v } }
    val va = grupos.filter { it.first }.map { it.second }
    val vb = grupos.filter { !it.first }.map { it.second }
    if (va.size >= minimo && vb.size >= minimo) out += Comparacao(fator, a, va.average(), va.size, b, vb.average(), vb.size)
  }

  dividir("Sono", "Dormiu 7h ou mais", "Dormiu menos de 7h") { d -> d.sonoHoras?.let { it >= 7 } }
  dividir("Qualidade do sono", "Sono bom (4–5)", "Sono ruim (1–2)") { d ->
    d.sonoQualidade?.let { if (it >= 4) true else if (it <= 2) false else null }
  }
  dividir("Comida antes do treino", "Comeu antes de treinar", "Treinou sem comer antes") { d ->
    d.refeicoesAntesDoTreino()?.isNotEmpty()
  }
  dividir("Café da manhã", "Tomou café da manhã", "Sem café da manhã") { d ->
    if (d.refeicoes.isEmpty()) null else d.refeicoes.any { it.tipo == TipoRefeicao.CAFE }
  }
  val kcals = pontos.map { it.first.kcal }.filter { it > 0 }
  if (kcals.size >= minimo * 2) {
    val med = mediana(kcals)
    dividir("Calorias do dia", "Comeu mais (≥ ${med.toInt()} kcal)", "Comeu menos") { d ->
      if (d.kcal > 0) d.kcal >= med else null
    }
  }
  dividir("Humor", "Humor bom (4–5)", "Humor baixo (1–2)") { d ->
    d.humor?.let { if (it >= 4) true else if (it <= 2) false else null }
  }
  dividir("Estresse", "Pouco estresse (1–2)", "Muito estresse (4–5)") { d ->
    d.estresse?.let { if (it <= 2) true else if (it >= 4) false else null }
  }
  dividir("Eventos do dia", "Dia tranquilo", "Dia com evento marcado") { d -> d.eventos.isEmpty() }
  return out.sortedByDescending { abs(it.diferenca) }
}

/** O que estava diferente num dia (para explicar uma queda ou um recorde). */
fun contextoDoDia(dia: Dia?): List<String> {
  if (dia == null) return emptyList()
  val c = mutableListOf<String>()
  dia.sonoHoras?.let { c += "dormiu ${fmtHoras(it)}" }
  dia.sonoQualidade?.let { c += "sono ${it}/5" }
  dia.humor?.let { c += "humor ${it}/5" }
  dia.estresse?.let { c += "estresse ${it}/5" }
  dia.refeicoesAntesDoTreino()?.let { c += if (it.isEmpty()) "treinou sem comer antes" else "comeu antes (${it.size})" }
  if (dia.kcal > 0) c += "${dia.kcal.toInt()} kcal"
  c += dia.eventos
  if (dia.notaDia.isNotBlank()) c += "“${dia.notaDia.trim().take(80)}”"
  return c
}

fun fmtHoras(h: Double): String {
  val horas = h.toInt()
  val min = Math.round((h - horas) * 60).toInt()
  return if (min == 0) "${horas}h" else "${horas}h${min.toString().padStart(2, '0')}"
}

/** Média móvel do peso (por tipo de pesagem) nos últimos [janela] dias. */
fun mediaMovel(pontos: List<Pair<LocalDate, Double>>, janela: Long = 7): List<Pair<LocalDate, Double>> =
    pontos.sortedBy { it.first }.map { (d, _) ->
      val dentro = pontos.filter { it.first <= d && it.first > d.minusDays(janela) }
      d to dentro.map { it.second }.average()
    }
