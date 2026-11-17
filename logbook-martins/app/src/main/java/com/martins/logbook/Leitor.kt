package com.martins.logbook

import java.time.LocalDate
import org.json.JSONObject

/** Uma série lida do texto, antes de ganhar id. */
data class SerieLida(
    val data: LocalDate,
    val exercicio: String,
    val peso: Double,
    val reps: Int,
    val nota: String,
    /** Hora em que a série foi anotada (só vem de backups do app). */
    val momento: Long? = null,
)

/** Como ler datas do tipo 7/6/25: dia/mês (Brasil) ou mês/dia (EUA). */
enum class OrdemData(val titulo: String) {
  DIA_MES("dia/mês/ano"),
  MES_DIA("mês/dia/ano"),
}

data class Leitura(
    val series: List<SerieLida>,
    /** Linhas com texto que não deu para entender (para você conferir). */
    val naoEntendidas: List<String>,
    /** Como as datas foram lidas (detectado sozinho, mas dá para trocar). */
    val ordem: OrdemData,
    /** Se o texto tinha datas ambíguas (todas com dia e mês até 12). */
    val ordemAmbigua: Boolean,
    /** "texto", "CSV do Discord" ou "JSON do Discord". */
    val formato: String,
)

/**
 * Lê treinos escritos como no Discord:
 *
 *     milkinaa — 21/7/25, 7:21
 *     bench press -> 94kg x 4 reps
 *     pulldown -> 35kg x 9 reps (3x3 - cluster)
 *     Lower-back extension -> 20kg x 3 sets x 8 reps
 *
 * Entende também o jeito rápido de digitar ("squat 160 x 4", "squat 160kg x 4, 4, 3",
 * "pull ups bw x 10") e os arquivos que o DiscordChatExporter gera (TXT, CSV e JSON).
 * Cada data encontrada vale para as séries seguintes, até aparecer outra data.
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

  /** 7/6/25, 07/06/2025, 7.6.25 */
  private val DATA_BARRA = Regex("""(?<![\d/.])(\d{1,2})[/.](\d{1,2})[/.](\d{2}|\d{4})(?![\d/.])""")
  /** 2025-06-07 (CSV/JSON do Discord) */
  private val DATA_ISO = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")
  /** 07-Jun-25, 7 jun 2025 (TXT do DiscordChatExporter) */
  private val DATA_MES = Regex(
      """(?i)(?<!\d)(\d{1,2})[- ](jan|fev|feb|mar|abr|apr|mai|may|jun|jul|ago|aug|set|sep|out|oct|nov|dez|dec)[a-z]*\.?[- ](\d{2}|\d{4})(?!\d)""")
  private val MESES = mapOf(
      "jan" to 1, "fev" to 2, "feb" to 2, "mar" to 3, "abr" to 4, "apr" to 4, "mai" to 5, "may" to 5,
      "jun" to 6, "jul" to 7, "ago" to 8, "aug" to 8, "set" to 9, "sep" to 9, "out" to 10, "oct" to 10,
      "nov" to 11, "dez" to 12, "dec" to 12,
  )

  // Só no formato do cabeçalho do Discord ("— Hoje às 15:56"), para que um comentário
  // como "hoje treinei pesado" não mude o dia das séries.
  private val HOJE = Regex("""(?i)(?:^|—\s*|\]\s*)(today|hoje)\s+(at|às|as)\s+\d{1,2}:\d{2}""")
  private val ONTEM = Regex("""(?i)(?:^|—\s*|\]\s*)(yesterday|ontem)\s+(at|às|as)\s+\d{1,2}:\d{2}""")

  /**
   * @param dataPadrao dia das séries que vêm antes de qualquer data no texto
   *   (no app: o dia que está aberto). "Hoje"/"Ontem" do Discord contam a partir de [hoje].
   * @param ordem como ler 7/6/25; null = descobrir sozinho pelo texto.
   */
  fun ler(
      texto: String,
      dataPadrao: LocalDate,
      hoje: LocalDate = LocalDate.now(),
      ordem: OrdemData? = null,
  ): Leitura {
    val t = texto.removePrefix("﻿")
    val inicio = t.trimStart()
    val cabecalho = inicio.lineSequence().firstOrNull().orEmpty()
    return when {
      inicio.startsWith("{") || inicio.startsWith("[") ->
        runCatching { deJson(inicio) }.getOrNull()?.let { lerTexto(it, dataPadrao, hoje, OrdemData.DIA_MES, "JSON do Discord") }
            ?: runCatching { deJsonGenerico(inicio) }.getOrNull()?.let { lerTexto(it, dataPadrao, hoje, OrdemData.DIA_MES, "JSON") }
      "Content" in cabecalho && "Date" in cabecalho && "," in cabecalho ->
        deCsv(inicio)?.let { lerTexto(it, dataPadrao, hoje, OrdemData.DIA_MES, "CSV do Discord") }
      pareceCsvDeSeries(cabecalho) -> deCsvGenerico(inicio)?.let { lerTexto(it, dataPadrao, hoje, ordem, "Planilha (CSV)") }
      else -> null
    } ?: lerTexto(t, dataPadrao, hoje, ordem, "texto")
  }

  private fun lerTexto(texto: String, dataPadrao: LocalDate, hoje: LocalDate, ordem: OrdemData?, formato: String): Leitura {
    val linhas = texto.lines().map { it.replace(NUMERACAO, "").trim() }
    val (detectada, ambigua) = detectarOrdem(linhas)
    val ord = ordem ?: detectada

    var dia = dataPadrao
    val series = mutableListOf<SerieLida>()
    val ruins = mutableListOf<String>()
    for (linha in linhas) {
      if (linha.isEmpty()) continue
      val s = lerSerie(linha)
      if (s != null) {
        repeat(s.series) { _ ->
          s.reps.forEach { r -> series += SerieLida(dia, s.nome, s.peso, r, s.nota) }
        }
        continue
      }
      val d = lerData(linha, hoje, ord)
      if (d != null) {
        dia = d
        continue
      }
      ruins += linha
    }
    return Leitura(series, ruins, ord, ambigua && ordem == null && formato == "texto", formato)
  }

  /** Se alguma data tem o primeiro número > 12, é dia/mês; se o segundo > 12, é mês/dia. */
  private fun detectarOrdem(linhas: List<String>): Pair<OrdemData, Boolean> {
    var dm = 0
    var md = 0
    var alguma = false
    for (l in linhas) {
      if (lerSerie(l) != null) continue
      val m = DATA_BARRA.find(l) ?: continue
      alguma = true
      val a = m.groupValues[1].toInt()
      val b = m.groupValues[2].toInt()
      if (a > 12 && b <= 12) dm++
      if (b > 12 && a <= 12) md++
    }
    return when {
      md > dm -> OrdemData.MES_DIA to false
      dm > 0 -> OrdemData.DIA_MES to false
      else -> OrdemData.DIA_MES to alguma
    }
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

  private fun ano(a: String) = a.toInt().let { if (it < 100) 2000 + it else it }

  private fun lerData(linha: String, hoje: LocalDate, ordem: OrdemData): LocalDate? {
    DATA_ISO.find(linha)?.let { m ->
      val (a, mes, d) = m.destructured
      return runCatching { LocalDate.of(a.toInt(), mes.toInt(), d.toInt()) }.getOrNull()
    }
    DATA_MES.find(linha)?.let { m ->
      val (d, mes, a) = m.destructured
      return runCatching { LocalDate.of(ano(a), MESES.getValue(mes.lowercase().take(3)), d.toInt()) }.getOrNull()
    }
    DATA_BARRA.find(linha)?.let { m ->
      val (x, y, a) = m.destructured
      val (d, mes) = if (ordem == OrdemData.DIA_MES) x to y else y to x
      return runCatching { LocalDate.of(ano(a), mes.toInt(), d.toInt()) }.getOrNull()
    }
    if (HOJE.containsMatchIn(linha)) return hoje
    if (ONTEM.containsMatchIn(linha)) return hoje.minusDays(1)
    return null
  }

  /** Cada mensagem vira "aaaa-mm-dd" + o conteúdo, e segue pelo leitor normal. */
  private fun deJson(json: String): String {
    val msgs = JSONObject(json).getJSONArray("messages")
    return buildString {
      for (i in 0 until msgs.length()) {
        val m = msgs.getJSONObject(i)
        val ts = m.optString("timestamp")
        if (ts.length >= 10) append(ts.take(10)).append('\n')
        append(m.optString("content")).append('\n')
      }
    }
  }

  private fun deCsv(csv: String): String? {
    val linhas = lerCsv(csv)
    val cab = linhas.firstOrNull() ?: return null
    val iData = cab.indexOfFirst { it.trim().equals("Date", true) }
    val iConteudo = cab.indexOfFirst { it.trim().equals("Content", true) }
    if (iData < 0 || iConteudo < 0) return null
    return buildString {
      linhas.drop(1).forEach { l ->
        val ts = l.getOrNull(iData).orEmpty()
        if (ts.length >= 10) append(ts.take(10)).append('\n')
        append(l.getOrNull(iConteudo).orEmpty()).append('\n')
      }
    }
  }

  // ---- Planilhas e JSON de outros apps (Strong, Hevy, exportações próprias…) ----

  private val COL_DATA = listOf("data", "date", "dia", "day", "start_time", "start time", "inicio", "início", "timestamp")
  private val COL_EX = listOf("exercicio", "exercício", "exercise", "exercise_title", "exercise name", "exercise_name", "ex", "nome", "name", "movimento")
  private val COL_KG = listOf("kg", "peso", "weight", "weight_kg", "carga", "load", "weight (kg)")
  private val COL_REPS = listOf("reps", "repeticoes", "repetições", "repetitions", "rep")
  private val COL_NOTA = listOf("nota", "notas", "note", "notes", "rir", "rpe", "obs", "observação")

  private fun coluna(cab: List<String>, nomes: List<String>): Int {
    val c = cab.map { it.trim().trim('"').lowercase() }
    return c.indexOfFirst { it in nomes }.takeIf { it >= 0 }
        ?: c.indexOfFirst { col -> nomes.any { n -> n.length > 3 && n in col } }
  }

  private fun separador(linha: String) = if (linha.count { it == ';' } > linha.count { it == ',' }) ';' else ','

  private fun pareceCsvDeSeries(cabecalho: String): Boolean {
    val cab = cabecalho.split(separador(cabecalho))
    return coluna(cab, COL_EX) >= 0 && coluna(cab, COL_REPS) >= 0
  }

  /** Planilha com colunas de data, exercício, carga e reps (em qualquer ordem e nome parecido). */
  private fun deCsvGenerico(csv: String): String? {
    val sep = separador(csv.lineSequence().first())
    val linhas = lerCsv(csv, sep)
    val cab = linhas.firstOrNull() ?: return null
    val iEx = coluna(cab, COL_EX)
    val iReps = coluna(cab, COL_REPS)
    if (iEx < 0 || iReps < 0) return null
    val iData = coluna(cab, COL_DATA)
    val iKg = coluna(cab, COL_KG)
    val iNota = coluna(cab, COL_NOTA)
    return buildString {
      var ultimaData = ""
      linhas.drop(1).forEach { l ->
        val ex = l.getOrNull(iEx)?.trim().orEmpty()
        val reps = l.getOrNull(iReps)?.trim()?.replace(',', '.')?.toDoubleOrNull()?.toInt() ?: return@forEach
        if (ex.isEmpty() || reps <= 0) return@forEach
        val data = if (iData >= 0) l.getOrNull(iData)?.trim().orEmpty() else ""
        if (data.isNotEmpty() && data != ultimaData) { append(data).append('\n'); ultimaData = data }
        val kg = if (iKg >= 0) l.getOrNull(iKg)?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0 else 0.0
        append(serieTexto(ex, kg, reps, if (iNota >= 0) l.getOrNull(iNota)?.trim().orEmpty() else ""))
      }
    }.takeIf { it.isNotBlank() }
  }

  private fun serieTexto(ex: String, kg: Double, reps: Int, nota: String): String =
      "${ex.replace(Regex("[()]"), " ").trim()} -> ${if (kg <= 0) "bw" else fmtPeso(kg) + "kg"} x $reps reps" +
          (if (nota.isNotBlank() && nota != "null") " (${nota.replace(Regex("[()]"), " ").trim()})" else "") + "\n"

  /**
   * JSON de qualquer formato: procura em qualquer nível objetos com reps (e carga), herdando o nome do
   * exercício e a data dos objetos de fora (ex.: treino → exercícios → séries).
   */
  private fun deJsonGenerico(json: String): String? {
    val raiz: Any = if (json.trimStart().startsWith("[")) org.json.JSONArray(json) else JSONObject(json)
    val sb = StringBuilder()
    var ultimaData = ""
    fun texto(o: JSONObject, nomes: List<String>): String? =
        o.keys().asSequence().firstOrNull { it.lowercase() in nomes }?.let { k -> o.opt(k)?.toString()?.takeIf { it.isNotBlank() && it != "null" } }
    fun dataDe(v: String): String = v.toLongOrNull()?.takeIf { it > 100_000_000_000 }
        ?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() } ?: v
    fun andar(no: Any?, data: String?, ex: String?) {
      when (no) {
        is org.json.JSONArray -> for (i in 0 until no.length()) andar(no.opt(i), data, ex)
        is JSONObject -> {
          val d = texto(no, COL_DATA)?.let(::dataDe) ?: data
          val e = texto(no, COL_EX + "title" + "exercise_template") ?: ex
          val reps = texto(no, COL_REPS)?.replace(',', '.')?.toDoubleOrNull()?.toInt()
          if (reps != null && reps > 0 && e != null) {
            if (d != null && d != ultimaData) { sb.append(d).append('\n'); ultimaData = d }
            val kg = texto(no, COL_KG)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            sb.append(serieTexto(e, kg, reps, texto(no, COL_NOTA).orEmpty()))
          }
          no.keys().forEach { k -> val v = no.opt(k); if (v is JSONObject || v is org.json.JSONArray) andar(v, d, e) }
        }
      }
    }
    andar(raiz, null, null)
    return sb.toString().takeIf { it.isNotBlank() }
  }

  /** CSV simples com aspas (o conteúdo da mensagem pode ter várias linhas). */
  private fun lerCsv(s: String, sep: Char = ','): List<List<String>> {
    val linhas = mutableListOf<List<String>>()
    var campo = StringBuilder()
    var atual = mutableListOf<String>()
    var aspas = false
    var i = 0
    while (i < s.length) {
      val c = s[i]
      when {
        aspas && c == '"' && s.getOrNull(i + 1) == '"' -> { campo.append('"'); i++ }
        c == '"' -> aspas = !aspas
        !aspas && c == sep -> { atual += campo.toString(); campo = StringBuilder() }
        !aspas && (c == '\n' || c == '\r') -> {
          if (c == '\r' && s.getOrNull(i + 1) == '\n') i++
          atual += campo.toString(); campo = StringBuilder()
          linhas += atual; atual = mutableListOf()
        }
        else -> campo.append(c)
      }
      i++
    }
    if (campo.isNotEmpty() || atual.isNotEmpty()) { atual += campo.toString(); linhas += atual }
    return linhas
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
