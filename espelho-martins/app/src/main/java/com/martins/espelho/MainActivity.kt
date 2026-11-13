package com.martins.espelho

import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.RegistrationState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  /** Estado de conexão com o app Meta AI (fica UNAVAILABLE até inicializar o kit). */
  private val registration = MutableStateFlow(RegistrationState.UNAVAILABLE)
  private val datReady = mutableStateOf(false)

  private val permissionsLauncher =
      registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val bluetoothOk = results[Manifest.permission.BLUETOOTH_CONNECT] ?: hasBluetoothPermission()
        if (bluetoothOk) initDat()
        else Toast.makeText(this, "Preciso da permissão de Bluetooth para falar com os óculos", Toast.LENGTH_LONG).show()
      }

  private val captureLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
          startMirrorService(result.resultCode, data)
        } else {
          Toast.makeText(this, "Sem permissão para capturar a tela", Toast.LENGTH_SHORT).show()
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF4FC3F7))) {
        MainScreen(
            registrationFlow = registration,
            datReady = datReady.value,
            onConnect = { if (datReady.value) Wearables.startRegistration(this) },
            onDisconnect = { if (datReady.value) Wearables.startUnregistration(this) },
            onStart = ::requestScreenCapture,
            onStop = { stopMirrorService() },
        )
      }
    }
  }

  override fun onStart() {
    super.onStart()
    val perms = buildList {
      add(Manifest.permission.BLUETOOTH_CONNECT)
      if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    if (perms.isEmpty()) initDat() else permissionsLauncher.launch(perms.toTypedArray())
  }

  private fun hasBluetoothPermission() =
      checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

  private fun initDat() {
    if (datReady.value) return
    MirrorState.ensureDatInitialized(this)
    datReady.value = true
    // Acompanha o estado de registro com o app Meta AI.
    lifecycleScope.launch {
      Wearables.registrationState.collect { registration.value = it }
    }
  }

  private fun requestScreenCapture() {
    if (registration.value != RegistrationState.REGISTERED) {
      Toast.makeText(this, "Primeiro toque em \"Conectar ao Meta AI\"", Toast.LENGTH_LONG).show()
      return
    }
    val mpm = getSystemService(MediaProjectionManager::class.java)
    captureLauncher.launch(mpm.createScreenCaptureIntent())
  }
}

@Composable
private fun MainScreen(
    registrationFlow: MutableStateFlow<RegistrationState>,
    datReady: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
  val registration by registrationFlow.collectAsState()
  val running by MirrorState.running.collectAsState()
  val status by MirrorState.status.collectAsState()
  val fps by MirrorState.fps.collectAsState()
  val mode by MirrorState.mode.collectAsState()
  val maxFps by MirrorState.maxFps.collectAsState()
  val registered = registration == RegistrationState.REGISTERED

  Column(
      modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
          .safeDrawingPadding()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("EspelhoMartins", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Text("Mostra a tela do celular no seu Meta Ray-Ban Display.", color = Color(0xFFB0B0B0))

    // Passo 1: conectar
    StepCard(number = 1, title = "Conectar ao Meta AI") {
      Text(
          when {
            !datReady -> "Aguardando permissão de Bluetooth…"
            registered -> "Conectado ✔"
            registration == RegistrationState.REGISTERING -> "Conectando… confirme no app Meta AI"
            else -> "Ainda não conectado"
          },
          color = if (registered) Color(0xFF81C784) else Color(0xFFB0B0B0),
      )
      Spacer(Modifier.height(8.dp))
      if (!registered) {
        Button(onClick = onConnect, enabled = datReady, modifier = Modifier.fillMaxWidth()) {
          Text("Conectar ao Meta AI")
        }
      } else {
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
          Text("Desconectar")
        }
      }
    }

    // Passo 2: o que mostrar
    StepCard(number = 2, title = "O que mostrar nos óculos") {
      MirrorMode.entries.forEach { m ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = m == mode, onClick = { MirrorState.setMode(m) })
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = m == mode, onClick = { MirrorState.setMode(m) })
          Column {
            Text(m.label, color = Color.White)
            Text(m.description, color = Color(0xFF9A9A9A), fontSize = 13.sp)
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      Text("Fluidez: até $maxFps imagens por segundo", color = Color.White)
      Slider(
          value = maxFps.toFloat(),
          onValueChange = { MirrorState.setMaxFps(it.toInt()) },
          valueRange = 1f..15f,
          steps = 13,
      )
      Text("Mais fluidez gasta mais bateria. Se travar, diminua.", color = Color(0xFF9A9A9A), fontSize = 13.sp)
    }

    // Passo 3: iniciar
    StepCard(number = 3, title = "Espelhar") {
      Text(status, color = if (running) Color(0xFF81C784) else Color(0xFFB0B0B0))
      if (running && fps > 0f) {
        Text("%.1f imagens por segundo".format(fps), color = Color(0xFF9A9A9A), fontSize = 13.sp)
      }
      Spacer(Modifier.height(8.dp))
      if (!running) {
        Button(onClick = onStart, enabled = registered, modifier = Modifier.fillMaxWidth()) {
          Text("Iniciar espelhamento")
        }
        Text(
            "O Android vai perguntar se pode compartilhar a tela: escolha \"Tela inteira\".",
            color = Color(0xFF9A9A9A), fontSize = 13.sp,
        )
      } else {
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Parar") }
      }
    }
  }
}

@Composable
private fun StepCard(number: Int, title: String, content: @Composable () -> Unit) {
  Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
      modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp)) {
      Text("$number. $title", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
      Spacer(Modifier.height(8.dp))
      content()
    }
  }
}
