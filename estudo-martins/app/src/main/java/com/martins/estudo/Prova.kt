package com.martins.estudo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Uma questão lida da foto, com a resposta e a resolução explicada. */
data class Questao(
    val numero: String,
    val enunciado: String,
    val resposta: String,
    val resolucao: String,
)

/** Uma prova (ou lista) já resolvida e salva no celular. */
data class Prova(
    val id: Long,
    val titulo: String,
    val questoes: List<Questao>,
) {
  fun toJson(): JSONObject = JSONObject()
      .put("id", id)
      .put("titulo", titulo)
      .put("questoes", JSONArray().apply {
        questoes.forEach {
          put(JSONObject()
              .put("numero", it.numero)
              .put("enunciado", it.enunciado)
              .put("resposta", it.resposta)
              .put("resolucao", it.resolucao))
        }
      })

  companion object {
    /** Lê o JSON devolvido pelo Claude (ou salvo no celular). */
    fun fromJson(json: JSONObject, id: Long = json.optLong("id", System.currentTimeMillis())): Prova {
      val arr = json.optJSONArray("questoes") ?: JSONArray()
      val qs = (0 until arr.length()).map { i ->
        val q = arr.getJSONObject(i)
        Questao(
            numero = q.optString("numero", "${i + 1}"),
            enunciado = q.optString("enunciado"),
            resposta = q.optString("resposta"),
            resolucao = q.optString("resolucao"),
        )
      }
      return Prova(id, json.optString("titulo", "Prova"), qs)
    }
  }
}

/** Guarda as provas resolvidas num arquivo dentro do app (só no seu celular). */
class ProvaStore(context: Context) {
  private val file = File(context.filesDir, "provas.json")

  fun all(): List<Prova> = runCatching {
    if (!file.exists()) return emptyList()
    val arr = JSONArray(file.readText())
    (0 until arr.length()).map { Prova.fromJson(arr.getJSONObject(it)) }
  }.getOrDefault(emptyList())

  fun save(prova: Prova) {
    val list = listOf(prova) + all().filter { it.id != prova.id }
    file.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
  }

  fun delete(id: Long) {
    val list = all().filter { it.id != id }
    file.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
  }
}

/** Guarda a chave da API do Claude só neste celular (nunca vai para o GitHub). */
class Settings(context: Context) {
  private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
  var apiKey: String
    get() = prefs.getString("api_key", "") ?: ""
    set(v) { prefs.edit().putString("api_key", v.trim()).apply() }
}
