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
import androidx.compose.material3.AlertDialog
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
  /** O pop-up do temporizador está aberto. */
  var aberto by mutableStateOf(false)
  /** Um alarme do sistema vai tocar no fim (então a tela não precisa tocar de novo). */
  var alarmeAgendado = false

  /** Chamado a cada mudança: o app salva o estado e agenda o alarme do sistema (ver SistemaTemporizador). */
  var aoMudar: (() -> Unit)? = null

  private fun mudou() = aoMudar?.invoke()

  fun abrir() { aberto = true }
  fun fechar() { aberto = false }

  /** Escolhe o tempo (sem começar): entre 5 s e 99 min. */
  fun escolher(ms: Long) {
    duracaoMs = ms.coerceIn(5_000, 99 * 60_000L)
    mudou()
  }

  fun iniciar(ms: Long = duracaoMs, agora: Long = System.currentTimeMillis()) {
    duracaoMs = ms
    fimEm = agora + ms
    restantePausado = null
    terminou = false
    mudou()
  }

  fun pausar(agora: Long = System.currentTimeMillis()) {
    fimEm?.let { restantePausado = max(0, it - agora); fimEm = null }
    mudou()
  }

  fun continuar(agora: Long = System.currentTimeMillis()) {
    restantePausado?.let { fimEm = agora + it; restantePausado = null }
    mudou()
  }

  fun mais(ms: Long) {
    fimEm?.let { fimEm = maxOf(it + ms, System.currentTimeMillis() + 1000); duracaoMs = maxOf(duracaoMs + ms, 5_000) }
    restantePausado?.let { restantePausado = maxOf(it + ms, 1000); duracaoMs = maxOf(duracaoMs + ms, 5_000) }
    mudou()
  }

  /** Chegou a zero. */
  fun acabou() {
    fimEm = null
    restantePausado = 0
    terminou = true
    aberto = true
    mudou()
  }

  fun zerar() {
    fimEm = null
    restantePausado = null
    terminou = false
    mudou()
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
      // Se o alarme do sistema está agendado, ele vibra e toca; aqui só mostra o "Tempo!".
      if (!Temporizador.alarmeAgendado) alarme(ctx)
      Temporizador.acabou()
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
  if (dia?.sessaoConcluida == true) dia.tempoTreino()?.takeIf { it > 0 }?.let { return it }
  val t = seriesDoDia.mapNotNull { it.momento }.sorted()
  if (t.size >= 2 && t.last() - t.first() >= 5 * 60_000) return t.last() - t.first()
  return null
}

/** Cartão no alto do Treino de hoje: cronômetro da sessão, descanso desde a última série e temporizador. */
@Composable
fun BarraSessao(diario: Diario, series: List<Serie>, dia: LocalDate) {
  val d = diario.de(dia)
  val agora = agoraAoVivo()
  var confirmandoCancelar by remember { mutableStateOf(false) }
  val doDia = series.filter { it.data == dia }
  val ultima = doDia.mapNotNull { it.momento }.maxOrNull()
  val restanteTimer = Temporizador.restante(agora)
  val tempo = d.tempoTreino(agora) ?: 0L

  Cartao {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Rotulo(
            when {
              d.sessaoRodando -> "Treino em andamento · desde ${horaDe(d.treinoInicio!!)}"
              d.sessaoPausada -> "Cronômetro pausado"
              d.sessaoConcluida -> "Treino concluído"
              else -> "Cronômetro"
            },
        )
        Text(
            fmtDuracao(tempo, sempreHoras = true), color = if (d.sessaoRodando) Texto else Cinza,
            fontSize = 34.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"),
        )
        if (d.sessaoRodando && ultima != null && ultima >= d.treinoInicio!!) {
          Text("Descansando ${fmtDuracao(agora - ultima)} desde a última série", color = Destaque, fontSize = 13.sp)
        }
        if (d.sessaoPausada) {
          Text("Pausado há ${fmtDuracao(agora - d.treinoPausadoEm!!)} · a pausa não conta no tempo", color = Cinza, fontSize = 13.sp)
        }
        if (d.sessaoConcluida) {
          val desc = descansos(doDia)
          Text(
              "Descanso médio ${if (desc.isEmpty()) "—" else fmtDuracao(desc.average().toLong())} · ${doDia.size} séries",
              color = Cinza, fontSize = 13.sp,
          )
        }
      }
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (d.treinoInicio == null) {
          BotaoDegrade(onClick = { diario.iniciarSessao(dia); diario.marcarTreino(dia, horaAgora()) }) {
            Text("⏱ Iniciar treino")
          }
        }
        OutlinedButton(onClick = { Temporizador.abrir() }) {
          Text(if (restanteTimer != null) "⏲ ${fmtDuracao(restanteTimer)}" else "⏲ Timer")
        }
      }
    }

    // Controles do cronômetro: pausar/retomar, concluir, continuar e cancelar (com confirmação).
    if (d.treinoInicio != null) {
      Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
          d.sessaoRodando -> {
            OutlinedButton(onClick = { diario.pausarSessao(dia) }, modifier = Modifier.weight(1f)) { Text("⏸ Pausar") }
            BotaoDegrade(onClick = { diario.finalizarSessao(dia) }, modifier = Modifier.weight(1f)) { Text("✓ Concluir treino") }
          }
          d.sessaoPausada -> {
            OutlinedButton(onClick = { diario.retomarSessao(dia) }, modifier = Modifier.weight(1f)) { Text("▶ Retomar") }
            BotaoDegrade(onClick = { diario.finalizarSessao(dia) }, modifier = Modifier.weight(1f)) { Text("✓ Concluir treino") }
          }
          else -> OutlinedButton(onClick = { diario.continuarSessao(dia) }, modifier = Modifier.weight(1f)) {
            Text("▶ Continuar treino")
          }
        }
      }
      TextButton(onClick = { confirmandoCancelar = true }, modifier = Modifier.align(Alignment.End)) {
        Text(if (d.sessaoConcluida) "Apagar o tempo deste treino" else "Cancelar cronômetro", color = Vermelho, fontSize = 13.sp)
      }
    }
  }

  if (confirmandoCancelar) {
    AlertDialog(
        onDismissRequest = { confirmandoCancelar = false },
        title = { Text(if (d.sessaoConcluida) "Apagar o tempo do treino?" else "Deseja mesmo cancelar o cronômetro?") },
        text = {
          Text(
              "O tempo marcado (${fmtDuracao(tempo, sempreHoras = true)}) será apagado e não entra nas estatísticas. " +
                  "As séries que você anotou continuam salvas.",
          )
        },
        confirmButton = {
          TextButton(onClick = { diario.zerarSessao(dia); confirmandoCancelar = false }) {
            Text(if (d.sessaoConcluida) "Apagar tempo" else "Cancelar cronômetro", color = Vermelho)
          }
        },
        dismissButton = { TextButton(onClick = { confirmandoCancelar = false }) { Text("Voltar") } },
    )
  }
}

