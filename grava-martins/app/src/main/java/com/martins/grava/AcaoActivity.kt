package com.martins.grava

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Tela invisível aberta pelos botões Print/Gravar da notificação.
 * Abrir uma tela faz o Android fechar a cortina de notificações; ela some na hora
 * e o serviço espera um pouco para a cortina não sair no print ou no vídeo.
 */
class AcaoActivity : Activity() {
  companion object {
    const val EXTRA_ACAO = "acao"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val acao = intent.getStringExtra(EXTRA_ACAO)
    if (acao != null && GravadorService.estado != GravadorService.Estado.DESLIGADO) {
      try {
        startService(
            Intent(this, GravadorService::class.java)
                .setAction(acao)
                .putExtra(GravadorService.EXTRA_ESPERA, 700L))
      } catch (_: Exception) {}
    }
    finish()
    if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
  }
}
