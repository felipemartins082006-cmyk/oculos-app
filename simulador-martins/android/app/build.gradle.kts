import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
}

// O app mostra a mesma página da versão web (pasta simulador-martins/), com os dados embutidos.
// A cada compilação, esses arquivos são copiados para dentro do APK.
val webDir = rootDir.parentFile
val webAssets = layout.buildDirectory.dir("generated/webassets")
val copyWeb by
    tasks.registering(Sync::class) {
      from(webDir) { include("index.html", "styles.css", "app.js", "data.js", "icon.svg", "vendor/**") }
      into(webAssets.map { it.dir("web") })
    }

android {
  namespace = "com.martins.simulador"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.martins.simulador"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  sourceSets { getByName("main") { assets.srcDir(webAssets) } }

  // Chave fixa: cada versão nova instala por cima da anterior. Uso pessoal, não é para a Play Store.
  signingConfigs {
    create("pessoal") {
      storeFile = file("simulador.keystore")
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
