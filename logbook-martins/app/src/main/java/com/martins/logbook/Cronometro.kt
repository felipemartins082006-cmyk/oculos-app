package com.martins.logbook

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.max
import kotlinx.coroutines.delay

/** "1:05:09" ou "42:13" ou "0:07". */
fun fmtDuracao(ms: Long, sempreHoras: Boolean = false): String {
  val total = max(0L, ms) / 1000
  val h = total / 3600
  val m = (total % 3600) / 60
  val s = total % 60
  return if (h > 0 || sempreHoras) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "1h12", "45 min". */
fun fmtMinutos(ms: Long): String {
  val min = ms / 60000
  return if (min >= 60) "${min / 60}h${(min % 60).toString().padStart(2, '0')}" else "$min min"
}

/** A hora de agora, atualizada a cada segundo (para os relógios na tela). */
@Composable
fun agoraAoVivo(): Long {
  val agora by produceState(System.currentTimeMillis()) {
    while (true) {
      delay(1000 - System.currentTimeMillis() % 1000)
      value = System.currentTimeMillis()
    }
  }
  return agora
}

/**
 * Temporizador de descanso (minutos e segundos). Fica fora das telas para continuar contando
 * quando você troca de aba; ao zerar, o celular vibra e toca.
 */
object Temporizador {
  var duracaoMs by mutableLongStateOf(90_000L)
  /** Quando termina (rodando) ou null. */
  var fimEm by mutableStateOf<Long?>(null)
  /** Quanto faltava quando você pausou. */
  var restantePausado by mutableStateOf<Long?>(null)
  /** Acabou de terminar ("Tempo!"). */
  var terminou by mutableStateOf(false)

  fun iniciar(ms: Long, agora: Long = System.currentTimeMillis()) {
    duracaoMs = ms
    fimEm = agora + ms
    restantePausado = null
    terminou = false
  }

  fun pausar(agora: Long = System.currentTimeMillis()) {
    fimEm?.let { restantePausado = max(0, it - agora); fimEm = null }
  }

  fun continuar(agora: Long = System.currentTimeMillis()) {
    restantePausado?.let { fimEm = agora + it; restantePausado = null }
  }

  fun mais(ms: Long) {
    fimEm?.let { fimEm = it + ms; duracaoMs += ms }
    restantePausado?.let { restantePausado = it + ms; duracaoMs += ms }
  }

  fun zerar() {
    fimEm = null
    restantePausado = null
    terminou = false
  }

  fun restante(agora: Long): Long? = fimEm?.let { max(0, it - agora) } ?: restantePausado
}

/** Vibra e toca o som de notificação do celular. */
fun alarme(ctx: Context) {
  runCatching {
    val vib = if (Build.VERSION.SDK_INT >= 31) {
      (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
      @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 450, 200, 450, 200, 700), -1))
  }
  runCatching {
    RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
  }
}

/** Fica ligado o tempo todo (no App): quando o temporizador chega a zero, avisa. */
@Composable
fun VigiaTemporizador() {
  val ctx = LocalContext.current
  val fim = Temporizador.fimEm
  LaunchedEffect(fim) {
    if (fim == null) return@LaunchedEffect
    delay(max(0, fim - System.currentTimeMillis()))
    if (Temporizador.fimEm == fim) {
      Temporizador.fimEm = null
      Temporizador.restantePausado = 0
      Temporizador.terminou = true
      alarme(ctx)
    }
  }
}

/** Descansos entre séries de um dia: intervalos entre 5 s e 15 min (mais que isso é pausa). */
fun descansos(seriesDoDia: List<Serie>): List<Long> {
  val t = seriesDoDia.mapNotNull { it.momento }.sorted()
  return t.zipWithNext { a, b -> b - a }.filter { it in 5_000..15 * 60_000 }
}

/** Duração do treino: pelo cronômetro (iniciar/finalizar) ou, sem ele, da primeira à última série. */
fun duracaoTreino(dia: Dia?, seriesDoDia: List<Serie>): Long? {
  val ini = dia?.treinoInicio
  val fim = dia?.treinoFim
  if (ini != null && fim != null && fim > ini) return fim - ini
  val t = seriesDoDia.mapNotNull { it.momento }.sorted()
  if (t.size >= 2 && t.last() - t.first() >= 5 * 60_000) return t.last() - t.first()
  return null
}

/** Cartão no alto do Treino de hoje: cronômetro da sessão, descanso desde a última série e temporizador. */
@Composable
fun BarraSessao(diario: Diario, series: List<Serie>, dia: LocalDate) {
  val d = diario.de(dia)
  val agora = agoraAoVivo()
  var timerAberto by remember { mutableStateOf(false) }
  val doDia = series.filter { it.data == dia }
  val ultima = doDia.mapNotNull { it.momento }.maxOrNull()
  val rodando = d.treinoInicio != null && d.treinoFim == null
  val restanteTimer = Temporizador.restante(agora)

  Cartao {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Rotulo(
            when {
              rodando -> "Treino em andamento · desde ${horaDe(d.treinoInicio!!)}"
              d.treinoFim != null -> "Treino de hoje"
              else -> "Cronômetro"
            },
        )
        val tempo = when {
          rodando -> agora - d.treinoInicio!!
          d.treinoFim != null -> d.treinoFim - d.treinoInicio!!
          else -> 0L
        }
        Text(
            fmtDuracao(tempo, sempreHoras = true), color = if (rodando) Texto else Cinza,
            fontSize = 34.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"),
        )
        if (rodando && ultima != null && ultima >= d.treinoInicio!!) {
          Text("Descansando ${fmtDuracao(agora - ultima)} desde a última série", color = Destaque, fontSize = 13.sp)
        }
        if (d.treinoFim != null) {
          val desc = descansos(doDia)
          Text(
              "Descanso médio ${if (desc.isEmpty()) "—" else fmtDuracao(desc.average().toLong())} · ${doDia.size} séries",
              color = Cinza, fontSize = 13.sp,
          )
        }
      }
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
          rodando -> OutlinedButton(onClick = { diario.finalizarSessao(dia) }) { Text("Finalizar") }
          d.treinoFim != null -> TextButton(onClick = { diario.alterar(dia) { it.copy(treinoFim = null) } }) { Text("Continuar") }
          else -> BotaoDegrade(onClick = { diario.iniciarSessao(dia); diario.marcarTreino(dia, horaAgora()) }) {
            Text("⏱ Iniciar treino")
          }
        }
        OutlinedButton(onClick = { timerAberto = !timerAberto }) {
          Text(if (restanteTimer != null && !timerAberto) "⏲ ${fmtDuracao(restanteTimer)}" else "⏲ Timer")
        }
      }
    }
    if (timerAberto || Temporizador.terminou) PainelTemporizador(agora)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PainelTemporizador(agora: Long) {
  val restante = Temporizador.restante(agora)
  var min by remember { mutableStateOf("") }
  var seg by remember { mutableStateOf("") }
  Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Rotulo("Temporizador de descanso", Modifier.fillMaxWidth())
    if (restante != null) {
      val frac = if (Temporizador.duracaoMs > 0) restante.toFloat() / Temporizador.duracaoMs else 0f
      Box(Modifier.size(190.dp).padding(top = 10.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(170.dp)) {
          val esp = 10.dp.toPx()
          val tam = Size(size.width - esp, size.height - esp)
          val topo = Offset(esp / 2, esp / 2)
          drawArc(Color(0x1FFFFFFF), 0f, 360f, false, topo, tam, style = Stroke(esp))
          drawArc(
              Brush.sweepGradient(listOf(DestaqueEscuro, Destaque, Color(0xFFFF8A7A), DestaqueEscuro)),
              -90f, 360f * frac, false, topo, tam, style = Stroke(esp, cap = StrokeCap.Round),
          )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
              if (Temporizador.terminou) "Tempo!" else fmtDuracao(restante),
              color = if (Temporizador.terminou) Destaque else Texto,
              fontSize = 38.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"),
          )
          if (Temporizador.restantePausado != null && !Temporizador.terminou) Text("pausado", color = Cinza, fontSize = 12.sp)
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
          Temporizador.terminou -> BotaoDegrade(onClick = { Temporizador.iniciar(Temporizador.duracaoMs) }) { Text("De novo") }
          Temporizador.fimEm != null -> OutlinedButton(onClick = { Temporizador.pausar() }) { Text("Pausar") }
          else -> BotaoDegrade(onClick = { Temporizador.continuar() }) { Text("Continuar") }
        }
        if (!Temporizador.terminou) OutlinedButton(onClick = { Temporizador.mais(15_000) }) { Text("+15s") }
        TextButton(onClick = { Temporizador.zerar() }) { Text("Zerar", color = Cinza) }
      }
    }
    if (restante == null || Temporizador.terminou) {
      FlowRow(
          Modifier.fillMaxWidth().padding(top = 10.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf(45, 60, 90, 120, 150, 180, 300).forEach { s ->
          FilterChip(selected = false, onClick = { Temporizador.iniciar(s * 1000L) }, label = { Text(fmtDuracao(s * 1000L)) })
        }
      }
      Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            min, { min = it.filter(Char::isDigit).take(3) }, modifier = Modifier.width(88.dp),
            label = { Text("min") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text(" : ", color = Cinza, fontSize = 22.sp)
        OutlinedTextField(
            seg, { seg = it.filter(Char::isDigit).take(2) }, modifier = Modifier.width(88.dp),
            label = { Text("seg") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.width(10.dp))
        val total = ((min.toIntOrNull() ?: 0) * 60 + (seg.toIntOrNull() ?: 0).coerceAtMost(59)) * 1000L
        BotaoDegrade(onClick = { Temporizador.iniciar(total) }, enabled = total > 0, modifier = Modifier.height(56.dp)) {
          Text("Iniciar")
        }
      }
    }
  }
}
