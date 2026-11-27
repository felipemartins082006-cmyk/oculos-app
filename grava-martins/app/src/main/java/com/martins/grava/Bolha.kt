package com.martins.grava

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Botão flutuante que fica por cima dos outros apps.
 * Pronto: [print] [gravar] [fechar]. Gravando: tempo + [parar]. Dá para arrastar para qualquer lugar.
 */
class Bolha(private val ctx: Context, private val acoes: Acoes) {

  interface Acoes {
    fun print()
    fun gravar()
    fun parar()
    fun fechar()
  }

  private val wm = ctx.getSystemService(WindowManager::class.java)
  private val raiz = LinearLayout(ctx)
  private val btnPrint = botao(R.drawable.ic_camera, "Tirar print")
  private val btnGravar = botao(R.drawable.ic_record_red, "Gravar")
  private val btnParar = botao(R.drawable.ic_stop, "Parar")
  private val btnFechar = botao(R.drawable.ic_close, "Fechar", pequeno = true)
  private val txtTempo =
      TextView(ctx).apply {
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(0, dp(6), 0, 0)
      }
  private val txtContagem =
      TextView(ctx).apply {
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 30f
        gravity = Gravity.CENTER
        minWidth = dp(52)
        minHeight = dp(56)
        paint.isFakeBoldText = true
      }
  private val lp =
      WindowManager.LayoutParams(
              WindowManager.LayoutParams.WRAP_CONTENT,
              WindowManager.LayoutParams.WRAP_CONTENT,
              WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
              WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                  WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
              PixelFormat.TRANSLUCENT)
          .apply {
            gravity = Gravity.TOP or Gravity.START
            val m = ctx.resources.displayMetrics
            x = m.widthPixels - dp(70)
            y = (m.heightPixels * 0.3).roundToInt()
          }
  private var naTela = false

  init {
    raiz.orientation = LinearLayout.VERTICAL
    raiz.gravity = Gravity.CENTER_HORIZONTAL
    raiz.setPadding(dp(4), dp(6), dp(4), dp(6))
    raiz.background =
        GradientDrawable().apply {
          setColor(0xE615181F.toInt())
          cornerRadius = dp(30).toFloat()
          setStroke(dp(1), 0x33FFFFFF)
        }
    raiz.elevation = dp(6).toFloat()
    for (v in listOf(txtContagem, txtTempo, btnParar, btnPrint, btnGravar, btnFechar)) raiz.addView(v)
    btnPrint.setOnClickListener { acoes.print() }
    btnGravar.setOnClickListener { acoes.gravar() }
    btnParar.setOnClickListener { acoes.parar() }
    btnFechar.setOnClickListener { acoes.fechar() }
    val arrastar = Arrastar()
    raiz.setOnTouchListener(arrastar)
    for (i in 0 until raiz.childCount) raiz.getChildAt(i).setOnTouchListener(arrastar)
  }

  fun mostrar(): Boolean {
    if (naTela) return true
    if (!Settings.canDrawOverlays(ctx)) return false
    return try {
      wm.addView(raiz, lp)
      naTela = true
      true
    } catch (e: Exception) {
      false
    }
  }

  fun remover() {
    if (!naTela) return
    naTela = false
    try {
      wm.removeView(raiz)
    } catch (_: Exception) {}
  }

  /** Some por um instante (para não sair no print). */
  fun esconder() {
    raiz.visibility = View.INVISIBLE
  }

  fun reaparecer() {
    raiz.visibility = View.VISIBLE
  }

  fun pronto() {
    mostrarSo(btnPrint, btnGravar, btnFechar)
  }

  fun contagem(n: Int) {
    txtContagem.text = n.toString()
    mostrarSo(txtContagem)
  }

  fun gravando(segundos: Long) {
    txtTempo.text = "%d:%02d".format(segundos / 60, segundos % 60)
    mostrarSo(txtTempo, btnParar)
  }

  private fun mostrarSo(vararg vs: View) {
    for (i in 0 until raiz.childCount) {
      val v = raiz.getChildAt(i)
      v.visibility = if (v in vs) View.VISIBLE else View.GONE
    }
  }

  private fun botao(icone: Int, descricao: String, pequeno: Boolean = false) =
      ImageView(ctx).apply {
        setImageResource(icone)
        contentDescription = descricao
        val t = if (pequeno) dp(40) else dp(52)
        val pad = if (pequeno) dp(10) else dp(13)
        layoutParams = LinearLayout.LayoutParams(t, t)
        setPadding(pad, pad, pad, pad)
        isClickable = true
      }

  private fun dp(v: Int): Int =
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics)
          .roundToInt()

  /** Toque rápido = clique; arrastar = mover o botão. */
  private inner class Arrastar : View.OnTouchListener {
    private val folga = ViewConfiguration.get(ctx).scaledTouchSlop
    private var x0 = 0f
    private var y0 = 0f
    private var px = 0
    private var py = 0
    private var arrastando = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, e: MotionEvent): Boolean {
      when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          x0 = e.rawX
          y0 = e.rawY
          px = lp.x
          py = lp.y
          arrastando = false
          if (v !== raiz) v.alpha = 0.55f
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = e.rawX - x0
          val dy = e.rawY - y0
          if (!arrastando && (abs(dx) > folga || abs(dy) > folga)) {
            arrastando = true
            v.alpha = 1f
          }
          if (arrastando && naTela) {
            val m = ctx.resources.displayMetrics
            lp.x = (px + dx.roundToInt()).coerceIn(0, (m.widthPixels - raiz.width).coerceAtLeast(0))
            lp.y = (py + dy.roundToInt()).coerceIn(0, (m.heightPixels - raiz.height).coerceAtLeast(0))
            try {
              wm.updateViewLayout(raiz, lp)
            } catch (_: Exception) {}
          }
        }
        MotionEvent.ACTION_UP -> {
          v.alpha = 1f
          if (!arrastando && v !== raiz) v.performClick()
        }
        MotionEvent.ACTION_CANCEL -> v.alpha = 1f
      }
      return true
    }
  }
}
