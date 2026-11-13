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

  /** Na primeira vez que o app abre, já entra com o logbook que você mandou. */
  fun carregarInicialSePreciso() {
    if (prefs.getBoolean("inicial_importado", false)) return
    val texto = context.assets.open("logbook_inicial.txt").bufferedReader().use { it.readText() }
    importar(Leitor.ler(texto, LocalDate.now()).series)
    prefs.edit().putBoolean("inicial_importado", true).apply()
  }

  fun adicionar(lidas: List<SerieLida>): List<Serie> {
    val novas = lidas.map { it.comId(proximoId++) }
    salvar(series.value + novas)
    return novas
  }

  fun atualizar(s: Serie) = salvar(series.value.map { if (it.id == s.id) s else it })

  fun apagar(id: Long) = salvar(series.value.filter { it.id != id })

  fun apagarTudo() = salvar(emptyList())

  /** Renomeia um exercício; se o nome novo já existe, os dois viram um só. */
  fun renomear(de: String, para: String) {
    val n = normalizarNome(para)
    if (n.isEmpty() || n == de) return
    salvar(series.value.map { if (it.exercicio == de) it.copy(exercicio = n) else it })
  }

  /**
   * Junta séries importadas sem duplicar: num mesmo dia, uma série igual
   * (exercício + carga + reps) que já está salva não entra de novo. Assim dá para
   * importar o mesmo texto do Discord duas vezes, ou um pedaço que se sobrepõe, sem bagunçar.
   */
  fun importar(lidas: List<SerieLida>): Importacao {
    val jaTem = HashMap<String, Int>()
    series.value.forEach { jaTem.merge(chave(it.data, it.exercicio, it.peso, it.reps), 1, Int::plus) }
    val entram = mutableListOf<SerieLida>()
    var repetidas = 0
    for (l in lidas) {
      val k = chave(l.data, l.exercicio, l.peso, l.reps)
      val n = jaTem[k] ?: 0
      if (n > 0) {
        jaTem[k] = n - 1
        repetidas++
      } else entram += l
    }
    if (entram.isNotEmpty()) adicionar(entram)
    return Importacao(entram.size, repetidas, entram.map { it.data }.distinct().size)
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
          .put("nota", it.nota))
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
      )
    }
  }
}
