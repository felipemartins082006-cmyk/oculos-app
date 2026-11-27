package com.martins.grava

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.Formatter
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

  private lateinit var config: Config
  private lateinit var painelSessao: View
  private lateinit var txtSessao: TextView
  private lateinit var txtSessaoDica: TextView
  private lateinit var btnEncerrar: Button
  private lateinit var swMic: Switch
  private lateinit var swBolha: Switch
  private lateinit var lista: LinearLayout
  private lateinit var txtVazio: TextView

  private val main = Handler(Looper.getMainLooper())
  private val fundo = Executors.newSingleThreadExecutor()
  private var modoPedido = GravadorService.MODO_PRINTS
  private var geracaoLista = 0
  private var continuarDepoisDaBolha = false

  private val ouvinte: () -> Unit = {
    mostrarSessao()
    carregarLista()
  }

  private val relogio =
      object : Runnable {
        override fun run() {
          mostrarSessao()
          if (GravadorService.estado == GravadorService.Estado.GRAVANDO) main.postDelayed(this, 1000)
        }
      }

  private val pedirPermissoes =
      registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == false) {
          config.microfone = false
          swMic.isChecked = false
          toast("Sem permissão do microfone: o vídeo vai ser gravado sem som.")
        }
        pedirCaptura()
      }

  private val pedirTela =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val dados = r.data
        if (r.resultCode == RESULT_OK && dados != null) {
          startForegroundService(
              Intent(this, GravadorService::class.java)
                  .setAction(GravadorService.ACAO_INICIAR)
                  .putExtra(GravadorService.EXTRA_CODIGO, r.resultCode)
                  .putExtra(GravadorService.EXTRA_DADOS, dados)
                  .putExtra(GravadorService.EXTRA_MODO, modoPedido))
          // Vai para trás para você usar o celular normalmente
          main.postDelayed({ moveTaskToBack(true) }, 250)
        } else {
          toast("Sem essa permissão o app não consegue ver a tela.")
        }
      }

  private val pedirBolha =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val ok = Settings.canDrawOverlays(this)
        config.bolha = ok
        swBolha.isChecked = ok
        if (!ok) toast("Sem o botão flutuante, use os botões da notificação.")
        if (continuarDepoisDaBolha) {
          continuarDepoisDaBolha = false
          pedirPermissoesQueFaltam()
        }
      }

  private val pedirApagar =
      registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { carregarLista() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    setContentView(R.layout.activity_main)
    config = Config(this)

    val raiz = findViewById<View>(R.id.raiz)
    ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
      val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      v.setPadding(b.left, b.top, b.right, b.bottom)
      WindowInsetsCompat.CONSUMED
    }

    painelSessao = findViewById(R.id.painelSessao)
    txtSessao = findViewById(R.id.txtSessao)
    txtSessaoDica = findViewById(R.id.txtSessaoDica)
    btnEncerrar = findViewById(R.id.btnEncerrar)
    lista = findViewById(R.id.lista)
    txtVazio = findViewById(R.id.txtVazio)

    findViewById<Button>(R.id.btnGravar).setOnClickListener { comecar(GravadorService.MODO_GRAVAR) }
    findViewById<Button>(R.id.btnPrint).setOnClickListener { comecar(GravadorService.MODO_PRINTS) }
    btnEncerrar.setOnClickListener {
      val acao =
          if (GravadorService.estado == GravadorService.Estado.PRONTO) GravadorService.ACAO_FECHAR
          else GravadorService.ACAO_PARAR
      startService(Intent(this, GravadorService::class.java).setAction(acao))
    }

    swMic = findViewById(R.id.swMic)
    swMic.isChecked = config.microfone
    swMic.setOnCheckedChangeListener { _, v ->
      config.microfone = v
      if (v && GravadorService.estado != GravadorService.Estado.DESLIGADO)
          toast("Vale a partir da próxima vez que você começar.")
    }

    val swContagem = findViewById<Switch>(R.id.swContagem)
    swContagem.isChecked = config.contagem
    swContagem.setOnCheckedChangeListener { _, v -> config.contagem = v }

    swBolha = findViewById(R.id.swBolha)
    swBolha.isChecked = config.bolha && Settings.canDrawOverlays(this)
    swBolha.setOnCheckedChangeListener { _, v ->
      if (v && !Settings.canDrawOverlays(this)) explicarBolha(continuar = false) else config.bolha = v
    }
  }

  override fun onStart() {
    super.onStart()
    GravadorService.ouvintes += ouvinte
    mostrarSessao()
    carregarLista()
  }

  override fun onStop() {
    GravadorService.ouvintes -= ouvinte
    main.removeCallbacks(relogio)
    super.onStop()
  }

  override fun onDestroy() {
    fundo.shutdown()
    super.onDestroy()
  }

  // ---------- Começar ----------

  private fun comecar(modo: String) {
    modoPedido = modo
    when (GravadorService.estado) {
      GravadorService.Estado.PRONTO -> {
        // Já pode ver a tela: não precisa pedir de novo
        if (modo == GravadorService.MODO_GRAVAR)
            startService(
                Intent(this, GravadorService::class.java)
                    .setAction(GravadorService.ACAO_GRAVAR)
                    .putExtra(GravadorService.EXTRA_ESPERA, 400L))
        moveTaskToBack(true)
        return
      }
      GravadorService.Estado.CONTAGEM,
      GravadorService.Estado.GRAVANDO -> {
        moveTaskToBack(true)
        return
      }
      GravadorService.Estado.DESLIGADO -> {}
    }
    if (config.bolha && !Settings.canDrawOverlays(this)) {
      explicarBolha(continuar = true)
      return
    }
    pedirPermissoesQueFaltam()
  }

  private fun pedirPermissoesQueFaltam() {
    val faltam = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= 33 && !temPermissao(Manifest.permission.POST_NOTIFICATIONS) &&
        !getPreferences(MODE_PRIVATE).getBoolean("pediuNotificacao", false)) {
      getPreferences(MODE_PRIVATE).edit().putBoolean("pediuNotificacao", true).apply()
      faltam += Manifest.permission.POST_NOTIFICATIONS
    }
    if (config.microfone && !temPermissao(Manifest.permission.RECORD_AUDIO)) faltam += Manifest.permission.RECORD_AUDIO
    if (faltam.isEmpty()) pedirCaptura() else pedirPermissoes.launch(faltam.toTypedArray())
  }

  private fun pedirCaptura() {
    val mpm = getSystemService(MediaProjectionManager::class.java)
    // Android 14+: já escolhe "tela inteira" (em vez de um app só)
    val i =
        if (Build.VERSION.SDK_INT >= 34)
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        else mpm.createScreenCaptureIntent()
    try {
      pedirTela.launch(i)
    } catch (e: Exception) {
      toast("Este celular não deixa gravar a tela.")
    }
  }

  private fun explicarBolha(continuar: Boolean) {
    AlertDialog.Builder(this)
        .setTitle("Botão flutuante")
        .setMessage(
            "Para o botão de print e gravação aparecer por cima dos outros apps, o Android precisa da permissão " +
                "\"Sobrepor a outros apps\".\n\nNa próxima tela, ative a opção para o Grava Tela e volte.")
        .setPositiveButton("Permitir") { _, _ ->
          continuarDepoisDaBolha = continuar
          pedirBolha.launch(
              Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        .setNegativeButton(if (continuar) "Usar sem o botão" else "Agora não") { _, _ ->
          config.bolha = false
          swBolha.isChecked = false
          if (continuar) pedirPermissoesQueFaltam()
        }
        .setOnCancelListener {
          if (!Settings.canDrawOverlays(this)) swBolha.isChecked = false
        }
        .show()
  }

  // ---------- Estado ----------

  private fun mostrarSessao() {
    main.removeCallbacks(relogio)
    when (GravadorService.estado) {
      GravadorService.Estado.DESLIGADO -> painelSessao.visibility = View.GONE
      GravadorService.Estado.PRONTO -> {
        painelSessao.visibility = View.VISIBLE
        txtSessao.text = "Pronto para prints"
        txtSessaoDica.text = "Use o botão flutuante ou a notificação para tirar print ou gravar."
        btnEncerrar.text = "Encerrar"
        btnEncerrar.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_close, 0, 0, 0)
      }
      GravadorService.Estado.CONTAGEM -> {
        painelSessao.visibility = View.VISIBLE
        txtSessao.text = "A gravação vai começar…"
        txtSessaoDica.text = ""
        btnEncerrar.text = "Cancelar"
        btnEncerrar.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_close, 0, 0, 0)
      }
      GravadorService.Estado.GRAVANDO -> {
        val s = (System.currentTimeMillis() - GravadorService.inicioGravacao) / 1000
        painelSessao.visibility = View.VISIBLE
        txtSessao.text = "●  Gravando  %d:%02d".format(s / 60, s % 60)
        txtSessaoDica.text = "Pode sair do app. Para parar, use o botão flutuante, a notificação ou o botão abaixo."
        btnEncerrar.text = "Parar e salvar"
        btnEncerrar.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_stop, 0, 0, 0)
        main.postDelayed(relogio, 1000)
      }
    }
  }

  // ---------- Lista de arquivos ----------

  private fun carregarLista() {
    val g = ++geracaoLista
    fundo.execute {
      val itens = Arquivos.listar(this)
      main.post { if (g == geracaoLista && !isDestroyed) mostrarLista(itens) }
    }
  }

  private fun mostrarLista(itens: List<Arquivo>) {
    lista.removeAllViews()
    txtVazio.visibility = if (itens.isEmpty()) View.VISIBLE else View.GONE
    val inflater = LayoutInflater.from(this)
    val formato = SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR"))
    for (a in itens) {
      val v = inflater.inflate(R.layout.item_arquivo, lista, false)
      v.findViewById<TextView>(R.id.txtNome).text = if (a.video) "Vídeo" else "Print"
      val partes = mutableListOf(formato.format(Date(a.data)))
      if (a.video && a.duracao > 0) {
        val s = (a.duracao + 500) / 1000
        partes += "%d:%02d".format(s / 60, s % 60)
      }
      partes += Formatter.formatShortFileSize(this, a.tamanho)
      v.findViewById<TextView>(R.id.txtInfo).text = partes.joinToString("  •  ")
      v.findViewById<View>(R.id.imgPlay).visibility = if (a.video) View.VISIBLE else View.GONE
      v.setOnClickListener { abrir(a) }
      v.findViewById<ImageButton>(R.id.btnCompartilhar).setOnClickListener { compartilhar(a) }
      v.findViewById<ImageButton>(R.id.btnApagar).setOnClickListener { confirmarApagar(a) }
      val img = v.findViewById<ImageView>(R.id.imgMiniatura)
      fundo.execute {
        val bmp = runCatching { contentResolver.loadThumbnail(a.uri, Size(192, 192), null) }.getOrNull()
        if (bmp != null) main.post { img.setImageBitmap(bmp) }
      }
      lista.addView(v)
    }
  }

  private fun abrir(a: Arquivo) {
    try {
      startActivity(
          Intent(Intent.ACTION_VIEW).setDataAndType(a.uri, a.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (e: Exception) {
      toast("Nenhum app para abrir este arquivo.")
    }
  }

  private fun compartilhar(a: Arquivo) {
    val i =
        Intent(Intent.ACTION_SEND)
            .setType(a.mime)
            .putExtra(Intent.EXTRA_STREAM, a.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(i, if (a.video) "Compartilhar vídeo" else "Compartilhar print"))
  }

  private fun confirmarApagar(a: Arquivo) {
    AlertDialog.Builder(this)
        .setTitle(if (a.video) "Apagar este vídeo?" else "Apagar este print?")
        .setMessage("Ele sai também da Galeria.")
        .setPositiveButton("Apagar") { _, _ -> apagar(a) }
        .setNegativeButton("Cancelar", null)
        .show()
  }

  private fun apagar(a: Arquivo) {
    try {
      contentResolver.delete(a.uri, null, null)
      carregarLista()
    } catch (e: SecurityException) {
      // Arquivo de uma instalação antiga do app: o Android pede confirmação
      if (Build.VERSION.SDK_INT >= 30) {
        val pi = android.provider.MediaStore.createDeleteRequest(contentResolver, listOf(a.uri))
        pedirApagar.launch(IntentSenderRequest.Builder(pi.intentSender).build())
      } else {
        toast("Apague pela Galeria.")
      }
    }
  }

  // ---------- Ajudantes ----------

  private fun temPermissao(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

  private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
