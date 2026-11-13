import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "com.martins.logbook"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.martins.logbook"
    minSdk = 26
    targetSdk = 36
    versionCode = 3
    versionName = "1.2"
  }

  // Chave fixa de assinatura: assim cada versão nova instala por cima da anterior
  // (sem desinstalar e sem perder os registros). Só para uso pessoal, não para a Play Store.
  signingConfigs {
    create("pessoal") {
      storeFile = file("logbook.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    debug { signingConfig = signingConfigs.getByName("pessoal") }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("pessoal")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures { compose = true }

  // Testes de tela rodando no computador (Robolectric), sem precisar de celular.
  testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.material3)
  testImplementation(libs.junit)
  testImplementation(libs.orgjson)
  testImplementation(libs.robolectric)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.test.manifest)
}
