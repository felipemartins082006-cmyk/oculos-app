package com.martins.logbook

import java.time.LocalDate

/** Uma série lida do texto, antes de ganhar id. */
data class SerieLida(
    val data: LocalDate,
    val exercicio: String,
    val peso: Double,
    val reps: Int,
    val nota: String,
)

data class Leitura(
    val series: List<SerieLida>,
    /** Linhas com texto que não deu para entender (para você conferir). */
    val naoEntendidas: List<String>,
)

/**
 * Lê o texto no formato que você usa no Discord:
 *
 *     milkinaa — 21/7/25, 7:21
 *     bench press -> 94kg x 4 reps
 *     pulldown -> 35kg x 9 reps (3x3 - cluster)
 *     Lower-back extension -> 20kg x 3 sets x 8 reps
 *
 * Também entende o jeito rápido de digitar no app: "squat 160 x 4", "squat 160kg x 4, 4, 3",
 * "pull ups bw x 10", "squat 160x4 x 3 sets".
 */
object Leitor {

  private val NUMERACAO = Regex("""^\s*\d+\.\s+""")

  private val SERIE = Regex(
      """^(.*?[^\d\s.,].*?)\s*(?:->|→|=>|:|-)?\s*""" +            // nome
          """(\d+(?:[.,]\d+)?|bw|pc)\s*(?:kgs?|quilos?)?\s*[x×*]\s*""" + // carga
          """(?:(\d+)\s*(?:sets?|s[ée]ries?)\s*[x×*]\s*)?""" +            // "3 sets x"
          """(\d+(?:\s*[,/]\s*\d+)*)\s*(?:reps?|repeti[çc][õo]es|rep)?\s*""" + // reps (ou lista)
          """(?:[x×*]\s*(\d+)\s*(?:sets?|s[ée]ries?))?\s*""" +          // "x 3 sets"
          """(?:\((.*)\))?\s*([^()]*?)\s*$""",                          // (nota) resto
      RegexOption.IGNORE_CASE,
  )

  private val DATA = Regex("""(?<!\d)(\d{1,2})/(\d{1,2})/(\d{2}|\d{4})(?!\d)""")
  // Só no formato do cabeçalho do Discord ("— Hoje às 15:56"), para que um comentário
  // como "hoje treinei pesado" não mude o dia das séries.
  private val HOJE = Regex("""(?i)(?:^|—\s*)(today|hoje)\s+(at|às|as)\s+\d{1,2}:\d{2}""")
  private val ONTEM = Regex("""(?i)(?:^|—\s*)(yesterday|ontem)\s+(at|às|as)\s+\d{1,2}:\d{2}""")

  /**
   * @param dataPadrao dia usado para as séries que vêm antes de qualquer data no texto
   * (no app: o dia que está aberto). "Hoje"/"Ontem" do Discord são contados a partir de [hoje].
   */
  fun ler(texto: String, dataPadrao: LocalDate, hoje: LocalDate = LocalDate.now()): Leitura {
    var dia = dataPadrao
    val series = mutableListOf<SerieLida>()
    val ruins = mutableListOf<String>()

    for (bruta in texto.lines()) {
      val linha = bruta.replace(NUMERACAO, "").trim()
      if (linha.isEmpty()) continue

      val s = lerSerie(linha)
      if (s != null) {
        repeat(s.series) { _ ->
          s.reps.forEach { r -> series += SerieLida(dia, s.nome, s.peso, r, s.nota) }
        }
        continue
      }

      val d = lerData(linha, hoje)
      if (d != null) {
        dia = d
        continue
      }
      ruins += bruta.trim()
    }
    return Leitura(series, ruins)
  }

  private class Item(val nome: String, val peso: Double, val reps: List<Int>, val series: Int, val nota: String)

  private fun lerSerie(linha: String): Item? {
    val m = SERIE.matchEntire(linha) ?: return null
    val g = m.groupValues
    val nome = normalizarNome(g[1])
    if (nome.isEmpty() || nome.none { it.isLetter() }) return null
    val peso = when (g[2].lowercase()) {
      "bw", "pc" -> 0.0
      else -> g[2].replace(',', '.').toDoubleOrNull() ?: return null
    }
    val reps = g[4].split(',', '/').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
    if (reps.isEmpty() || reps.any { it > 1000 }) return null
    val series = (g[3].ifEmpty { g[5] }).toIntOrNull()?.coerceIn(1, 50) ?: 1
    val nota = listOf(g[6].trim(), g[7].trim()).filter { it.isNotEmpty() }.joinToString(" ")
    return Item(nome, peso, reps, series, nota)
  }

  private fun lerData(linha: String, hoje: LocalDate): LocalDate? {
    DATA.find(linha)?.let { m ->
      val (d, mes, a) = m.destructured
      val ano = a.toInt().let { if (it < 100) 2000 + it else it }
      return runCatching { LocalDate.of(ano, mes.toInt(), d.toInt()) }.getOrNull()
    }
    if (HOJE.containsMatchIn(linha)) return hoje
    if (ONTEM.containsMatchIn(linha)) return hoje.minusDays(1)
    return null
  }

  /** Escreve as séries no mesmo formato, para backup (e dá para importar de volta). */
  fun escrever(series: List<Serie>): String = buildString {
    series.groupBy { it.data }.toSortedMap().forEach { (d, ss) ->
      append(String.format("%02d/%02d/%04d\n", d.dayOfMonth, d.monthValue, d.year))
      ss.sortedBy { it.id }.forEach { s ->
        append(s.exercicio).append(" -> ")
        append(if (s.peso == 0.0) "bw" else fmtPeso(s.peso) + "kg")
        append(" x ").append(s.reps).append(" reps")
        if (s.nota.isNotBlank()) append(" (").append(s.nota).append(")")
        append('\n')
      }
      append('\n')
    }
  }
}
