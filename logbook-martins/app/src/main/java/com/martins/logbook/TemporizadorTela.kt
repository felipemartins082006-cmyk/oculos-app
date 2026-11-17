package com.martins.logbook

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Anel do temporizador (quanto falta do total). */
@Composable
private fun Anel(frac: Float, tamanho: Dp, espessura: Dp) {
  Canvas(Modifier.size(tamanho)) {
    val esp = espessura.toPx()
    val tam = Size(size.width - esp, size.height - esp)
    val topo = Offset(esp / 2, esp / 2)
    drawArc(Color(0x1FFFFFFF), 0f, 360f, false, topo, tam, style = Stroke(esp))
    drawArc(
        Brush.sweepGradient(listOf(DestaqueEscuro, Destaque, Coral, DestaqueEscuro)),
        -90f, 360f * frac.coerceIn(0f, 1f), false, topo, tam, style = Stroke(esp, cap = StrokeCap.Round),
    )
  }
}

/**
 * Bolha flutuante (em todas as abas) enquanto o temporizador roda, está pausado ou acabou.
 * Toque para abrir o pop-up.
 */
@Composable
fun BoxScope.BolhaTemporizador() {
  val agora = agoraAoVivo()
  val restante = Temporizador.restante(agora)
  if (Temporizador.aberto || (restante == null && !Temporizador.terminou)) return
  val frac = if (Temporizador.duracaoMs > 0) (restante ?: 0).toFloat() / Temporizador.duracaoMs else 0f
  Row(
      Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 96.dp)
          .clip(RoundedCornerShape(50))
          .background(if (Temporizador.terminou) DegradeDestaque else DegradePainel)
          .border(0.7.dp, DegradeBorda, RoundedCornerShape(50))
          .clickable { Temporizador.abrir() }
          .semantics { contentDescription = "Abrir temporizador" }
          .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Anel(frac, 34.dp, 4.dp)
      Text("⏲", fontSize = 13.sp, color = Texto)
    }
    Spacer(Modifier.width(8.dp))
    Text(
        when {
          Temporizador.terminou -> "Tempo!"
          Temporizador.restantePausado != null -> "${fmtDuracao(restante ?: 0)} ⏸"
          else -> fmtDuracao(restante ?: 0)
        },
        color = if (Temporizador.terminou) SobreDestaque else Texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"),
    )
  }
}

/**
 * Pop-up do temporizador: aparece por cima de tudo e continua contando mesmo fechado
 * (a bolha mostra o tempo; o celular avisa no fim, até com o app fechado).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.PopupTemporizador() {
  if (!Temporizador.aberto) return
  val ctx = LocalContext.current
  val agora = agoraAoVivo()
  val restante = Temporizador.restante(agora)
  BackHandler { Temporizador.fechar() }

  // Na primeira vez, pede permissão para avisar com notificação (Android 13+).
  val pedirNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  fun comecar(ms: Long) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      val p = ctx.getSharedPreferences("temporizador", 0)
      if (!p.getBoolean("pediuNotificacao", false)) {
        p.edit().putBoolean("pediuNotificacao", true).apply()
        pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
    Temporizador.iniciar(ms)
  }

  // Fundo escurecido: tocar fora fecha (o temporizador continua).
  Box(
      Modifier.fillMaxSize().background(Color(0x99000000))
          .clickable(remember { MutableInteractionSource() }, indication = null) { Temporizador.fechar() },
  )
  Column(
      Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)
          .clip(RoundedCornerShape(32.dp))
          .background(DegradePainel)
          .border(0.7.dp, DegradeBorda, RoundedCornerShape(32.dp))
          .clickable(remember { MutableInteractionSource() }, indication = null) { }
          .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Rotulo("Temporizador de descanso", Modifier.weight(1f))
      TextButton(onClick = { Temporizador.fechar() }) { Text("Fechar", color = Cinza) }
    }

    if (restante != null || Temporizador.terminou) {
      // Rodando, pausado ou acabou.
      val frac = if (Temporizador.duracaoMs > 0) (restante ?: 0).toFloat() / Temporizador.duracaoMs else 0f
      Box(Modifier.padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Anel(frac, 200.dp, 12.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
              if (Temporizador.terminou) "Tempo!" else fmtDuracao(restante ?: 0),
              color = if (Temporizador.terminou) Destaque else Texto,
              fontSize = 44.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"),
          )
          Text(
              when {
                Temporizador.terminou -> "descanso concluído"
                Temporizador.restantePausado != null -> "pausado"
                else -> "de ${fmtDuracao(Temporizador.duracaoMs)}"
              },
              color = Cinza, fontSize = 13.sp,
          )
        }
      }
      Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
          Temporizador.terminou -> BotaoDegrade(onClick = { comecar(Temporizador.duracaoMs) }) { Text("↻ De novo") }
          Temporizador.fimEm != null -> OutlinedButton(onClick = { Temporizador.pausar() }) { Text("⏸ Pausar") }
          else -> BotaoDegrade(onClick = { Temporizador.continuar() }) { Text("▶ Continuar") }
        }
        if (!Temporizador.terminou) {
          OutlinedButton(onClick = { Temporizador.mais(-15_000) }) { Text("−15s") }
          OutlinedButton(onClick = { Temporizador.mais(15_000) }) { Text("+15s") }
        }
      }
      TextButton(onClick = { Temporizador.zerar() }) { Text(if (Temporizador.terminou) "Escolher outro tempo" else "Zerar", color = Cinza) }
    } else {
      // Escolher o tempo: − / + grandes (minutos e segundos) e atalhos.
      val total = Temporizador.duracaoMs
      val min = total / 60_000
      val seg = (total % 60_000) / 1000
      Text(
          fmtDuracao(total), color = Texto, fontSize = 56.sp, fontWeight = FontWeight.Bold,
          style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.padding(top = 4.dp),
      )
      Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Ajuste("Minutos", "$min min",
            onMenos = { Temporizador.escolher(total - 60_000) }, onMais = { Temporizador.escolher(total + 60_000) })
        Ajuste("Segundos", "$seg s",
            onMenos = { Temporizador.escolher(total - 5_000) }, onMais = { Temporizador.escolher(total + 5_000) })
      }
      FlowRow(
          Modifier.fillMaxWidth().padding(top = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
      ) {
        listOf(45, 60, 90, 120, 150, 180, 300).forEach { s ->
          FilterChip(selected = total == s * 1000L, onClick = { comecar(s * 1000L) }, label = { Text(fmtDuracao(s * 1000L)) })
        }
      }
      BotaoDegrade(onClick = { comecar(total) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("▶  Iniciar ${fmtDuracao(total)}")
      }
    }
    Text(
        "Pode fechar: o tempo continua (bolha na tela e notificação), e o celular vibra e toca no fim, mesmo com o app fechado.",
        color = Cinza, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp),
    )
  }
}

@Composable
private fun Ajuste(titulo: String, valor: String, onMenos: () -> Unit, onMais: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(titulo, color = Cinza, fontSize = 12.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
      BotaoRedondo("−", "$titulo menos", onMenos)
      Text(valor, color = Texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center)
      BotaoRedondo("+", "$titulo mais", onMais)
    }
  }
}

@Composable
private fun BotaoRedondo(simbolo: String, descricao: String, onClick: () -> Unit) {
  Box(
      Modifier.size(46.dp).clip(CircleShape).background(Superficie2).border(0.7.dp, Borda, CircleShape)
          .clickable(onClick = onClick).semantics { contentDescription = descricao },
      contentAlignment = Alignment.Center,
  ) { Text(simbolo, color = Texto, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}
