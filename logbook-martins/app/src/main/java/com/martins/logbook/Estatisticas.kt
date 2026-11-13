package com.martins.logbook

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Grupo muscular principal de um exercício (pelo nome), para o gráfico de pizza. */
enum class Grupo(val titulo: String) {
  PERNAS("Pernas"),
  COSTAS("Costas"),
  PEITO("Peito"),
  OMBROS("Ombros"),
  BRACOS("Braços"),
  CORE("Core / lombar"),
  OUTROS("Outros"),
}

private val PALAVRAS_GRUPO = listOf(
    Grupo.CORE to listOf("abs", "abdominal", "crunch", "plank", "prancha", "leg raise", "elevação de pernas",
        "back extension", "hyperextension", "lombar", "lower back", "ab wheel", "rollout", "side bend", "oblíquo"),
    Grupo.PERNAS to listOf("squat", "agacha", "leg press", "leg extension", "leg curl", "lunge", "afundo", "passada",
        "bulgar", "búlgar", "stiff", "rdl", "romanian", "deadlift", "terra", "hip thrust", "glute", "gluteo", "glúteo",
        "calf", "panturrilha", "adductor", "abductor", "adutor", "abdutor", "hack", "extensora", "flexora",
        "step up", "good morning", "cadeira", "mesa flexora", "sissy", "pelvic", "pélvica"),
    Grupo.COSTAS to listOf("row", "remada", "pull up", "pull-up", "pullup", "chin", "pulldown", "pull down", "puxada",
        "lat ", "pullover", "shrug", "encolhimento", "barra fixa", "cavalinho", "t-bar", "t bar"),
    Grupo.OMBROS to listOf("shoulder", "ombro", "overhead", "ohp", "military", "militar", "arnold", "lateral",
        "front raise", "elevação frontal", "rear delt", "face pull", "desenvolvimento", "deltoid", "crucifixo inverso"),
    Grupo.BRACOS to listOf("curl", "rosca", "bicep", "tricep", "pushdown", "skull", "french", "francês", "kickback",
        "coice", "testa", "scott", "preacher", "hammer", "martelo", "forearm", "antebraço"),
    Grupo.PEITO to listOf("bench", "supino", "chest", "peito", "fly", "crucifixo", "crossover", "cross over", "pec",
        "peck", "dip", "paralela", "push up", "pushup", "flexão", "voador", "butterfly"),
)

fun grupoDe(exercicio: String): Grupo {
  val n = " ${exercicio.lowercase(Locale.ROOT)} "
  return PALAVRAS_GRUPO.firstOrNull { (_, palavras) -> palavras.any { it in n } }?.first ?: Grupo.OUTROS
}

/** Quanto um exercício subiu no período: melhor 1RM do começo × melhor 1RM recente. */
data class Progressao(val exercicio: String, val de: Double, val para: Double, val desde: LocalDate, val ate: LocalDate) {
  val variacao: Double get() = para / de - 1
}

data class Estatisticas(
    val treinos: Int,
    val series: Int,
    val volumeKg: Double,
    val porSemana: Double,
    /** Semanas seguidas com pelo menos um treino, contando até agora. */
    val sequenciaAtual: Int,
    val maiorSequencia: Int,
    val porGrupo: List<Pair<Grupo, Int>>,
    val porMes: List<Pair<YearMonth, Int>>,
    /** Segunda a domingo. */
    val porDiaSemana: List<Int>,
    val maiorCarga: Serie?,
    val melhorUmRm: Serie?,
    val maiorVolumeDia: Pair<LocalDate, Double>?,
    val favorito: Pair<String, Int>?,
    val progressoes: List<Progressao>,
    val melhoresTreinos: List<Performance>,
    val duracaoMediaMs: Long?,
    val tempoTotalMs: Long,
    val treinosCronometrados: Int,
    val descansoMedioMs: Long?,
    val descansoTotalMs: Long,
)

fun calcularEstatisticas(
    todas: List<Serie>,
    dias: Map<LocalDate, Dia>,
    desde: LocalDate?,
    hoje: LocalDate = LocalDate.now(),
): Estatisticas {
  val ss = if (desde == null) todas else todas.filter { it.data >= desde }
  val porData = ss.groupBy { it.data }
  val datas = porData.keys.sorted()
  val inicio = desde ?: datas.firstOrNull() ?: hoje
  val semanasNoPeriodo = (ChronoUnit.DAYS.between(inicio, hoje) + 1).coerceAtLeast(7) / 7.0

  // Sequência de semanas com treino (semana ISO), contando para trás a partir desta semana.
  val todasSemanas = todas.map { it.data }.distinct().map { it.with(DayOfWeek.MONDAY) }.toSortedSet()
  var atual = 0
  var seg = hoje.with(DayOfWeek.MONDAY)
  if (seg !in todasSemanas) seg = seg.minusWeeks(1) // a semana atual ainda pode não ter treino
  while (seg in todasSemanas) { atual++; seg = seg.minusWeeks(1) }
  var maior = 0
  var corrida = 0
  var anterior: LocalDate? = null
  todasSemanas.forEach { s ->
    corrida = if (anterior != null && s == anterior!!.plusWeeks(1)) corrida + 1 else 1
    maior = maxOf(maior, corrida)
    anterior = s
  }

  val porGrupo = ss.groupingBy { grupoDe(it.exercicio) }.eachCount().toList().sortedByDescending { it.second }

  val primeiroMes = YearMonth.from(if (desde == null) inicio else maxOf(inicio, hoje.minusMonths(11).withDayOfMonth(1)))
  val meses = generateSequence(primeiroMes) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(hoje) }.toList().takeLast(12)
  val porMes = meses.map { m -> m to datas.count { YearMonth.from(it) == m } }
  val porDiaSemana = (1..7).map { dow -> datas.count { it.dayOfWeek.value == dow } }

  val volumePorDia = porData.mapValues { (_, l) -> l.sumOf { it.volume } }

  // Progressões: exercícios com pelo menos 3 sessões no período.
  val progressoes = ss.groupBy { it.exercicio }.mapNotNull { (ex, l) ->
    val sessoes = l.groupBy { it.data }.toSortedMap().map { (d, s) -> d to s.maxOf { it.umRm } }
    if (sessoes.size < 3) return@mapNotNull null
    val n = maxOf(1, sessoes.size / 4)
    val comeco = sessoes.take(n).maxOf { it.second }
    val fim = sessoes.takeLast(n).maxOf { it.second }
    if (comeco <= 0) null else Progressao(ex, comeco, fim, sessoes.first().first, sessoes.last().first)
  }.sortedByDescending { it.variacao }.take(6)

  val perf = performancePorDia(todas).filterKeys { desde == null || it >= desde }

  val duracoes = datas.mapNotNull { d -> duracaoTreino(dias[d], porData.getValue(d)) }
  val desc = datas.flatMap { d -> descansos(porData.getValue(d)) }

  return Estatisticas(
      treinos = datas.size,
      series = ss.size,
      volumeKg = ss.sumOf { it.volume },
      porSemana = datas.size / semanasNoPeriodo,
      sequenciaAtual = atual,
      maiorSequencia = maior,
      porGrupo = porGrupo,
      porMes = porMes,
      porDiaSemana = porDiaSemana,
      maiorCarga = ss.maxWithOrNull(compareBy<Serie>({ it.peso }, { it.reps })),
      melhorUmRm = ss.maxByOrNull { it.umRm },
      maiorVolumeDia = volumePorDia.maxByOrNull { it.value }?.toPair(),
      favorito = ss.groupBy { it.exercicio }.mapValues { (_, l) -> l.map { it.data }.distinct().size }
          .maxByOrNull { it.value }?.toPair(),
      progressoes = progressoes,
      melhoresTreinos = perf.values.sortedByDescending { it.variacao }.take(3),
      duracaoMediaMs = duracoes.takeIf { it.isNotEmpty() }?.average()?.toLong(),
      tempoTotalMs = duracoes.sum(),
      treinosCronometrados = duracoes.size,
      descansoMedioMs = desc.takeIf { it.isNotEmpty() }?.average()?.toLong(),
      descansoTotalMs = desc.sum(),
  )
}

/** "12,4 t" ou "850 kg". */
fun fmtVolume(kg: Double): String =
    if (kg >= 1000) String.format(Locale.forLanguageTag("pt-BR"), "%.1f t", kg / 1000) else "${kg.toInt()} kg"
