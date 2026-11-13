package com.martins.logbook

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.time.LocalDate
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Como você se pesou: em jejum ao acordar (o mais confiável) ou na academia, com roupa. */
enum class TipoPesagem(val titulo: String, val descricao: String) {
  JEJUM("Em jejum", "ao acordar, de cueca"),
  SESSAO("Na sessão", "na academia, com roupa"),
}

data class Pesagem(val id: Long, val hora: String, val kg: Double, val tipo: TipoPesagem)

enum class TipoRefeicao(val titulo: String, val emoji: String) {
  CAFE("Café da manhã", "☕"),
  ALMOCO("Almoço", "🍛"),
  LANCHE("Lanche", "🥪"),
  PRE("Pré-treino", "⚡"),
  POS("Pós-treino", "💪"),
  JANTAR("Jantar", "🍽️"),
  CEIA("Ceia", "🌙"),
}

/** Um item que você comeu, do jeito que você pesa: "arroz 150g 195kcal". */
data class Alimento(val nome: String, val gramas: Double?, val kcal: Double?)

data class Refeicao(
    val id: Long,
    val hora: String,
    val tipo: TipoRefeicao,
    val itens: List<Alimento>,
    val nota: String = "",
) {
  val kcal: Double get() = itens.sumOf { it.kcal ?: 0.0 }
}

/** Eventos que podem mexer com o treino (não é diagnóstico: é o contexto do dia). */
val EVENTOS_COMUNS = listOf(
    "Prova / TCC", "Trabalho puxado", "Estresse pessoal", "Dormi mal", "Doente", "Viagem",
    "Álcool", "Pouca água", "Dia de folga", "Comemoração",
)

/** Tudo do dia além das séries: corpo, sono, cabeça e comida. */
data class Dia(
    val data: LocalDate,
    val pesagens: List<Pesagem> = emptyList(),
    /** Horas dormidas na noite que terminou neste dia. */
    val sonoHoras: Double? = null,
    /** Qualidade do sono de 1 (péssimo) a 5 (ótimo). */
    val sonoQualidade: Int? = null,
    val sonoNota: String = "",
    /** Humor de 1 a 5. */
    val humor: Int? = null,
    /** Estresse de 1 (tranquilo) a 5 (muito). */
    val estresse: Int? = null,
    val notaDia: String = "",
    val eventos: List<String> = emptyList(),
    val refeicoes: List<Refeicao> = emptyList(),
    /** Hora em que o treino começou ("18:30"): marcada sozinha na primeira série do dia. */
    val treinoHora: String? = null,
) {
  val kcal: Double get() = refeicoes.sumOf { it.kcal }
  val vazio: Boolean
    get() = pesagens.isEmpty() && sonoHoras == null && sonoQualidade == null && sonoNota.isBlank() &&
        humor == null && estresse == null && notaDia.isBlank() && eventos.isEmpty() && refeicoes.isEmpty() &&
        treinoHora == null

  /** Refeições feitas antes do treino (pelo horário). Sem hora do treino: null. */
  fun refeicoesAntesDoTreino(): List<Refeicao>? = treinoHora?.let { t -> refeicoes.filter { it.hora < t } }
}

/** Lê "arroz 150g 195kcal", "frango grelhado 120 g 190 kcal", "banana 90kcal", "whey 30g". */
object LeitorComida {
  private val GRAMAS = Regex("""(\d+(?:[.,]\d+)?)\s*(?:g|gr|gramas?)\b""", RegexOption.IGNORE_CASE)
  private val KCAL = Regex("""(\d+(?:[.,]\d+)?)\s*(?:kcal|cal|calorias?)\b""", RegexOption.IGNORE_CASE)

  fun ler(linha: String): Alimento? {
    var resto = linha.trim()
    if (resto.isEmpty()) return null
    val kcal = KCAL.find(resto)?.let { resto = resto.removeRange(it.range); it.groupValues[1].numero() }
    val gramas = GRAMAS.find(resto)?.let { resto = resto.removeRange(it.range); it.groupValues[1].numero() }
    val nome = resto.replace(Regex("""\s*[-–:,·]\s*$"""), "").replace(Regex("\\s+"), " ").trim()
    if (nome.none { it.isLetter() }) return null
    return Alimento(nome, gramas, kcal)
  }

  fun lerVarios(texto: String): List<Alimento> =
      texto.split('\n', ';').mapNotNull { ler(it) }

  private fun String.numero() = replace(',', '.').toDoubleOrNull()
}

/** Ícone (emoji do próprio celular, livre de licença) para o que você comeu. */
fun emojiDoAlimento(nome: String): String {
  val n = nome.lowercase(Locale.ROOT)
  return EMOJIS.firstOrNull { (palavras, _) -> palavras.any { it in n } }?.second ?: "🍽️"
}

private val EMOJIS = listOf(
    listOf("whey", "proteína", "proteina", "shake", "vitamina") to "🥤",
    listOf("arroz", "rice") to "🍚",
    listOf("feijão", "feijao", "bean", "lentilha", "grão") to "🫘",
    listOf("frango", "chicken", "peito") to "🍗",
    listOf("carne", "patinho", "alcatra", "picanha", "beef", "steak", "bife", "músculo") to "🥩",
    listOf("peixe", "tilápia", "tilapia", "salmão", "salmao", "atum", "fish", "sardinha") to "🐟",
    listOf("camarão", "camarao", "shrimp") to "🍤",
    listOf("ovo", "egg", "omelete", "omelette") to "🥚",
    listOf("pão", "pao", "bread", "torrada") to "🍞",
    listOf("tapioca", "cuscuz", "panqueca", "crepioca") to "🫓",
    listOf("macarrão", "macarrao", "massa", "pasta", "espaguete", "lasanha") to "🍝",
    listOf("batata doce", "batata-doce") to "🍠",
    listOf("batata", "potato", "mandioca", "aipim", "inhame") to "🥔",
    listOf("aveia", "oat", "granola", "cereal", "mingau") to "🥣",
    listOf("banana") to "🍌",
    listOf("maçã", "maca", "apple") to "🍎",
    listOf("laranja", "orange", "tangerina", "mexerica") to "🍊",
    listOf("morango", "strawberr") to "🍓",
    listOf("uva", "grape") to "🍇",
    listOf("abacate", "avocado") to "🥑",
    listOf("melancia", "melão", "melao") to "🍉",
    listOf("abacaxi", "pineapple") to "🍍",
    listOf("manga", "mango", "mamão", "mamao", "fruta") to "🥭",
    listOf("leite", "milk") to "🥛",
    listOf("iogurte", "yogurt", "coalhada") to "🍦",
    listOf("queijo", "cheese", "requeijão", "requeijao", "cottage") to "🧀",
    listOf("café", "cafe", "coffee", "espresso") to "☕",
    listOf("salada", "alface", "brócolis", "brocolis", "legume", "verdura", "couve", "salad") to "🥗",
    listOf("tomate", "tomato") to "🍅",
    listOf("cenoura", "carrot") to "🥕",
    listOf("milho", "corn", "pipoca") to "🌽",
    listOf("castanha", "amendoim", "nuts", "nozes", "amêndoa", "amendoa", "pasta de amendoim") to "🥜",
    listOf("chocolate", "cacau", "brigadeiro") to "🍫",
    listOf("doce", "bolo", "cake", "sorvete", "açúcar", "acucar") to "🍰",
    listOf("pizza") to "🍕",
    listOf("hambúrguer", "hamburguer", "burger", "lanche") to "🍔",
    listOf("sushi", "japon") to "🍣",
    listOf("sopa", "caldo") to "🍲",
    listOf("suco", "juice") to "🧃",
    listOf("refrigerante", "coca", "soda") to "🥤",
    listOf("cerveja", "beer", "chopp") to "🍺",
    listOf("vinho", "wine") to "🍷",
    listOf("água", "agua", "water") to "💧",
    listOf("mel", "honey") to "🍯",
    listOf("creatina", "suplemento", "cápsula", "capsula") to "💊",
)

/**
 * O diário de cada dia (fora as séries), salvo num arquivo dentro do app.
 */
class Diario(context: Context) {
  private val arquivo = File(context.filesDir, "diario.json")
  val dias = mutableStateOf(carregar())
  private var proximoId = (dias.value.values.flatMap { d -> d.pesagens.map { it.id } + d.refeicoes.map { it.id } }
      .maxOrNull() ?: 0L) + 1

  fun de(data: LocalDate): Dia = dias.value[data] ?: Dia(data)

  fun alterar(data: LocalDate, mudanca: (Dia) -> Dia) {
    val novo = mudanca(de(data))
    val mapa = dias.value.toMutableMap()
    if (novo.vazio) mapa.remove(data) else mapa[data] = novo
    salvar(mapa)
  }

  fun novoId(): Long = proximoId++

  fun pesar(data: LocalDate, hora: String, kg: Double, tipo: TipoPesagem) =
      alterar(data) { it.copy(pesagens = (it.pesagens + Pesagem(novoId(), hora, kg, tipo)).sortedBy { p -> p.hora }) }

  /** Marca a hora do treino na primeira série do dia (se ainda não tinha). */
  fun marcarTreino(data: LocalDate, hora: String) {
    if (de(data).treinoHora == null) alterar(data) { it.copy(treinoHora = hora) }
  }

  /** Para o backup completo e para restaurar. */
  fun paraJson(): JSONObject = JSONObject().apply {
    dias.value.toSortedMap().forEach { (d, dia) -> put(d.toString(), diaJson(dia)) }
  }

  fun restaurar(json: JSONObject) {
    val mapa = dias.value.toMutableMap()
    json.keys().forEach { k -> runCatching { LocalDate.parse(k) }.getOrNull()?.let { d -> mapa[d] = diaDe(d, json.getJSONObject(k)) } }
    salvar(mapa)
    proximoId = (mapa.values.flatMap { d -> d.pesagens.map { it.id } + d.refeicoes.map { it.id } }.maxOrNull() ?: 0L) + 1
  }

  fun apagarTudo() = salvar(emptyMap())

  private fun salvar(mapa: Map<LocalDate, Dia>) {
    val json = JSONObject()
    mapa.toSortedMap().forEach { (d, dia) -> json.put(d.toString(), diaJson(dia)) }
    val tmp = File(arquivo.parentFile, "diario.json.tmp")
    tmp.writeText(json.toString())
    if (!tmp.renameTo(arquivo)) {
      arquivo.writeText(json.toString())
      tmp.delete()
    }
    dias.value = mapa
  }

  private fun carregar(): Map<LocalDate, Dia> {
    if (!arquivo.exists()) return emptyMap()
    return runCatching {
      val json = JSONObject(arquivo.readText())
      json.keys().asSequence().associate { k -> LocalDate.parse(k).let { d -> d to diaDe(d, json.getJSONObject(k)) } }
    }.getOrElse {
      arquivo.copyTo(File(arquivo.parentFile, "diario-${System.currentTimeMillis()}.ilegivel.json"), overwrite = true)
      emptyMap()
    }
  }

  companion object {
    fun diaJson(d: Dia): JSONObject = JSONObject().apply {
      d.sonoHoras?.let { put("sonoH", it) }
      d.sonoQualidade?.let { put("sonoQ", it) }
      if (d.sonoNota.isNotBlank()) put("sonoNota", d.sonoNota)
      d.humor?.let { put("humor", it) }
      d.estresse?.let { put("estresse", it) }
      if (d.notaDia.isNotBlank()) put("notaDia", d.notaDia)
      if (d.eventos.isNotEmpty()) put("eventos", JSONArray(d.eventos))
      d.treinoHora?.let { put("treinoHora", it) }
      if (d.pesagens.isNotEmpty()) put("pesagens", JSONArray().apply {
        d.pesagens.forEach { p -> put(JSONObject().put("id", p.id).put("hora", p.hora).put("kg", p.kg).put("tipo", p.tipo.name)) }
      })
      if (d.refeicoes.isNotEmpty()) put("refeicoes", JSONArray().apply {
        d.refeicoes.forEach { r ->
          put(JSONObject().put("id", r.id).put("hora", r.hora).put("tipo", r.tipo.name).put("nota", r.nota)
              .put("itens", JSONArray().apply {
                r.itens.forEach { a ->
                  put(JSONObject().put("n", a.nome).apply { a.gramas?.let { put("g", it) }; a.kcal?.let { put("k", it) } })
                }
              }))
        }
      })
    }

    fun diaDe(data: LocalDate, o: JSONObject): Dia {
      fun dbl(k: String) = if (o.has(k)) o.getDouble(k) else null
      fun int(k: String) = if (o.has(k)) o.getInt(k) else null
      val pes = o.optJSONArray("pesagens") ?: JSONArray()
      val ref = o.optJSONArray("refeicoes") ?: JSONArray()
      val ev = o.optJSONArray("eventos") ?: JSONArray()
      return Dia(
          data = data,
          pesagens = (0 until pes.length()).map { i ->
            val p = pes.getJSONObject(i)
            Pesagem(p.getLong("id"), p.getString("hora"), p.getDouble("kg"),
                runCatching { TipoPesagem.valueOf(p.getString("tipo")) }.getOrDefault(TipoPesagem.JEJUM))
          },
          sonoHoras = dbl("sonoH"),
          sonoQualidade = int("sonoQ"),
          sonoNota = o.optString("sonoNota"),
          humor = int("humor"),
          estresse = int("estresse"),
          notaDia = o.optString("notaDia"),
          eventos = (0 until ev.length()).map { ev.getString(it) },
          refeicoes = (0 until ref.length()).map { i ->
            val r = ref.getJSONObject(i)
            val it = r.optJSONArray("itens") ?: JSONArray()
            Refeicao(
                id = r.getLong("id"),
                hora = r.getString("hora"),
                tipo = runCatching { TipoRefeicao.valueOf(r.getString("tipo")) }.getOrDefault(TipoRefeicao.LANCHE),
                itens = (0 until it.length()).map { j ->
                  val a = it.getJSONObject(j)
                  Alimento(a.getString("n"), if (a.has("g")) a.getDouble("g") else null, if (a.has("k")) a.getDouble("k") else null)
                },
                nota = r.optString("nota"),
            )
          },
          treinoHora = if (o.has("treinoHora")) o.getString("treinoHora") else null,
      )
    }
  }
}
