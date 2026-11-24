package com.martins.baixa

import android.app.Application

/** Liga o motor assim que o Android abre o app, antes mesmo da tela aparecer. */
class BaixaApp : Application() {
  override fun onCreate() {
    super.onCreate()
    Engine.ensure(this)
  }
}
