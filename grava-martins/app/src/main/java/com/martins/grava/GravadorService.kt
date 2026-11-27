package com.martins.grava

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.view.Display
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Fica ligado enquanto o app pode ver a tela (depois que você aceita o aviso do Android).
 * Uma única "tela virtual" espelha a tela do celular; ela é ligada no gravador de vídeo
 * durante a gravação ou num leitor de imagem na hora do print.
 */
class GravadorService : Service(), Bolha.Acoes {

  enum class Estado {
    DESLIGADO,
    PRONTO,
    CONTAGEM,
    GRAVANDO,
  }

  companion object {
    const val ACAO_INICIAR = "iniciar"
    const val ACAO_PRINT = "print"
    const val ACAO_GRAVAR = "gravar"
    const val ACAO_PARAR = "parar"
    const val ACAO_FECHAR = "fechar"

    const val EXTRA_CODIGO = "codigo"
    const val EXTRA_DADOS = "dados"
    const val EXTRA_MODO = "modo"
    const val EXTRA_ESPERA = "espera"

    const val MODO_GRAVAR = "gravar"
    const val MODO_PRINTS = "prints"

    private const val CANAL_SESSAO = "sessao"
    private const val CANAL_SALVOS = "salvos"
    private const val ID_SESSAO = 1
    private const val ID_SALVO = 2

    @Volatile
    var estado = Estado.DESLIGADO
      private set

    @Volatile
    var inicioGravacao = 0L
      private set

    /** Avisos para a tela do app (mudou o estado / salvou arquivo). Sempre na thread principal. */
    val ouvintes = mutableSetOf<() -> Unit>()
  }

  private val main = Handler(Looper.getMainLooper())
  private val fundoThread = HandlerThread("print").apply { start() }
  private val fundo = Handler(fundoThread.looper)

  private var projecao: MediaProjection? = null
  private var tela: VirtualDisplay? = null
  private var bolha: Bolha? = null
  private var comMicrofone = false
  private var encerrando = false
  private var fazendoPrint = false

  private var gravador: MediaRecorder? = null
  private var arquivoVideo: ParcelFileDescriptor? = null
  private var uriVideo: Uri? = null

  private var contagemRestante = 0
  private val tickContagem =
      object : Runnable {
        override fun run() {
          if (estado != Estado.CONTAGEM) return
          if (contagemRestante <= 0) {
            bolha?.esconder()
            // espera a bolha sumir antes de começar
            main.postDelayed({ if (estado == Estado.CONTAGEM) comecarGravacao() }, 150)
            return
          }
          bolha?.contagem(contagemRestante)
          contagemRestante--
          main.postDelayed(this, 1000)
        }
      }

  private val tickRelogio =
      object : Runnable {
        override fun run() {
          if (estado != Estado.GRAVANDO) return
          bolha?.gravando((System.currentTimeMillis() - inicioGravacao) / 1000)
          main.postDelayed(this, 1000)
        }
      }

  private val aoParar =
      object : MediaProjection.Callback() {
        // O Android encerrou a captura (pelo aviso na barra de status, por exemplo)
        override fun onStop() {
          main.post { encerrar() }
        }
      }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
        NotificationChannel(CANAL_SESSAO, "Gravação e prints", NotificationManager.IMPORTANCE_LOW).apply {
          setShowBadge(false)
        })
    nm.createNotificationChannel(
        NotificationChannel(CANAL_SALVOS, "Vídeos salvos", NotificationManager.IMPORTANCE_DEFAULT))
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val acao = intent?.action
    if (acao == ACAO_INICIAR) {
      iniciar(intent)
      return START_NOT_STICKY
    }
    if (projecao == null) {
      // Serviço recriado sem permissão de tela: não há o que fazer
      stopSelf()
      return START_NOT_STICKY
    }
    val espera = intent?.getLongExtra(EXTRA_ESPERA, 0L) ?: 0L
    when (acao) {
      ACAO_PRINT -> tirarPrint(espera)
      ACAO_GRAVAR -> main.postDelayed({ gravar() }, espera)
      ACAO_PARAR -> parar()
      ACAO_FECHAR -> encerrar()
    }
    return START_NOT_STICKY
  }

  // ---------- Começo da sessão ----------

  private fun iniciar(intent: Intent) {
    val codigo = intent.getIntExtra(EXTRA_CODIGO, 0)
    @Suppress("DEPRECATION")
    val dados: Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DADOS, Intent::class.java)
        else intent.getParcelableExtra(EXTRA_DADOS)
    val modo = intent.getStringExtra(EXTRA_MODO) ?: MODO_PRINTS
    if (dados == null || projecao != null) {
      if (projecao != null && modo == MODO_GRAVAR) gravar()
      return
    }

    val config = Config(this)
    comMicrofone =
        config.microfone &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    estado = Estado.PRONTO
    // Precisa estar em primeiro plano ANTES de pegar a permissão da tela (Android 14+)
    var tipo = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
    if (comMicrofone && Build.VERSION.SDK_INT >= 30) tipo = tipo or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    try {
      startForeground(ID_SESSAO, notificacao(), tipo)
    } catch (e: Exception) {
      // Microfone recusado pelo sistema: segue sem som
      comMicrofone = false
      try {
        startForeground(ID_SESSAO, notificacao(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
      } catch (e2: Exception) {
        aviso("Não foi possível começar: ${e2.message}")
        estado = Estado.DESLIGADO
        stopSelf()
        return
      }
    }

    val mpm = getSystemService(MediaProjectionManager::class.java)
    val p =
        try {
          mpm.getMediaProjection(codigo, dados)
        } catch (e: Exception) {
          null
        }
    if (p == null) {
      aviso("O Android não deixou ver a tela. Tente de novo.")
      encerrar()
      return
    }
    projecao = p
    p.registerCallback(aoParar, main)

    val (l, a, dpi) = medidas()
    tela =
        try {
          p.createVirtualDisplay(
              "GravaTela", l, a, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, main)
        } catch (e: Exception) {
          null
        }
    if (tela == null) {
      aviso("Não foi possível ver a tela. Tente de novo.")
      encerrar()
      return
    }

    if (config.bolha) {
      val b = Bolha(this, this)
      if (b.mostrar()) {
        bolha = b
        b.pronto()
      }
    }
    avisarTela()

    if (modo == MODO_GRAVAR) {
      // Dá tempo do aviso do Android sumir e do app ir para trás
      main.postDelayed({ gravar() }, 400)
    } else if (bolha == null) {
      aviso("Pronto! Para tirar print, puxe a barra de notificações e toque em Print.")
    }
  }

  // ---------- Botões ----------

  override fun print() = tirarPrint(0)

  override fun gravar() {
    if (estado != Estado.PRONTO || fazendoPrint) return
    if (Config(this).contagem) {
      estado = Estado.CONTAGEM
      contagemRestante = 3
      if (bolha == null) aviso("Gravando em 3 segundos…")
      atualizarNotificacao()
      avisarTela()
      main.post(tickContagem)
    } else {
      estado = Estado.CONTAGEM
      bolha?.esconder()
      main.postDelayed({ if (estado == Estado.CONTAGEM) comecarGravacao() }, 150)
    }
  }

  override fun parar() {
    when (estado) {
      Estado.GRAVANDO -> {
        pararGravacao()
        encerrar()
      }
      Estado.CONTAGEM -> {
        main.removeCallbacks(tickContagem)
        estado = Estado.PRONTO
        bolha?.reaparecer()
        bolha?.pronto()
        atualizarNotificacao()
        avisarTela()
      }
      else -> {}
    }
  }

  override fun fechar() = encerrar()

  // ---------- Gravação ----------

  private fun comecarGravacao() {
    val vd = tela ?: return
    val (l, a, dpi) = medidas()
    val (vl, va) = tamanhoVideo(l, a)
    val uri = Arquivos.novoVideo(this)
    val pfd = uri?.let { runCatching { contentResolver.openFileDescriptor(it, "rw") }.getOrNull() }
    if (uri == null || pfd == null) {
      uri?.let { Arquivos.descartar(this, it) }
      falhaAoGravar("Não foi possível criar o arquivo do vídeo.")
      return
    }

    var rec = prepararGravador(pfd, vl, va, comMicrofone)
    if (rec == null && comMicrofone) {
      rec = prepararGravador(pfd, vl, va, false)
      if (rec != null) aviso("Microfone indisponível: gravando sem som.")
    }
    if (rec == null) {
      runCatching { pfd.close() }
      Arquivos.descartar(this, uri)
      falhaAoGravar("Este celular não aceitou gravar a tela.")
      return
    }

    try {
      vd.resize(vl, va, (dpi * vl.toFloat() / l).roundToInt().coerceAtLeast(120))
      vd.surface = rec.surface
      rec.start()
    } catch (e: Exception) {
      vd.surface = null
      rec.release()
      runCatching { pfd.close() }
      Arquivos.descartar(this, uri)
      falhaAoGravar("Não foi possível começar a gravação.")
      return
    }

    gravador = rec
    arquivoVideo = pfd
    uriVideo = uri
    estado = Estado.GRAVANDO
    inicioGravacao = System.currentTimeMillis()
    bolha?.gravando(0)
    bolha?.reaparecer()
    main.postDelayed(tickRelogio, 1000)
    atualizarNotificacao()
    avisarTela()
  }

  private fun prepararGravador(pfd: ParcelFileDescriptor, l: Int, a: Int, som: Boolean): MediaRecorder? {
    val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
    return try {
      if (som) r.setAudioSource(MediaRecorder.AudioSource.MIC)
      r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
      r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      r.setOutputFile(pfd.fileDescriptor)
      r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
      r.setVideoSize(l, a)
      r.setVideoFrameRate(30)
      r.setVideoEncodingBitRate((l.toLong() * a * 30 / 8).coerceIn(2_000_000, 16_000_000).toInt())
      if (som) {
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44100)
        r.setAudioChannels(1)
        r.setAudioEncodingBitRate(128_000)
      }
      r.prepare()
      r
    } catch (e: Exception) {
      r.release()
      null
    }
  }

  private fun falhaAoGravar(msg: String) {
    aviso(msg)
    estado = Estado.PRONTO
    bolha?.reaparecer()
    bolha?.pronto()
    atualizarNotificacao()
    avisarTela()
  }

  private fun pararGravacao() {
    val rec = gravador ?: return
    main.removeCallbacks(tickRelogio)
    tela?.surface = null
    var ok = true
    try {
      rec.stop()
    } catch (e: RuntimeException) {
      ok = false // parou cedo demais, sem nenhum quadro
    }
    rec.release()
    runCatching { arquivoVideo?.close() }
    gravador = null
    arquivoVideo = null
    val uri = uriVideo ?: return
    uriVideo = null
    if (ok) {
      Arquivos.publicar(this, uri)
      vibrar()
      notificarVideo(uri)
      aviso("Vídeo salvo na Galeria")
    } else {
      Arquivos.descartar(this, uri)
      aviso("A gravação foi curta demais e não foi salva.")
    }
    ouvintes.toList().forEach { it() }
  }

  /** Tamanho do vídeo: o da tela, diminuído se o celular não conseguir gravar nesse tamanho. */
  private fun tamanhoVideo(l: Int, a: Int): Pair<Int, Int> {
    val caps =
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
            .mapNotNull { runCatching { it.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities }.getOrNull() }
    var escala = 1.0
    repeat(12) {
      val vl = ((l * escala).toInt() / 16 * 16).coerceAtLeast(16)
      val va = ((a * escala).toInt() / 16 * 16).coerceAtLeast(16)
      if (caps.isEmpty() || caps.any { c -> c.areSizeAndRateSupported(vl, va, 30.0) }) return vl to va
      escala *= 0.85
    }
    return if (l < a) 720 to 1280 else 1280 to 720
  }

  // ---------- Print ----------

  private fun tirarPrint(espera: Long) {
    if (estado != Estado.PRONTO || fazendoPrint) return
    fazendoPrint = true
    val temBolha = bolha != null
    bolha?.esconder()
    main.postDelayed({ capturar() }, espera + if (temBolha) 250 else 0)
  }

  private fun capturar() {
    val vd = tela
    if (vd == null) {
      fimDoPrint()
      return
    }
    val (l, a, dpi) = medidas()
    val leitor = ImageReader.newInstance(l, a, PixelFormat.RGBA_8888, 2)
    try {
      vd.resize(l, a, dpi)
      vd.surface = leitor.surface
    } catch (e: Exception) {
      leitor.close()
      aviso("Não foi possível tirar o print.")
      fimDoPrint()
      return
    }
    val terminou = AtomicBoolean(false)
    var tentativas = 0
    // Espera um pouco para pegar a imagem mais recente da tela
    val pegar =
        object : Runnable {
          override fun run() {
            if (terminou.get()) return
            val img = runCatching { leitor.acquireLatestImage() }.getOrNull()
            if (img == null) {
              if (++tentativas < 20) fundo.postDelayed(this, 100)
              else {
                terminou.set(true)
                main.post {
                  vd.surface = null
                  leitor.close()
                  aviso("Não foi possível tirar o print.")
                  fimDoPrint()
                }
              }
              return
            }
            terminou.set(true)
            val bmp =
                try {
                  val plano = img.planes[0]
                  val largura = plano.rowStride / plano.pixelStride
                  val cheio = Bitmap.createBitmap(largura, img.height, Bitmap.Config.ARGB_8888)
                  cheio.copyPixelsFromBuffer(plano.buffer)
                  if (largura != img.width) Bitmap.createBitmap(cheio, 0, 0, img.width, img.height).also { cheio.recycle() }
                  else cheio
                } catch (e: Exception) {
                  null
                } finally {
                  img.close()
                }
            main.post {
              vd.surface = null
              leitor.close()
              fimDoPrint()
              if (bmp != null) {
                somDeFoto()
                vibrar()
              }
            }
            if (bmp == null) {
              main.post { aviso("Não foi possível tirar o print.") }
              return
            }
            val salvo = salvarPng(bmp)
            bmp.recycle()
            main.post {
              aviso(if (salvo) "Print salvo na Galeria" else "Não foi possível salvar o print.")
              ouvintes.toList().forEach { it() }
            }
          }
        }
    fundo.postDelayed(pegar, 300)
  }

  private fun fimDoPrint() {
    fazendoPrint = false
    bolha?.reaparecer()
  }

  private fun salvarPng(bmp: Bitmap): Boolean {
    val uri = Arquivos.novoPrint(this) ?: return false
    return try {
      contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
      Arquivos.publicar(this, uri)
      true
    } catch (e: Exception) {
      Arquivos.descartar(this, uri)
      false
    }
  }

  // ---------- Fim ----------

  private fun encerrar() {
    if (encerrando) return
    encerrando = true
    main.removeCallbacks(tickContagem)
    main.removeCallbacks(tickRelogio)
    if (estado == Estado.GRAVANDO) pararGravacao()
    bolha?.remover()
    bolha = null
    runCatching { tela?.release() }
    tela = null
    projecao?.let {
      runCatching { it.unregisterCallback(aoParar) }
      runCatching { it.stop() }
    }
    projecao = null
    estado = Estado.DESLIGADO
    avisarTela()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    if (!encerrando) encerrar()
    fundoThread.quitSafely()
    super.onDestroy()
  }

  // ---------- Ajudantes ----------

  private fun medidas(): Triple<Int, Int, Int> {
    val d = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    val m = DisplayMetrics()
    @Suppress("DEPRECATION") d.getRealMetrics(m)
    return Triple(m.widthPixels, m.heightPixels, m.densityDpi)
  }

  private fun avisarTela() {
    main.post { ouvintes.toList().forEach { it() } }
  }

  private fun aviso(msg: String) {
    main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
  }

  private fun vibrar() {
    try {
      val v =
          if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
          else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
      v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {}
  }

  private fun somDeFoto() {
    try {
      MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
    } catch (_: Exception) {}
  }

  private fun atualizarNotificacao() {
    if (estado == Estado.DESLIGADO) return
    getSystemService(NotificationManager::class.java).notify(ID_SESSAO, notificacao())
  }

  private fun acaoTela(codigo: Int, acao: String): PendingIntent =
      PendingIntent.getActivity(
          this,
          codigo,
          Intent(this, AcaoActivity::class.java)
              .putExtra(AcaoActivity.EXTRA_ACAO, acao)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

  private fun acaoServico(codigo: Int, acao: String): PendingIntent =
      PendingIntent.getService(
          this,
          codigo,
          Intent(this, GravadorService::class.java).setAction(acao),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

  private fun notificacao(): Notification {
    val abrir =
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val b =
        Notification.Builder(this, CANAL_SESSAO)
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(abrir)
            .setColor(getColor(R.color.brand))
    if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
    fun acao(icone: Int, texto: String, pi: PendingIntent) =
        b.addAction(Notification.Action.Builder(Icon.createWithResource(this, icone), texto, pi).build())
    when (estado) {
      Estado.GRAVANDO -> {
        b.setContentTitle("Gravando a tela")
            .setContentText(if (comMicrofone) "Com som do microfone" else "Sem som")
            .setUsesChronometer(true)
            .setWhen(inicioGravacao)
            .setShowWhen(true)
        acao(R.drawable.ic_stop, "Parar e salvar", acaoServico(10, ACAO_PARAR))
      }
      Estado.CONTAGEM -> {
        b.setContentTitle("A gravação vai começar…")
        acao(R.drawable.ic_close, "Cancelar", acaoServico(11, ACAO_PARAR))
      }
      else -> {
        b.setContentTitle("Pronto para print e gravação").setContentText("Toque em Print ou Gravar aqui embaixo")
        acao(R.drawable.ic_camera, "Print", acaoTela(12, ACAO_PRINT))
        acao(R.drawable.ic_record, "Gravar", acaoTela(13, ACAO_GRAVAR))
        acao(R.drawable.ic_close, "Fechar", acaoServico(14, ACAO_FECHAR))
      }
    }
    return b.build()
  }

  private fun notificarVideo(uri: Uri) {
    val ver =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    val enviar =
        Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("video/mp4")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Compartilhar vídeo")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val n =
        Notification.Builder(this, CANAL_SALVOS)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(getColor(R.color.brand))
            .setContentTitle("Vídeo salvo")
            .setContentText("Toque para assistir. Fica na Galeria, em Filmes › ${Arquivos.PASTA}.")
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(this, 20, ver, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(
                Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_share),
                        "Compartilhar",
                        PendingIntent.getActivity(this, 21, enviar, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    .build())
            .build()
    try {
      getSystemService(NotificationManager::class.java).notify(ID_SALVO, n)
    } catch (_: SecurityException) {}
  }
}
