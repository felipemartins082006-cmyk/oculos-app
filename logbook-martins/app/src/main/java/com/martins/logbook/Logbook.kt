package com.martins.logbook

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Resultado de uma importação. */
data class Importacao(val adicionadas: Int, val repetidas: Int, val dias: Int)

/**
 * Todas as séries, salvas num arquivo dentro do app (só no seu celular, sem internet).
 * Cada mudança grava o arquivo inteiro: com milhares de séries isso continua instantâneo.
 */
class Logbook(private val context: Context) {
  private val arquivo = File(context.filesDir, "logbook.json")
  private val prefs = context.getSharedPreferences("logbook", Context.MODE_PRIVATE)

  val series = mutableStateOf<List<Serie>>(carregar())

  private var proximoId = (series.value.maxOfOrNull { it.id } ?: 0L) + 1

  /** Quantas séries da última importação ainda dá para desfazer (0 = nada). */
  val desfazivel = mutableStateOf(ultimaImportacao().count())

  init {
    // A versão 1.0 vinha com um logbook de exemplo (ids 1 a 155, gravados antes de qualquer
    // outra coisa). O app agora começa vazio: tira só esses registros de exemplo.
    if (prefs.getBoolean("inicial_importado", false)) {
      salvar(series.value.filter { it.id > 155 })
      prefs.edit().remove("inicial_importado").apply()
    }
  }

  fun adicionar(lidas: List<SerieLida>, momento: Long? = null): List<Serie> {
    val novas = lidas.map { it.comId(proximoId++).copy(momento = momento) }
    salvar(series.value + novas)
    return novas
  }

  fun atualizar(s: Serie) = salvar(series.value.map { if (it.id == s.id) s else it })

  fun apagar(id: Long) = salvar(series.value.filter { it.id != id })

  fun apagarTudo() {
    salvar(emptyList())
    esquecerImportacao()
  }

  /** Renomeia um exercício; se o nome novo já existe, os dois viram um só. */
  fun renomear(de: String, para: String) {
    val n = normalizarNome(para)
    if (n.isEmpty() || n == de) return
    salvar(series.value.map { if (it.exercicio == de) it.copy(exercicio = n) else it })
  }

  /**
   * Separa o que é novo do que já está salvo: num mesmo dia, uma série igual
   * (exercício + carga + reps) que já existe não entra de novo. Assim dá para importar
   * o mesmo texto do Discord duas vezes, ou um pedaço que se sobrepõe, sem duplicar.
   */
  fun separarNovas(lidas: List<SerieLida>): Pair<List<SerieLida>, Int> {
    val jaTem = HashMap<String, Int>()
    series.value.forEach { jaTem.merge(chave(it.data, it.exercicio, it.peso, it.reps), 1, Int::plus) }
    val novas = mutableListOf<SerieLida>()
    var repetidas = 0
    for (l in lidas) {
      val k = chave(l.data, l.exercicio, l.peso, l.reps)
      val n = jaTem[k] ?: 0
      if (n > 0) {
        jaTem[k] = n - 1
        repetidas++
      } else novas += l
    }
    return novas to repetidas
  }

  /** Importa de uma vez (ex.: todo o histórico do Discord). Dá para desfazer depois. */
  fun importar(lidas: List<SerieLida>): Importacao {
    val (novas, repetidas) = separarNovas(lidas)
    if (novas.isNotEmpty()) {
      val salvas = adicionar(novas)
      prefs.edit()
          .putLong("imp_de", salvas.first().id)
          .putLong("imp_ate", salvas.last().id)
          .apply()
      desfazivel.value = ultimaImportacao().count()
    }
    return Importacao(novas.size, repetidas, novas.map { it.data }.distinct().size)
  }

  /** Apaga as séries da última importação (as que você editou depois também saem). */
  fun desfazerImportacao(): Int {
    val ids = ultimaImportacao()
    val n = series.value.count { it.id in ids }
    salvar(series.value.filter { it.id !in ids })
    esquecerImportacao()
    return n
  }

  private fun ultimaImportacao(): LongRange {
    val de = prefs.getLong("imp_de", 0)
    val ate = prefs.getLong("imp_ate", -1)
    return if (de <= 0 || ate < de) LongRange.EMPTY
    else if (series.value.none { it.id in de..ate }) LongRange.EMPTY
    else de..ate
  }

  private fun esquecerImportacao() {
    prefs.edit().remove("imp_de").remove("imp_ate").apply()
    desfazivel.value = 0
  }

  /** As séries em JSON (para o backup completo). */
  fun paraJson(): JSONArray = JSONArray().apply {
    series.value.forEach {
      put(JSONObject().put("data", it.data.toString()).put("ex", it.exercicio).put("kg", it.peso)
          .put("reps", it.reps).put("nota", it.nota))
    }
  }

  private fun chave(d: LocalDate, ex: String, peso: Double, reps: Int) = "$d|$ex|${fmtPeso(peso)}|$reps"

  private fun SerieLida.comId(id: Long) = Serie(id, data, exercicio, peso, reps, nota)

  private fun salvar(lista: List<Serie>) {
    val json = JSONArray()
    lista.forEach {
      json.put(JSONObject()
          .put("id", it.id)
          .put("data", it.data.toString())
          .put("ex", it.exercicio)
          .put("kg", it.peso)
          .put("reps", it.reps)
          .put("nota", it.nota)
          .apply { it.momento?.let { m -> put("t", m) } })
    }
    // Grava num arquivo temporário e troca: se o celular desligar no meio, nada se perde.
    val tmp = File(arquivo.parentFile, "logbook.json.tmp")
    tmp.writeText(json.toString())
    if (!tmp.renameTo(arquivo)) {
      arquivo.writeText(json.toString())
      tmp.delete()
    }
    series.value = lista
  }

  private fun carregar(): List<Serie> {
    if (!arquivo.exists()) return emptyList()
    return runCatching { lerArquivo() }.getOrElse {
      // Nunca sobrescreve um arquivo que não deu para ler: guarda uma cópia antes.
      arquivo.copyTo(File(arquivo.parentFile, "logbook-${System.currentTimeMillis()}.ilegivel.json"), overwrite = true)
      emptyList()
    }
  }

  private fun lerArquivo(): List<Serie> {
    val arr = JSONArray(arquivo.readText())
    return (0 until arr.length()).map { i ->
      val o = arr.getJSONObject(i)
      Serie(
          id = o.getLong("id"),
          data = LocalDate.parse(o.getString("data")),
          exercicio = o.getString("ex"),
          peso = o.getDouble("kg"),
          reps = o.getInt("reps"),
          nota = o.optString("nota"),
          momento = if (o.has("t")) o.getLong("t") else null,
      )
    }
  }

  companion object {
    /** Lê as séries de um backup completo. */
    fun seriesDeJson(arr: JSONArray): List<SerieLida> = (0 until arr.length()).map { i ->
      val o = arr.getJSONObject(i)
      SerieLida(LocalDate.parse(o.getString("data")), o.getString("ex"), o.getDouble("kg"), o.getInt("reps"), o.optString("nota"))
    }
  }
}
