package com.martins.logbook

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

/**
 * Tela de aviso do fim do temporizador: aparece por cima de tudo, até na tela bloqueada
 * (aberta pela notificação de "tela cheia" quando o descanso acaba).
 */
class AlarmeActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Mostrar em cima da tela bloqueada e acender a tela.
    if (Build.VERSION.SDK_INT >= 27) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }
    Aparencia.carregar(applicationContext)
    SistemaTemporizador.configurar(applicationContext)
    val fechar = {
      NotificationManagerCompat.from(this).cancel(SistemaTemporizador.NOTIF_ID)
      finish()
    }
    setContent {
      Tema {
        Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
          Column(
              Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(DegradePainel)
                  .border(0.7.dp, DegradeBorda, RoundedCornerShape(32.dp)).padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text("⏱", fontSize = 48.sp)
            Text("Tempo!", color = Destaque, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text("O descanso acabou. Bora para a próxima série!", color = Texto, fontSize = 16.sp,
                modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedButton(onClick = { Temporizador.iniciar(30_000); Temporizador.aberto = false; fechar() }) { Text("+30s") }
              BotaoDegrade(onClick = {
                startActivity(Intent(this@AlarmeActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                fechar()
              }) { Text("Abrir o Logbook") }
            }
            TextButton(onClick = { Temporizador.zerar(); fechar() }, modifier = Modifier.padding(top = 8.dp)) {
              Text("Fechar", color = Cinza)
            }
          }
        }
      }
    }
  }
}
