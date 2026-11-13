package com.martins.estudo

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Verde = Color(0xFF81C784)
private val Cinza = Color(0xFF9A9A9A)
private val Fundo = Color(0xFF161616)

class MainActivity : ComponentActivity() {

  private lateinit var store: ProvaStore
  private lateinit var settings: Settings

  private val provas = mutableStateOf<List<Prova>>(emptyList())
  private val aberta = mutableStateOf<Prova?>(null)
  private val carregando = mutableStateOf(false)
  private val erro = mutableStateOf<String?>(null)
  private val registration = MutableStateFlow(RegistrationState.UNAVAILABLE)
  private val kitPronto = mutableStateOf(false)

  private val permissionsLauncher =
      registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) iniciarKit()
      }

  /** Seletor de fotos do Android: abre a galeria, sem pedir permissão. Até 5 fotos. */
  private val escolherFotos =
      registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) resolver(uris)
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    store = ProvaStore(this)
    settings = Settings(this)
    provas.value = store.all()

    setContent {
      MaterialTheme(colorScheme = darkColorScheme(primary = Verde)) {
        val p = aberta.value
        BackHandler(enabled = p != null) { aberta.value = null }
        Column(
            Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(16.dp)
        ) {
          if (p == null) {
            TelaInicial(
                provas = provas.value,
                carregando = carregando.value,
                erro = erro.value,
                apiKeyInicial = settings.apiKey,
                onSalvarChave = { settings.apiKey = it; Toast.makeText(this@MainActivity, "Chave salva neste celular", Toast.LENGTH_SHORT).show() },
                registrationFlow = registration,
                kitPronto = kitPronto.value,
                onConectar = { if (kitPronto.value) Wearables.startRegistration(this@MainActivity) },
                onNova = {
                  erro.value = null
                  escolherFotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onAbrir = { aberta.value = it },
            )
          } else {
            TelaProva(
                prova = p,
                onVoltar = { aberta.value = null },
                onRevisar = { OculosRevisao.comecar(this@MainActivity, p) },
                onPararRevisao = { OculosRevisao.parar("Revisão encerrada") },
                onApagar = {
                  store.delete(p.id)
                  provas.value = store.all()
                  aberta.value = null
                },
            )
          }
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) iniciarKit()
    else permissionsLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
  }

  private fun iniciarKit() {
    if (kitPronto.value) return
    OculosRevisao.iniciarKit(this)
    kitPronto.value = true
    lifecycleScope.launch { Wearables.registrationState.collect { registration.value = it } }
  }

  private fun resolver(fotos: List<Uri>) {
    val chave = settings.apiKey
    if (chave.isBlank()) {
      erro.value = "Primeiro coloque sua chave da API do Claude (abaixo, em Configurações)."
      return
    }
    carregando.value = true
    lifecycleScope.launch {
      try {
        val prova = withContext(Dispatchers.IO) { Resolver.resolver(this@MainActivity, chave, fotos) }
        store.save(prova)
        provas.value = store.all()
        aberta.value = prova
      } catch (e: Exception) {
        erro.value = e.message ?: "Algo deu errado. Tente de novo."
      } finally {
        carregando.value = false
      }
    }
  }
}

// ------------------------------------------------------------------ Telas

@Composable
private fun TelaInicial(
    provas: List<Prova>,
    carregando: Boolean,
    erro: String?,
    apiKeyInicial: String,
    onSalvarChave: (String) -> Unit,
    registrationFlow: MutableStateFlow<RegistrationState>,
    kitPronto: Boolean,
    onConectar: () -> Unit,
    onNova: () -> Unit,
    onAbrir: (Prova) -> Unit,
) {
  val registration by registrationFlow.collectAsState()
  var mostrarConfig by remember { mutableStateOf(apiKeyInicial.isBlank()) }
  var chave by remember { mutableStateOf(apiKeyInicial) }

  LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Text("EstudoMartins", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
      Text("Fotografe suas provas antigas e revise com a resolução explicada.", color = Cinza)
      Spacer(Modifier.height(8.dp))
    }

    item {
      if (carregando) {
        Card(colors = CardDefaults.cardColors(containerColor = Fundo), modifier = Modifier.fillMaxWidth()) {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Spacer(Modifier.size(12.dp))
            Text("Lendo e resolvendo… pode levar 1 ou 2 minutos.", color = Color.White)
          }
        }
      } else {
        Button(onClick = onNova, modifier = Modifier.fillMaxWidth().height(56.dp)) {
          Text("📷  Nova prova: escolher fotos da galeria", fontSize = 16.sp)
        }
      }
      if (erro != null) {
        Spacer(Modifier.height(8.dp))
        Text(erro, color = Color(0xFFE57373))
      }
    }

    if (provas.isNotEmpty()) {
      item { Text("Suas provas", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
      items(provas, key = { it.id }) { p ->
        Card(
            colors = CardDefaults.cardColors(containerColor = Fundo),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().clickable { onAbrir(p) },
        ) {
          Column(Modifier.padding(16.dp)) {
            Text(p.titulo, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("${p.questoes.size} questões", color = Cinza, fontSize = 13.sp)
          }
        }
      }
    }

    item {
      Spacer(Modifier.height(8.dp))
      TextButton(onClick = { mostrarConfig = !mostrarConfig }) {
        Text(if (mostrarConfig) "▾ Configurações" else "▸ Configurações")
      }
      if (mostrarConfig) {
        Card(colors = CardDefaults.cardColors(containerColor = Fundo), modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chave da API do Claude", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Crie em console.anthropic.com → API Keys. Fica salva só neste celular.", color = Cinza, fontSize = 13.sp)
            OutlinedTextField(
                value = chave,
                onValueChange = { chave = it },
                placeholder = { Text("sk-ant-…") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onSalvarChave(chave) }, enabled = chave.isNotBlank()) { Text("Salvar chave") }

            Spacer(Modifier.height(8.dp))
            Text("Óculos (Meta AI)", color = Color.White, fontWeight = FontWeight.SemiBold)
            val conectado = registration == RegistrationState.REGISTERED
            Text(
                when {
                  !kitPronto -> "Aguardando permissão de Bluetooth…"
                  conectado -> "Conectado ✔"
                  else -> "Ainda não conectado"
                },
                color = if (conectado) Verde else Cinza,
            )
            if (!conectado) {
              OutlinedButton(onClick = onConectar, enabled = kitPronto) { Text("Conectar ao Meta AI") }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TelaProva(
    prova: Prova,
    onVoltar: () -> Unit,
    onRevisar: () -> Unit,
    onPararRevisao: () -> Unit,
    onApagar: () -> Unit,
) {
  val ativo by OculosRevisao.ativo.collectAsState()
  val status by OculosRevisao.status.collectAsState()
  var confirmarApagar by remember { mutableStateOf(false) }

  LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      TextButton(onClick = onVoltar) { Text("← Voltar") }
      Text(prova.titulo, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
      Text("${prova.questoes.size} questões · toque em uma para ver a resolução", color = Cinza, fontSize = 13.sp)
      Spacer(Modifier.height(8.dp))
      if (!ativo) {
        Button(onClick = onRevisar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
          Text("🕶️  Revisar nos óculos")
        }
      } else {
        OutlinedButton(onClick = onPararRevisao, modifier = Modifier.fillMaxWidth()) { Text("Parar revisão nos óculos") }
      }
      Text(status, color = if (ativo) Verde else Cinza, fontSize = 13.sp)
    }

    items(prova.questoes) { q -> CartaoQuestao(q) }

    item {
      Spacer(Modifier.height(12.dp))
      if (!confirmarApagar) {
        TextButton(onClick = { confirmarApagar = true }) { Text("Apagar esta prova", color = Color(0xFFE57373)) }
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Apagar mesmo?", color = Color.White)
          TextButton(onClick = onApagar) { Text("Sim, apagar", color = Color(0xFFE57373)) }
          TextButton(onClick = { confirmarApagar = false }) { Text("Cancelar") }
        }
      }
    }
  }
}

@Composable
private fun CartaoQuestao(q: Questao) {
  var aberta by remember { mutableStateOf(false) }
  Card(
      colors = CardDefaults.cardColors(containerColor = Fundo),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().clickable { aberta = !aberta },
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Questão ${q.numero}", color = Verde, fontWeight = FontWeight.SemiBold)
      Text(q.enunciado, color = Color.White)
      if (aberta) {
        Spacer(Modifier.height(4.dp))
        Text("Resposta", color = Cinza, fontSize = 13.sp)
        Text(q.resposta, color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Resolução", color = Cinza, fontSize = 13.sp)
        Text(q.resolucao, color = Color(0xFFDDDDDD))
      } else {
        Text("Toque para ver a resolução", color = Cinza, fontSize = 13.sp)
      }
    }
  }
}
