import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
}

// O app mostra as telas da pasta baixa-martins/web/ (as mesmas que o PC serve no navegador).
// A cada compilação, esses arquivos são copiados para dentro do APK.
val webDir = rootDir.parentFile.resolve("web")
val webAssets = layout.buildDirectory.dir("generated/webassets")
val copyWeb by
    tasks.registering(Sync::class) {
      from(webDir)
      into(webAssets.map { it.dir("web") })
    }

android {
  namespace = "com.martins.baixa"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.martins.baixa"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  sourceSets { getByName("main") { assets.srcDir(webAssets) } }

  // Chave fixa: cada versão nova instala por cima da anterior sem perder os computadores pareados.
  // Só para uso pessoal, não para a Play Store.
  signingConfigs {
    getByName("debug") {
      storeFile = file("baixa-debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
    create("pessoal") {
      storeFile = file("baixa-debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("pessoal")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

tasks.named("preBuild") { dependsOn(copyWeb) }

dependencies {
  implementation(libs.androidx.activity)
  implementation(libs.androidx.core)
  implementation(libs.androidx.webkit)
}
