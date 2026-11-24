package com.martins.baixa

import android.content.Context
import android.os.Environment
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * O "motor" do app: o mesmo programa Baixa Martins do PC (feito em Go), rodando dentro do celular
 * em http://127.0.0.1:47900 (só o próprio celular acessa). Ele usa:
 *   - yt-dlp + Python + QuickJS (biblioteca youtubedl-android) para baixar do YouTube
 *   - ffmpeg (mesma biblioteca) para converter para MP3 e juntar vídeo + áudio
 *   - Whisper (compilado para o celular) para fazer letras e legendas
 */
object Engine {
  const val PORT = 47900
  const val BASE = "http://127.0.0.1:$PORT"
  private const val TAG = "BaixaEngine"

  @Volatile private var process: Process? = null
  @Volatile var status = "parado"
    private set
  @Volatile var error = ""
    private set
  private val lock = Any()

  fun isRunning(): Boolean = process?.isAlive == true

  /** Liga o motor (se ainda não estiver ligado). Não trava: roda numa thread separada. */
  fun ensure(ctx: Context) {
    synchronized(lock) {
      if (isRunning() || status == "iniciando") return
      status = "iniciando"
      error = ""
    }
    val app = ctx.applicationContext
    thread(name = "baixa-engine") {
      try {
        start(app)
      } catch (e: Throwable) {
        Log.e(TAG, "não iniciou", e)
        error = e.message ?: e.toString()
        status = "erro"
      }
    }
  }

  private fun start(ctx: Context) {
    val lib = File(ctx.applicationInfo.nativeLibraryDir)
    val base = File(ctx.noBackupFilesDir, YoutubeDL.baseName)
    val pkgs = File(base, "packages")
    val pyDir = File(pkgs, "python")
    val data = File(ctx.filesDir, "baixa").apply { mkdirs() }
    val tmp = File(ctx.getExternalFilesDir(null) ?: ctx.cacheDir, "baixando").apply { mkdirs() }
    // marcador "Python e ffmpeg prontos": o motor só chama o yt-dlp/ffmpeg depois que ele existir
    val ready = File(data, ".ferramentas-prontas")
    ready.delete()

    @Suppress("DEPRECATION")
    val music = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "BaixaMartins")
    @Suppress("DEPRECATION")
    val movies = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "BaixaMartins")

    // 1) liga o motor na hora: a tela abre sem esperar o Python
    val pb = ProcessBuilder(File(lib, "libbaixa.so").absolutePath)
    pb.directory(data)
    pb.redirectErrorStream(true)
    pb.redirectOutput(File(data, "saida.log"))
    pb.environment().apply {
      put("BAIXA_MARTINS_DIR", data.absolutePath)
      put("BM_LISTEN", "127.0.0.1")
      put("BM_READY", ready.absolutePath)
      put("BM_PYTHON", File(lib, "libpython.so").absolutePath)
      put("BM_YTDLP", File(File(base, YoutubeDL.ytdlpDirName), YoutubeDL.ytdlpBin).absolutePath)
      put("BM_QJS", File(lib, "libqjs.so").absolutePath)
      put("BM_FFMPEG", File(lib, "libffmpeg.so").absolutePath)
      put("BM_WHISPER", File(lib, "libwhispercli.so").absolutePath)
      put("BM_WHISPER_V8", File(lib, "libwhispercli_v8.so").absolutePath)
      put("BM_AUDIO_DIR", music.absolutePath)
      put("BM_VIDEO_DIR", movies.absolutePath)
      put("BM_TEMP", tmp.absolutePath)
      // o mesmo ambiente que a youtubedl-android usa para o Python e o ffmpeg
      put("LD_LIBRARY_PATH", listOf("python", "ffmpeg", "aria2c").joinToString(":") { File(pkgs, "$it/usr/lib").absolutePath })
      put("SSL_CERT_FILE", File(pyDir, "usr/etc/tls/cert.pem").absolutePath)
      put("PYTHONHOME", File(pyDir, "usr").absolutePath)
      put("HOME", File(pyDir, "usr").absolutePath)
      put("TMPDIR", ctx.cacheDir.absolutePath)
      put("PATH", (System.getenv("PATH") ?: "/system/bin") + ":" + lib.absolutePath)
    }
    val p = pb.start()
    process = p
    thread(name = "baixa-engine-wait") {
      val code = runCatching { p.waitFor() }.getOrDefault(-1)
      Log.w(TAG, "motor parou: $code")
      if (process === p) status = "parado"
    }

    // 2) enquanto isso, prepara o Python e o ffmpeg (na primeira vez descompacta uns 50 MB)
    YoutubeDL.getInstance().init(ctx)
    FFmpeg.getInstance().init(ctx)
    ready.writeText("ok")

    for (i in 0 until 100) {
      if (!p.isAlive) break
      if (ping()) {
        status = "ok"
        Log.i(TAG, "motor ligado")
        return
      }
      Thread.sleep(100)
    }
    if (ping()) { // outro motor (de antes) ainda está ligado e respondendo
      status = "ok"
      return
    }
    val log = runCatching { File(data, "saida.log").readText().takeLast(600) }.getOrDefault("")
    p.destroy()
    throw IllegalStateException("o motor não respondeu. $log")
  }

  fun ping(): Boolean =
      runCatching {
            val c = URL("$BASE/api/info").openConnection() as HttpURLConnection
            c.connectTimeout = 300
            c.readTimeout = 600
            c.inputStream.use { it.readBytes() }
            c.responseCode == 200
          }
          .getOrDefault(false)

  fun stop() {
    process?.destroy()
    process = null
    status = "parado"
  }
}
