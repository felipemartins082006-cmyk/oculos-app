package com.martins.logbook

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

/**
 * Coeficiente de eficiência (0 a 100) de um dia.
 *
 * - **Prontidão** (como você chegou): sono (horas e qualidade), nota do dia, humor, estresse,
 *   alimentação e recuperação (dias de descanso antes). Cada item vira uma nota de 0 a 100 e entra
 *   com o seu peso; os que você não preencheu ficam de fora (a conta usa só o que existe).
 * - **Desempenho** (como você rendeu): o rendimento do treino comparado com as suas últimas sessões
 *   (1RM estimado). 0% vale 50; +10% vale 100; −10% vale 0.
 * - **Coeficiente** = 60% prontidão + 40% desempenho nos dias de treino; nos dias de descanso, é a prontidão.
 *
 * É uma primeira versão, de propósito simples e transparente: dá para ajustar os pesos depois.
 */
data class Componente(val nome: String, val nota: Double, val peso: Double, val detalhe: String)

data class Coeficiente(
    val data: LocalDate,
    val diaDeTreino: Boolean,
    val prontidao: Double?,
    val desempenho: Double?,
    val valor: Double?,
    val componentes: List<Componente>,
    /** O que faltou preencher para a conta ficar completa. */
    val faltando: List<String>,
)

const val PESO_PRONTIDAO = 0.6
const val PESO_DESEMPENHO = 0.4

/** Dia de treino: tem séries anotadas, ou você marcou como treino. Senão, é descanso (ou sem registro). */
fun ehDiaDeTreino(data: LocalDate, series: List<Serie>, dia: Dia?): Boolean =
    series.any { it.data == data } || dia?.descanso == false

/** Quantos dias sem treinar antes deste dia (null se não há treino anterior). */
fun diasDeDescansoAntes(data: LocalDate, datasTreino: Collection<LocalDate>): Int? {
  val anterior = datasTreino.filter { it < data }.maxOrNull() ?: return null
  return (ChronoUnit.DAYS.between(anterior, data) - 1).toInt()
}

private fun limitar(v: Double) = v.coerceIn(0.0, 100.0)

fun coeficienteDoDia(
    data: LocalDate,
    series: List<Serie>,
    dias: Map<LocalDate, Dia>,
    perf: Map<LocalDate, Performance> = performancePorDia(series),
): Coeficiente {
  val dia = dias[data]
  val treino = ehDiaDeTreino(data, series, dia)
  val c = mutableListOf<Componente>()
  val falta = mutableListOf<String>()

  dia?.sonoHoras?.let { h ->
    c += Componente("Sono", limitar(if (h >= 8) 100.0 else (h - 4) / 4 * 100), 25.0, "dormiu ${fmtHoras(h)}")
  } ?: run { falta += "horas de sono" }
  dia?.sonoQualidade?.let { q ->
    c += Componente("Qualidade do sono", (q - 1) / 4.0 * 100, 15.0, "$q/5")
  } ?: run { falta += "qualidade do sono" }
  dia?.notaDia10?.let { n -> c += Componente("Nota do dia", n * 10.0, 15.0, "$n/10") } ?: run { falta += "nota do dia" }
  dia?.humor?.let { h -> c += Componente("Humor", (h - 1) / 4.0 * 100, 10.0, "$h/5") } ?: run { falta += "humor" }
  dia?.estresse?.let { e -> c += Componente("Estresse", (5 - e) / 4.0 * 100, 10.0, "$e/5 (menos é melhor)") }
      ?: run { falta += "estresse" }

  // Alimentação: no dia de treino, o que pesa é ter comido antes; no descanso, comer bem ao longo do dia.
  val refeicoes = dia?.refeicoes.orEmpty()
  if (refeicoes.isNotEmpty()) {
    val antes = dia?.refeicoesAntesDoTreino()
    c += if (treino && antes != null) {
      Componente("Alimentação", if (antes.isNotEmpty()) 100.0 else 35.0, 15.0,
          if (antes.isNotEmpty()) "comeu antes do treino" else "treinou sem comer antes")
    } else {
      Componente("Alimentação", when (refeicoes.size) { 1 -> 40.0; 2 -> 70.0; else -> 100.0 }, 15.0, "${refeicoes.size} refeições")
    }
  } else falta += "alimentação"

  // Recuperação: dias de descanso antes do treino (1–2 é o ideal); no dia de descanso, descansar conta a favor.
  val datasTreino = series.map { it.data }.toSet() + dias.filterValues { it.descanso == false }.keys
  if (treino) {
    diasDeDescansoAntes(data, datasTreino)?.let { d ->
      val nota = when (d) { 0 -> 55.0; 1 -> 100.0; 2 -> 95.0; 3 -> 80.0; else -> 65.0 }
      c += Componente("Recuperação", nota, 10.0, if (d == 0) "treinou ontem também" else "$d dia(s) de descanso antes")
    }
  } else {
    c += Componente("Recuperação", 100.0, 10.0, "dia de descanso")
  }

  val pesoTotal = c.sumOf { it.peso }
  // Só a recuperação não basta: precisa de pelo menos mais um item preenchido.
  val prontidao = if (c.size >= 2) c.sumOf { it.nota * it.peso } / pesoTotal else null
  val desempenho = perf[data]?.let { limitar(50 + it.variacao * 500) }
  val valor = when {
    !treino -> prontidao
    prontidao != null && desempenho != null -> PESO_PRONTIDAO * prontidao + PESO_DESEMPENHO * desempenho
    else -> prontidao ?: desempenho
  }
  return Coeficiente(data, treino, prontidao, desempenho, valor, c, falta)
}

/** Coeficiente de todos os dias com algum dado (treino ou diário). */
fun coeficientes(series: List<Serie>, dias: Map<LocalDate, Dia>): List<Coeficiente> {
  val perf = performancePorDia(series)
  val datas = (series.map { it.data } + dias.keys).toSortedSet()
  return datas.map { coeficienteDoDia(it, series, dias, perf) }.filter { it.valor != null }
}

/** Relação entre prontidão e rendimento: inclinação (rendimento por ponto de prontidão) e correlação r. */
data class Relacao(val inclinacao: Double, val r: Double, val n: Int)

fun relacaoProntidaoRendimento(series: List<Serie>, dias: Map<LocalDate, Dia>, minimo: Int = 5): Relacao? {
  val perf = performancePorDia(series)
  val pares = perf.mapNotNull { (d, p) -> coeficienteDoDia(d, series, dias, perf).prontidao?.let { it to p.variacao } }
  if (pares.size < minimo) return null
  val mx = pares.map { it.first }.average()
  val my = pares.map { it.second }.average()
  val sxy = pares.sumOf { (x, y) -> (x - mx) * (y - my) }
  val sxx = pares.sumOf { (x, _) -> (x - mx) * (x - mx) }
  val syy = pares.sumOf { (_, y) -> (y - my) * (y - my) }
  if (sxx == 0.0 || syy == 0.0) return null
  return Relacao(sxy / sxx, sxy / sqrt(sxx * syy), pares.size)
}

/** Palavra para a nota: "excelente", "bom"… */
fun faixaCoeficiente(v: Double): String = when {
  v >= 85 -> "excelente"
  v >= 70 -> "bom"
  v >= 55 -> "ok"
  v >= 40 -> "baixo"
  else -> "muito baixo"
}
