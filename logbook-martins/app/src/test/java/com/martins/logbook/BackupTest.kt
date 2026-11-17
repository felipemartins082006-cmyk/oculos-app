package com.martins.logbook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupTest {
  private val ctx = ApplicationProvider.getApplicationContext<Context>()
  private val hoje = LocalDate.of(2026, 11, 17)

  @Test fun backupCompletoIdaEVolta() {
    val lb = Logbook(ctx)
    val diario = Diario(ctx)
    val figuras = Figuras(ctx)
    lb.adicionar(Leitor.ler("squat 160kg x 4", hoje).series, momento = 1_763_000_000_000)
    lb.adicionar(Leitor.ler("bench press 90kg x 5 (1 RIR)", hoje).series)
    diario.pesar(hoje, "07:00", 82.4, TipoPesagem.JEJUM)
    diario.alterar(hoje) { it.copy(notaDia10 = 8, sonoHoras = 7.5, treinoInicio = 1L, treinoFim = 3_600_001L, descanso = false) }
    // Uma "foto" escolhida para um exercício.
    val pasta = File(ctx.filesDir, "fotos").apply { mkdirs() }
    File(pasta, "abc.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
    ctx.getSharedPreferences("figuras", Context.MODE_PRIVATE).edit().putString("farmer walk", "foto:abc.jpg").commit()

    val json = Backup.gerar(lb, diario, Figuras(ctx)).toString()
    assertTrue(Backup.ehBackup(json))

    // Apaga tudo e restaura.
    lb.apagarTudo(); diario.apagarTudo(); File(pasta, "abc.jpg").delete()
    ctx.getSharedPreferences("figuras", Context.MODE_PRIVATE).edit().clear().commit()
    val c = Backup.ler(json)!!
    assertEquals(3, c.versao)
    val res = Backup.restaurar(c, Logbook(ctx), Diario(ctx), Figuras(ctx))
    assertEquals(2, res.adicionadas)

    val series = Logbook(ctx).series.value
    assertEquals(1_763_000_000_000, series.first { it.exercicio == "squat" }.momento)
    assertEquals("1 RIR", series.first { it.exercicio == "bench press" }.nota)
    val d = Diario(ctx).de(hoje)
    assertEquals(82.4, d.pesagens.single().kg, 0.0)
    assertEquals(8, d.notaDia10)
    assertEquals(false, d.descanso)
    assertEquals(3_600_000L, d.tempoTreino())
    assertTrue(File(pasta, "abc.jpg").readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
    assertTrue(Figuras(ctx).de("farmer walk") is Figura.Foto)
    // Restaurar de novo não duplica as séries.
    assertEquals(0, Backup.restaurar(c, Logbook(ctx), Diario(ctx), Figuras(ctx)).adicionadas)
  }

  @Test fun backupDeVersaoAntigaEDoFuturo() {
    val v2 = """{"logbook":2,"series":[{"data":"2025-06-07","ex":"pull ups","kg":7.5,"reps":7,"nota":""}],
      "diario":{"2025-06-07":{"sonoH":7.5}}}"""
    val c2 = Backup.ler(v2)!!
    assertEquals(2, c2.versao)
    assertEquals(7.5, c2.series.single().peso, 0.0)
    // Uma versão futura com campos que este app não conhece: lê o que entende e ignora o resto.
    val v9 = """{"logbook":9,"novidade":{"x":1},"series":[{"data":"2027-01-02","ex":"squat","kg":200,"reps":3,"t":5,"coisaNova":true},
      {"quebrado":true}],"diario":{"2027-01-02":{"humor":4,"campoNovo":[1,2]}}}"""
    val c9 = Backup.ler(v9)!!
    assertEquals(1, c9.series.size)
    assertEquals(5L, c9.series.single().momento)
    Backup.restaurar(c9, Logbook(ctx), Diario(ctx), null)
    assertEquals(4, Diario(ctx).de(LocalDate.of(2027, 1, 2)).humor)
  }

  @Test fun planilhasDeQualquerApp() {
    // A planilha que o próprio app exporta volta igual.
    val lb = Logbook(ctx)
    lb.adicionar(Leitor.ler("21/7/25\nbench press -> 94kg x 4 reps (1 RIR)\npull ups -> bw x 10", hoje).series)
    val csv = Backup.csv(lb.series.value)
    val r = Leitor.ler(csv, hoje)
    assertEquals("Planilha (CSV)", r.formato)
    assertEquals(lb.series.value.map { Triple(it.exercicio, it.peso, it.reps) }, r.series.map { Triple(it.exercicio, it.peso, it.reps) })
    assertEquals(LocalDate.of(2025, 7, 21), r.series.first().data)
    assertEquals("1 RIR", r.series.first().nota)

    // Estilo Strong (vírgulas, aspas, data com hora).
    val strong = "Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes\n" +
        "2025-06-07 15:56:00,\"Push\",1h,\"Bench Press (Barbell)\",1,80,8,0,0,\"\"\n" +
        "2025-06-07 15:56:00,\"Push\",1h,\"Bench Press (Barbell)\",2,82.5,6,0,0,\"\"\n"
    val rs = Leitor.ler(strong, hoje)
    assertEquals(listOf(80.0, 82.5), rs.series.map { it.peso })
    assertEquals("bench press barbell", rs.series.first().exercicio)
    assertEquals(LocalDate.of(2025, 6, 7), rs.series.first().data)

    // Ponto e vírgula, em português.
    val pt = "Data;Exercício;Carga;Repetições\n07/06/2025;Agachamento;100,5;8\n"
    val rp = Leitor.ler(pt, hoje)
    assertEquals(100.5, rp.series.single().peso, 0.0)
    assertEquals("agachamento", rp.series.single().exercicio)
  }

  @Test fun jsonDeQualquerApp() {
    // Estilo Hevy: treino → exercícios → séries.
    val hevy = """{"workouts":[{"title":"Leg day","start_time":"2025-06-07T15:56:00Z","exercises":[
      {"title":"Squat (Barbell)","sets":[{"weight_kg":100,"reps":5},{"weight_kg":105,"reps":3}]},
      {"title":"Leg Extension","sets":[{"weight_kg":60,"reps":12}]}]}]}"""
    val r = Leitor.ler(hevy, hoje)
    assertEquals("JSON", r.formato)
    assertEquals(3, r.series.size)
    assertEquals(listOf("squat barbell", "squat barbell", "leg extension"), r.series.map { it.exercicio })
    assertEquals(LocalDate.of(2025, 6, 7), r.series.first().data)
    // Lista simples.
    val lista = """[{"date":"2025-06-08","exercise":"deadlift","weight":140,"reps":4}]"""
    assertEquals(140.0, Leitor.ler(lista, hoje).series.single().peso, 0.0)
    assertNotNull(Leitor.ler(lista, hoje))
  }
}
