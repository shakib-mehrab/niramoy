import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

// Release signing comes from keystore.properties in the project root (never committed).
val keystoreProps = Properties().apply {
  val f = rootProject.file("keystore.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

// Version comes from the release tag: CI passes -PappVersion=1.2.3 for tag v1.2.3, giving
// versionName 1.2.3 and versionCode 10203 (always increasing), so no manual bumps are needed.
val appVersion = ((findProperty("appVersion") as String?) ?: "1.0.0").removePrefix("v")
val appVersionCode = appVersion.split('.').map { part -> part.filter(Char::isDigit).toIntOrNull() ?: 0 }
  .let { (it + listOf(0, 0, 0)).take(3) }.let { (a, b, c) -> a * 10000 + b * 100 + c }
// GitHub repo that publishes releases (and version.json for in-app updates). Forks: -PupdateRepo=you/niramoy
val updateRepo = (findProperty("updateRepo") as String?) ?: "shakib-mehrab/niramoy"

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.niramoy"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.niramoy.app"
    minSdk = 24
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersion
    buildConfigField("String", "UPDATE_MANIFEST_URL", "\"https://github.com/$updateRepo/releases/latest/download/version.json\"")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      if (hasReleaseKeystore) {
        storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
        storePassword = keystoreProps.getProperty("storePassword")
        keyAlias = keystoreProps.getProperty("keyAlias")
        keyPassword = keystoreProps.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      // R8: smaller APK for low-end phones.
      isMinifyEnabled = true
      isShrinkResources = true
      // Real phones are ARM; leaving out x86 keeps the APK small for low-end devices.
      ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// SUPABASE_URL / SUPABASE_ANON_KEY come from .env (falls back to .env.example), same as Ad Deen.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.coil.compose)
  implementation(libs.tesseract4android)
  implementation(libs.androidx.exifinterface)
  testImplementation(libs.junit)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
