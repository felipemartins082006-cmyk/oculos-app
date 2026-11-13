package com.martins.estudo

import android.content.Context
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.views.ActionRole
import com.meta.wearable.dat.display.views.ButtonStyle
import com.meta.wearable.dat.display.views.ContentScope
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.IconName
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Modo "Revisar nos óculos": mostra uma questão por vez na lente,
 * como flashcard. Primeiro só a pergunta; ao tocar em "Ver resposta",
 * aparece a resposta e o começo da resolução.
 */
object OculosRevisao {
  private const val TAG = "EstudoMartins"

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val _status = MutableStateFlow("Desligado")
  val status: StateFlow<String> = _status.asStateFlow()

  private val _ativo = MutableStateFlow(false)
  val ativo: StateFlow<Boolean> = _ativo.asStateFlow()

  private var session: DeviceSession? = null
  private var display: Display? = null
  private val jobs = mutableListOf<Job>()

  private var questoes: List<Questao> = emptyList()
  private var indice = 0
  private var mostrandoResposta = false
  private var acertos = 0

  @Volatile private var datIniciado = false

  fun iniciarKit(context: Context) {
    if (datIniciado) return
    synchronized(this) {
      if (datIniciado) return
      Wearables.initialize(context.applicationContext)
      datIniciado = true
    }
  }

  fun comecar(context: Context, prova: Prova) {
    iniciarKit(context)
    if (prova.questoes.isEmpty()) { _status.value = "Esta prova não tem questões"; return }
    if (Wearables.registrationState.value != RegistrationState.REGISTERED) {
      _status.value = "Conecte o app ao Meta AI primeiro"
      return
    }
    parar()
    questoes = prova.questoes
    indice = 0
    acertos = 0
    mostrandoResposta = false
    _ativo.value = true
    _status.value = "Procurando os óculos…"

    Wearables.createSession(AutoDeviceSelector(filter = { it.isDisplayCapable() })).fold(
        onSuccess = { s ->
          session = s
          jobs += scope.launch {
            s.state.collect { state ->
              Log.i(TAG, "Sessão: $state")
              if (state == DeviceSessionState.STARTED && display == null) ligarTela(s)
              if (state == DeviceSessionState.STOPPED && _ativo.value) parar("Os óculos desconectaram")
            }
          }
          jobs += scope.launch {
            s.errors.collect { error -> parar("Erro nos óculos: ${error.description}") }
          }
          s.start()
        },
        onFailure = { error, _ -> parar("Não achei os óculos: ${error.description}") },
    )
  }

  private fun ligarTela(s: DeviceSession) {
    _status.value = "Ligando a tela dos óculos…"
    s.addDisplay().fold(
        onSuccess = { d ->
          display = d
          jobs += scope.launch {
            d.state.collect { state ->
              if (state == DisplayState.STARTED) {
                _status.value = "Revisando nos óculos ✔"
                mostrarAtual()
              }
            }
          }
        },
        onFailure = { error, _ -> parar("Não consegui usar a tela dos óculos: ${error.description}") },
    )
  }

  private fun enviar(conteudo: ContentScope.() -> Unit) {
    val d = display ?: return
    scope.launch {
      d.sendContent(conteudo).onFailure { error, _ ->
        Log.w(TAG, "Falha ao enviar: ${error.description}")
      }
    }
  }

  private fun corta(texto: String, max: Int) =
      if (texto.length <= max) texto else texto.take(max - 1).trimEnd() + "…"

  private fun mostrarAtual() {
    if (indice >= questoes.size) return mostrarFim()
    val q = questoes[indice]
    val posicao = "Questão ${q.numero} · ${indice + 1} de ${questoes.size}"

    if (!mostrandoResposta) {
      enviar {
        flexBox(direction = Direction.COLUMN, gap = 12) {
          flexBox(padding = 24, background = FlexBoxBackground.CARD) {
            text(posicao, style = TextStyle.META, color = TextColor.SECONDARY)
            text(corta(q.enunciado, 320), style = TextStyle.BODY)
          }
          buttonGroup {
            button("Ver resposta", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK,
                onClick = { mostrandoResposta = true; mostrarAtual() }, actionRole = ActionRole.PRIMARY)
            button("Sair", onClick = { parar("Revisão encerrada") })
          }
        }
      }
    } else {
      enviar {
        flexBox(direction = Direction.COLUMN, gap = 12) {
          flexBox(padding = 24, background = FlexBoxBackground.CARD) {
            text(posicao, style = TextStyle.META, color = TextColor.SECONDARY)
            text(corta(q.resposta, 140), style = TextStyle.HEADING)
            text(corta(q.resolucao, 260), style = TextStyle.BODY, color = TextColor.SECONDARY)
          }
          buttonGroup {
            button("Acertei", style = ButtonStyle.PRIMARY, iconName = IconName.CHECKMARK,
                onClick = { acertos++; proxima() }, actionRole = ActionRole.PRIMARY)
            button("Errei", onClick = { proxima() })
          }
        }
      }
    }
  }

  private fun proxima() {
    indice++
    mostrandoResposta = false
    mostrarAtual()
  }

  private fun mostrarFim() {
    enviar {
      flexBox(direction = Direction.COLUMN, gap = 12) {
        flexBox(padding = 24, background = FlexBoxBackground.CARD) {
          text("Fim da revisão", style = TextStyle.HEADING)
          text("Você acertou $acertos de ${questoes.size}", style = TextStyle.BODY)
        }
        buttonGroup {
          button("Recomeçar", style = ButtonStyle.PRIMARY, actionRole = ActionRole.PRIMARY,
              onClick = { indice = 0; acertos = 0; mostrandoResposta = false; mostrarAtual() })
          button("Sair", onClick = { parar("Revisão encerrada") })
        }
      }
    }
  }

  fun parar(mensagem: String = "Desligado") {
    jobs.forEach { it.cancel() }
    jobs.clear()
    session?.let { s ->
      runCatching { s.removeDisplay() }
      runCatching { s.stop() }
    }
    session = null
    display = null
    _ativo.value = false
    _status.value = mensagem
  }
}
