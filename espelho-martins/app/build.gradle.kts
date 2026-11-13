import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.compose.compiler)
}

val localProperties =
    Properties().apply {
      val file = rootDir.resolve("local.properties")
      if (file.exists()) {
        file.inputStream().use { load(it) }
      }
    }

android {
  namespace = "com.martins.espelho"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.martins.espelho"
    minSdk = 31
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    // Só a arquitetura dos celulares atuais (deixa o app menor).
    ndk { abiFilters += "arm64-v8a" }

    // Meta Wearables Device Access Toolkit: em Modo de Desenvolvedor pode ficar vazio.
    manifestPlaceholders["mwdat_application_id"] =
        localProperties.getProperty("mwdat_application_id", "")
    manifestPlaceholders["mwdat_client_token"] =
        localProperties.getProperty("mwdat_client_token", "")
  }

  // Chave fixa de assinatura: assim cada versão nova instala por cima da anterior
  // (sem precisar desinstalar). É só para uso pessoal, não para a Play Store.
  signingConfigs {
    getByName("debug") {
      storeFile = file("espelho-debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures { compose = true }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.material3)
  implementation(libs.mwdat.core)
  implementation(libs.mwdat.display)
}
