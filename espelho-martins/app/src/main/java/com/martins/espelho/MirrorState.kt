package com.martins.espelho

import android.content.Context
import com.meta.wearable.dat.core.Wearables
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Qual parte da tela do celular vai para os óculos. */
enum class MirrorMode(val label: String, val description: String) {
  FULL("Tela inteira", "Mostra o celular todo (fica pequeno)"),
  TOP("Metade de cima", "Maior, bom para ler notificações e títulos"),
  BOTTOM("Metade de baixo", "Maior, bom para teclado e mensagens"),
}

/** Estado compartilhado entre a tela do app e o serviço de espelhamento. */
object MirrorState {
  private val _running = MutableStateFlow(false)
  val running: StateFlow<Boolean> = _running.asStateFlow()

  private val _status = MutableStateFlow("Parado")
  val status: StateFlow<String> = _status.asStateFlow()

  private val _fps = MutableStateFlow(0f)
  val fps: StateFlow<Float> = _fps.asStateFlow()

  private val _mode = MutableStateFlow(MirrorMode.FULL)
  val mode: StateFlow<MirrorMode> = _mode.asStateFlow()

  /** Limite de imagens por segundo enviadas aos óculos. */
  private val _maxFps = MutableStateFlow(4)
  val maxFps: StateFlow<Int> = _maxFps.asStateFlow()

  fun setRunning(value: Boolean) { _running.value = value }
  fun setStatus(value: String) { _status.value = value }
  fun setFps(value: Float) { _fps.value = value }
  fun setMode(value: MirrorMode) { _mode.value = value }
  fun setMaxFps(value: Int) { _maxFps.value = value.coerceIn(1, 15) }

  @Volatile private var datInitialized = false

  /** Inicializa o kit da Meta uma única vez (depois das permissões de Bluetooth). */
  fun ensureDatInitialized(context: Context) {
    if (datInitialized) return
    synchronized(this) {
      if (datInitialized) return
      Wearables.initialize(context.applicationContext)
      datInitialized = true
    }
  }
}
