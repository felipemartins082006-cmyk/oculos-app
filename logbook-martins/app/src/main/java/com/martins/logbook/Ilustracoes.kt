package com.martins.logbook

import org.json.JSONArray

/**
 * Ilustrações de exercícios do projeto Everkinetic (everkinetic.com, CC BY-SA 4.0):
 * cada uma tem o início e o fim do movimento. Ficam dentro do app, em assets/ilustracoes.
 */
data class Ilustracao(val id: String, val titulo: String, val arquivos: List<String>)

fun lerIndiceIlustracoes(json: String): List<Ilustracao> {
  val arr = JSONArray(json)
  return (0 until arr.length()).map { i ->
    val o = arr.getJSONObject(i)
    val f = o.getJSONArray("f")
    Ilustracao(o.getString("id"), o.getString("t"), (0 until f.length()).map { f.getString(it) })
  }
}

/**
 * Acha a ilustração de um exercício pelo nome que você digita ("squat", "incline smith bench press",
 * "stiff", "pulldown"…): primeiro por um dicionário de nomes usados na academia, depois por
 * semelhança com os títulos das ilustrações.
 */
class BuscadorIlustracoes(val todas: List<Ilustracao>) {
  private val porTitulo = todas.associateBy { it.titulo }
  private val tokensTitulo = todas.associateWith { tokens(it.titulo) }

  /** Apelidos (já em tokens) → título da ilustração. */
  private val apelidosBrutos: List<Pair<Set<String>, Ilustracao>> =
      APELIDOS.mapNotNull { (apelido, titulo) -> porTitulo[titulo]?.let { tokens(apelido) to it } }

  /** Palavras raras pesam mais: em "spider bicep curl", "spider" decide (e não "bicep"). */
  private val peso: Map<String, Double> = apelidosBrutos.flatMap { it.first }.groupingBy { it }.eachCount()
      .mapValues { 1.0 / it.value }

  /** Do mais específico (mais palavras, palavras mais raras) para o mais geral. */
  private val apelidos = apelidosBrutos.sortedByDescending { (a, _) -> a.size + a.sumOf { peso[it] ?: 0.0 } * 0.5 }

  fun porId(id: String): Ilustracao? = todas.firstOrNull { it.id == id }

  /** A ilustração mais provável para o nome, ou null se nenhuma parecer certa. */
  fun achar(nome: String): Ilustracao? {
    val q = tokens(nome)
    if (q.isEmpty()) return null
    apelidos.firstOrNull { (a, _) -> q.containsAll(a) }?.let { return it.second }
    return ranking(q).firstOrNull()?.takeIf { (_, nota) -> nota >= 0.6 }?.first
  }

  /** Para a tela "Escolher ilustração": as mais parecidas com o texto (ou todas, sem texto). */
  fun buscar(texto: String): List<Ilustracao> {
    val q = tokens(texto)
    if (q.isEmpty()) return todas.sortedBy { it.titulo }
    val direto = apelidos.filter { (a, _) -> q.containsAll(a) }.map { it.second }
    val parecidas = ranking(q).filter { it.second > 0 }.map { it.first }
    val porTexto = todas.filter { it.titulo.contains(texto.trim(), ignoreCase = true) }
    return (direto + parecidas + porTexto).distinct()
  }

  private fun ranking(q: Set<String>): List<Pair<Ilustracao, Double>> =
      todas.map { il ->
        val t = tokensTitulo.getValue(il)
        val comuns = q.intersect(t)
        // Precisa ter em comum pelo menos uma palavra de movimento (squat, curl, press…).
        val nota = if (comuns.none { it in MOVIMENTOS }) 0.0
        else 2.0 * comuns.size / (q.size + t.size)
        il to nota
      }.sortedWith(compareByDescending<Pair<Ilustracao, Double>> { it.second }.thenBy { it.first.titulo.length })

  companion object {
    private val SINONIMOS = mapOf(
        "dumbell" to "dumbbell", "dumbells" to "dumbbell", "dumbbells" to "dumbbell", "db" to "dumbbell",
        "halter" to "dumbbell", "halteres" to "dumbbell", "bb" to "barbell", "barra" to "barbell",
        "curls" to "curl", "raises" to "raise", "rows" to "row", "rowing" to "row", "squats" to "squat",
        "lunges" to "lunge", "extensions" to "extension", "crunches" to "crunch", "shrugs" to "shrug",
        "dips" to "dip", "flys" to "fly", "flyes" to "fly", "flies" to "fly", "fly's" to "fly", "fly’s" to "fly",
        "presses" to "press", "pushups" to "pushup", "pushdowns" to "pushdown", "ups" to "up",
        "tricep" to "triceps", "bicep" to "biceps", "lifts" to "lift", "deadlifts" to "deadlift",
        "pullup" to "pull up", "pullups" to "pull up", "chinup" to "chin up", "chinups" to "chin up",
        "pulldown" to "pull down", "pulldowns" to "pull down", "push-up" to "pushup", "push" to "push",
        "calves" to "calf", "legs" to "leg", "hyperextension" to "hyperextensions",
    )

    /** Palavras que não ajudam a achar a figura (marcas de aparelho, "with", etc.). */
    private val IGNORAR = setOf(
        "with", "on", "the", "a", "of", "using", "and", "to", "homs", "club", "hammer strength", "de", "da", "do",
        "unilateral", "one", "arm", "single", "weight", "olympic",
    )

    private val MOVIMENTOS = setOf(
        "squat", "press", "curl", "row", "raise", "deadlift", "lift", "extension", "pushdown", "pull", "fly",
        "dip", "lunge", "crunch", "shrug", "calf", "pullover", "crossover", "butterfly", "hyperextensions",
        "pushup", "chin", "good", "kickback", "rollout", "bridging", "step", "adductor", "abductor", "plank",
        "snatch", "bend", "squats", "leg",
    )

    fun tokens(texto: String): Set<String> {
      var s = texto.lowercase()
          .replace(Regex("[:(),.’'\\-_/]"), " ")
          .replace(Regex("\\bdead\\s+lifts?\\b"), "deadlift")
          .replace(Regex("\\bpull\\s+downs?\\b"), "pull down")
          .replace(Regex("\\bpush\\s+downs?\\b"), "pushdown")
          .replace(Regex("\\bpush\\s+ups?\\b"), "pushup")
          .replace(Regex("\\bsmith\\s+machine\\b"), "smith")
      s = s.split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { SINONIMOS[it] ?: it }
      return s.split(' ').filter { it.isNotBlank() && it !in IGNORAR }.toSet()
    }

    /** Nomes de academia (inglês e português) → título da ilustração do Everkinetic. */
    val APELIDOS = listOf(
        // Pernas
        "squat" to "Barbell Squat", "back squat" to "Barbell Squat", "agachamento" to "Barbell Squat",
        "agachamento livre" to "Barbell Squat", "high bar squat" to "Barbell Squat", "low bar squat" to "Barbell Squat",
        "front squat" to "Front Squat with Barbell", "agachamento frontal" to "Front Squat with Barbell",
        "smith squat" to "Squats: Smith Machine", "agachamento smith" to "Squats: Smith Machine",
        "hack squat" to "Hack Squat: Machine", "hack" to "Hack Squat: Machine",
        "goblet squat" to "Squats: Dumbbells", "sumo squat" to "Pile Squat with Dumbbell",
        "bulgarian" to "One Leg Squat with Barbell", "bulgarian split squat" to "One Leg Squat with Barbell",
        "bulgaro" to "One Leg Squat with Barbell", "búlgaro" to "One Leg Squat with Barbell",
        "split squat" to "One Leg Squat with Barbell", "leg press" to "Leg Press",
        "leg extension" to "Leg Extensions", "cadeira extensora" to "Leg Extensions", "extensora" to "Leg Extensions",
        "leg curl" to "Lying Leg Curl Machine", "lying leg curl" to "Lying Leg Curl Machine",
        "mesa flexora" to "Lying Leg Curl Machine", "seated leg curl" to "Seated Leg Curl",
        "cadeira flexora" to "Seated Leg Curl", "standing leg curl" to "Standing Leg Curls",
        "adductor" to "Thigh Adductor", "adutora" to "Thigh Adductor", "cadeira adutora" to "Thigh Adductor",
        "abductor" to "Thigh Abductor", "abdutora" to "Thigh Abductor", "cadeira abdutora" to "Thigh Abductor",
        "hip adduction" to "Hip Adduction", "lunge" to "Lunges: Dumbbell", "afundo" to "Lunges: Dumbbell",
        "walking lunge" to "Lunges: Dumbbell (Walking)", "passada" to "Lunges: Dumbbell (Walking)",
        "step up" to "Step Ups: Dumbbell", "calf raise" to "Standing Calf Raises using Machine",
        "panturrilha" to "Standing Calf Raises using Machine", "seated calf raise" to "Seated Calf Raise using Machine",
        "hip thrust" to "Bridging", "glute bridge" to "Bridging", "elevação pélvica" to "Bridging",
        "sissy squat" to "Weighted Sissy Squat with Weight Plate",
        // Terra e lombar
        "deadlift" to "Dead Lifts: Barbell", "levantamento terra" to "Dead Lifts: Barbell", "terra" to "Dead Lifts: Barbell",
        "stiff" to "Romanian Dead Lift", "stiff leg deadlift" to "Romanian Dead Lift", "rdl" to "Romanian Dead Lift",
        "romanian deadlift" to "Romanian Dead Lift", "good morning" to "Good Mornings: Barbell",
        "back extension" to "Hyperextensions", "lower back extension" to "Hyperextensions",
        "hyperextensions" to "Hyperextensions", "lombar" to "Hyperextensions", "rack pull" to "Dead Lifts: Barbell",
        // Peito
        "bench press" to "Bench Press: Barbell", "supino" to "Bench Press: Barbell", "supino reto" to "Bench Press: Barbell",
        "larsen press" to "Bench Press: Barbell", "flat bench" to "Bench Press: Barbell",
        "incline bench press" to "Bench Press: Barbell (Incline)", "supino inclinado" to "Bench Press: Barbell (Incline)",
        "incline press" to "Bench Press: Barbell (Incline)",
        "incline smith bench press" to "Bench Press: Smith Machine (Incline)", "incline smith press" to "Bench Press: Smith Machine (Incline)",
        "smith bench press" to "Bench Press: Smith Machine", "smith press" to "Bench Press: Smith Machine",
        "decline bench press" to "Bench Press: Barbell (Decline)", "supino declinado" to "Bench Press: Barbell (Decline)",
        "close grip bench press" to "Bench Press: Barbell (Close Grip)",
        "dumbbell bench press" to "Bench Press: Dumbbell", "supino dumbbell" to "Bench Press: Dumbbell",
        "incline dumbbell bench press" to "Bench Press: Dumbbell (Incline)", "incline dumbbell press" to "Bench Press: Dumbbell (Incline)",
        "supino inclinado dumbbell" to "Bench Press: Dumbbell (Incline)",
        "chest press" to "Bench Press: Machine", "machine chest press" to "Bench Press: Machine",
        "incline chest press" to "Bench Press: Machine", "decline chest press" to "Decline Chest Press",
        "dip" to "Chest Dips", "chest dip" to "Chest Dips", "paralelas" to "Chest Dips", "paralela" to "Chest Dips",
        "bench dip" to "Bench Dips", "dumbbell fly" to "Dumbbell Flys", "crucifixo" to "Dumbbell Flys",
        "incline fly" to "Incline Dumbbell Fly's", "crucifixo inclinado" to "Incline Dumbbell Fly's",
        "pec deck" to "Butterfly", "peck deck" to "Butterfly", "voador" to "Butterfly",
        "butterfly" to "Butterfly", "chest fly" to "Butterfly", "machine fly" to "Butterfly",
        "cable crossover" to "Cable Crossover", "crossover" to "Cable Crossover", "cross over" to "Cable Crossover",
        "cable fly" to "Flat Bench Cable Flys", "pushup" to "Push Up: Body Weight", "flexão" to "Push Up: Body Weight",
        "pullover" to "Dumbbell Bent Arm Pullover",
        // Costas
        "pull up" to "Pull Ups", "barra fixa" to "Pull Ups", "chin up" to "Chin Up",
        "pull down" to "Pull Down: Underhand", "lat pull down" to "Pull Down: Underhand",
        "puxada" to "Pull Down: Underhand", "puxada frente" to "Pull Down: Underhand",
        "close grip pull down" to "Pull Down: V Bar ", "v bar pull down" to "Pull Down: V Bar ", "puxada triangulo" to "Pull Down: V Bar ",
        "underhand pull down" to "Pull Down: Underhand",
        "straight arm pull down" to "Straight Arm Push Down", "pull down straight arm" to "Straight Arm Push Down",
        "barbell row" to "Bent Over Rowing", "bent over row" to "Bent Over Rowing",
        "remada curvada" to "Bent Over Rowing", "pendlay row" to "Bent Over Rowing",
        "row" to "Bent Over Rowing", "remada" to "Seated Cable Rows",
        "reverse grip row" to "Reverse Grips Bent Over Barbell Rows",
        "t bar row" to "T-Bar Rows", "remada cavalinho" to "T-Bar Rows", "cavalinho" to "T-Bar Rows",
        "chest supported row" to "T-Bar Rows", "seated row" to "Seated Cable Rows", "cable row" to "Seated Cable Rows",
        "remada baixa" to "Seated Cable Rows", "low row" to "Seated Cable Rows",
        "dumbbell row" to "Rear Deltoid Row Dumbbell", "remada unilateral" to "Rear Deltoid Row Dumbbell",
        "shrug" to "Barbell Shrugs", "encolhimento" to "Barbell Shrugs", "dumbbell shrug" to "Shoulder Shrugs: Dumbbell",
        // Ombros
        "shoulder press" to "Shoulder Press, Dumbbells", "desenvolvimento" to "Shoulder Press, Dumbbells",
        "overhead press" to "Shoulder Press, Barbell", "ohp" to "Shoulder Press, Barbell",
        "military press" to "Shoulder Press, Barbell", "barbell shoulder press" to "Shoulder Press, Barbell",
        "smith shoulder press" to "Shoulder Press, Barbell", "shoulder smith press" to "Shoulder Press, Barbell",
        "machine shoulder press" to "Shoulder Press, on Machine", "arnold press" to "Shoulder Press, Dumbbells",
        "lateral raise" to "Lateral Dumbbell Raises", "elevação lateral" to "Lateral Dumbbell Raises",
        "cable lateral raise" to "Lateral Dumbbell Raises",
        "front raise" to "Front Dumbbell Raise", "elevação frontal" to "Front Dumbbell Raise",
        "rear delt fly" to "Lying Rear Lateral Raise", "reverse fly" to "Lying Rear Lateral Raise",
        "crucifixo inverso" to "Lying Rear Lateral Raise", "face pull" to "Seated Rear Lateral Cable Raise",
        "upright row" to "Upright Barbell Rows", "remada alta" to "Upright Barbell Rows",
        // Braços
        "biceps curl" to "Biceps Curl: Barbell", "barbell curl" to "Biceps Curl: Barbell", "rosca direta" to "Biceps Curl: Barbell",
        "curl" to "Biceps Curl: Dumbbell", "dumbbell curl" to "Biceps Curl: Dumbbell", "rosca" to "Biceps Curl: Dumbbell",
        "ez bar curl" to "EZ Bar Curl with Barbell", "seated biceps curl" to "Seated Bicep Curl with Dumbbell",
        "incline curl" to "Alternating Incline Curl with Dumbbell", "rosca inclinada" to "Alternating Incline Curl with Dumbbell",
        "hammer curl" to "Biceps Curl: Dumbbell (Hammer)", "rosca martelo" to "Biceps Curl: Dumbbell (Hammer)",
        "preacher curl" to "Preacher Curl: Barbell", "scott curl" to "Preacher Curl: Barbell", "biceps scott curl" to "Preacher Curl: Barbell",
        "rosca scott" to "Preacher Curl: Barbell", "spider curl" to "Spider Curl with Barbell",
        "concentration curl" to "Concentration Curls with Dumbbell", "rosca concentrada" to "Concentration Curls with Dumbbell",
        "cable curl" to "Standing Biceps Curl with Cable", "machine curl" to "Biceps Curl: Machine",
        "triceps pushdown" to "Triceps Pushdown: Cable", "pushdown" to "Triceps Pushdown: Cable",
        "triceps pulley" to "Triceps Pushdown: Cable", "triceps polia" to "Triceps Pushdown: Cable",
        "rope pushdown" to "Triceps Pushdown: Cable (Rope)", "triceps corda" to "Triceps Pushdown: Cable (Rope)",
        "skull crusher" to "Lying Triceps Press with Barbell", "triceps testa" to "Lying Triceps Press with Barbell",
        "french press" to "Triceps Extension: Dumbbell (Seated, Two Arm)", "triceps francês" to "Triceps Extension: Dumbbell (Seated, Two Arm)",
        "overhead triceps extension" to "Triceps Extension: Dumbbell (Seated, Two Arm)",
        "kickback" to "Triceps Kickback with Dumbbell", "triceps coice" to "Triceps Kickback with Dumbbell",
        "triceps dip" to "Triceps Dips: Body Weight",
        // Abdômen
        "crunch" to "Crunches", "abdominal" to "Crunches", "abs" to "Crunches", "ab" to "Crunches",
        "abs machine" to "Seated Ab Crunch with Cable", "cable crunch" to "Seated Ab Crunch with Cable",
        "abdominal máquina" to "Seated Ab Crunch with Cable", "leg raise" to "Flat Bench Leg Raises",
        "elevação de pernas" to "Flat Bench Leg Raises", "ab wheel" to "Ab Rollout on Knees with Barbell",
        "rollout" to "Ab Rollout on Knees with Barbell", "side bend" to "Side Bend with Dumbbell",
        "plank" to "Side Plank", "prancha" to "Side Plank",
    )
  }
}
