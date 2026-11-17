package com.martins.logbook

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Personalizar o app: tema (paleta), foto de fundo da galeria, escurecer, desfoque e transparência. */
@Composable
fun TelaAparencia(onVoltar: () -> Unit) {
  val ctx = LocalContext.current
  BackHandler(onBack = onVoltar)
  val escolherFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null && !Aparencia.definirFoto(ctx, uri)) Toast.makeText(ctx, "Não consegui abrir essa foto", Toast.LENGTH_LONG).show()
  }
  fun mudou() = Aparencia.salvar(ctx)

  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onVoltar) { Text("‹", fontSize = 28.sp, color = Cinza) }
        Column {
          Rotulo("Personalizar")
          Text("Aparência", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Texto)
        }
      }
    }

    // Prévia
    item {
      Cartao {
        Rotulo("Prévia")
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("Bench Press", color = Texto, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("94 kg × 4 · recorde", color = Destaque, fontSize = 14.sp)
          }
          BotaoDegrade(onClick = {}) { Text("+ série") }
        }
      }
    }

    // Temas prontos
    item {
      Cartao {
        Rotulo("Tema")
        PALETAS.chunked(2).forEach { linha ->
          Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            linha.forEach { p ->
              val sel = p.id == Aparencia.paletaId && !(Aparencia.usarCoresDaFoto && Aparencia.fundoImagem != null)
              Column(
                  Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                      .background(Brush.linearGradient(listOf(p.fundoTopo, p.fundo)))
                      .border(if (sel) 2.dp else 0.7.dp, if (sel) p.destaque else Borda, RoundedCornerShape(22.dp))
                      .clickable { Aparencia.paletaId = p.id; Aparencia.usarCoresDaFoto = false; mudou() }
                      .semantics { contentDescription = "Tema ${p.nome}" }
                      .padding(12.dp),
              ) {
                Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(p.coral, p.destaque, p.escuro))))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                  Text(p.nome, color = Texto, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                  if (sel) Text("✓", color = p.destaque, fontWeight = FontWeight.Bold)
                }
              }
            }
            if (linha.size == 1) Spacer(Modifier.weight(1f))
          }
        }
      }
    }

    // Foto de fundo
    item {
      Cartao {
        Rotulo("Foto de fundo")
        val img = Aparencia.fundoImagem
        if (img == null) {
          Text("Escolha uma foto da galeria para ficar atrás de todo o app.", color = Cinza, fontSize = 13.sp,
              modifier = Modifier.padding(top = 6.dp))
          BotaoDegrade(
              onClick = { escolherFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
              modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
          ) { Text("🖼  Escolher da galeria") }
        } else {
          Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(img, contentDescription = "Sua foto de fundo", contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedButton(onClick = { escolherFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("Trocar foto")
              }
              TextButton(onClick = { Aparencia.removerFoto(ctx) }) { Text("Remover foto", color = Vermelho) }
            }
          }
          Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Usar as cores da foto", color = Texto, fontSize = 14.sp)
              Text("o app pega a cor principal da imagem", color = Cinza, fontSize = 12.sp)
            }
            Aparencia.paletaFoto?.let { pf ->
              Box(Modifier.size(22.dp).clip(CircleShape).background(Brush.linearGradient(listOf(pf.coral, pf.destaque, pf.escuro))))
              Spacer(Modifier.width(8.dp))
            }
            Switch(
                checked = Aparencia.usarCoresDaFoto,
                onCheckedChange = { Aparencia.usarCoresDaFoto = it; mudou() },
                modifier = Modifier.semantics { contentDescription = "Usar as cores da foto" },
            )
          }
          Ajuste("Escurecer a foto", Aparencia.escurecer, 0.15f..0.92f, "${(Aparencia.escurecer * 100).toInt()}%") {
            Aparencia.escurecer = it; mudou()
          }
          if (Build.VERSION.SDK_INT >= 31) {
            Ajuste("Desfoque", Aparencia.desfoque, 0f..24f, "${Aparencia.desfoque.toInt()}") { Aparencia.desfoque = it; mudou() }
          }
        }
      }
    }

    // Transparência dos cartões
    item {
      Cartao {
        Rotulo("Cartões")
        Ajuste("Opacidade do vidro", Aparencia.vidro, 0.03f..0.35f, "${(Aparencia.vidro * 100).toInt()}%") {
          Aparencia.vidro = it; mudou()
        }
        Text("Menos opaco deixa ver mais o fundo; mais opaco deixa o texto mais firme.", color = Cinza, fontSize = 12.sp)
      }
    }

    item {
      TextButton(onClick = { Aparencia.padrao(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao padrão (Rubi)", color = Cinza) }
    }
  }
}

@Composable
private fun Ajuste(titulo: String, valor: Float, faixa: ClosedFloatingPointRange<Float>, texto: String, onMudar: (Float) -> Unit) {
  Column(Modifier.padding(top = 12.dp)) {
    Row {
      Text(titulo, color = Texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
      Text(texto, color = Destaque, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value = valor, onValueChange = onMudar, valueRange = faixa,
        colors = SliderDefaults.colors(thumbColor = Destaque, activeTrackColor = Destaque, inactiveTrackColor = Superficie2),
        modifier = Modifier.semantics { contentDescription = titulo },
    )
  }
}
