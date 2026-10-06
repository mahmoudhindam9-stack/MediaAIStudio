import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.example.mediaaistudio"
    minSdk = 24
    targetSdk = 36
    versionCode = 12
    versionName = "1.7.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val backendUrl = (
      System.getenv("MEDIA_AI_BACKEND_URL")
        ?: project.findProperty("MEDIA_AI_BACKEND_URL")?.toString()
        ?: ""
      ).trim().trimEnd('/')
    val backendApiKey = (
      System.getenv("MEDIA_AI_BACKEND_API_KEY")
        ?: project.findProperty("MEDIA_AI_BACKEND_API_KEY")?.toString()
        ?: ""
      ).trim()

    fun buildConfigString(value: String): String =
      "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    buildConfigField("String", "MEDIA_AI_BACKEND_URL", buildConfigString(backendUrl))
    buildConfigField("String", "MEDIA_AI_BACKEND_API_KEY", buildConfigString(backendApiKey))

    fun getResolvedEnv(key: String): String? {
      val envVal = System.getenv(key)
      if (!envVal.isNullOrEmpty()) return envVal.trim()
      val propVal = project.findProperty(key)?.toString()
      if (!propVal.isNullOrEmpty()) return propVal.trim()
      val rootEnvFile = rootProject.file(".env")
      if (rootEnvFile.exists()) {
        val line = rootEnvFile.readLines().firstOrNull {
          val t = it.trim()
          !t.startsWith("#") && t.contains("=") && t.split("=", limit = 2)[0].trim() == key
        }
        if (line != null) {
          return line.split("=", limit = 2)[1].trim()
        }
      }
      return null
    }

    val permanentReleaseCertSha256 = (
      getResolvedEnv("PERMANENT_RELEASE_CERT_SHA256")
        ?: getResolvedEnv("RELEASE_CERT_SHA256")
        ?: "305e793b81fc46944d01258eeeab404f77766c03f14d3f076b2ce81494d82a73"
    ).trim().lowercase().replace(":", "")
    buildConfigField("String", "PERMANENT_RELEASE_CERT_SHA256", buildConfigString(permanentReleaseCertSha256))
  }

  fun getSigningEnv(key: String): String {
    val envVal = System.getenv(key)
    if (!envVal.isNullOrEmpty()) return envVal.trim()
    val propVal = project.findProperty(key)?.toString()
    if (!propVal.isNullOrEmpty()) return propVal.trim()
    val rootEnvFile = rootProject.file(".env")
    if (rootEnvFile.exists()) {
      val line = rootEnvFile.readLines().firstOrNull {
        val t = it.trim()
        !t.startsWith("#") && t.contains("=") && t.split("=", limit = 2)[0].trim() == key
      }
      if (line != null) {
        return line.split("=", limit = 2)[1].trim()
      }
    }
    return ""
  }

  val releaseKeystorePath = getSigningEnv("RELEASE_KEYSTORE_PATH").ifEmpty {
    rootProject.file("release.keystore").takeIf { it.exists() }?.absolutePath ?: ""
  }
  val releaseStorePassword = getSigningEnv("RELEASE_STORE_PASSWORD")
  val releaseKeyAlias = getSigningEnv("RELEASE_KEY_ALIAS")
  val releaseKeyPassword = getSigningEnv("RELEASE_KEY_PASSWORD")

  val hasProductionSigning = releaseKeystorePath.isNotEmpty() &&
    releaseStorePassword.isNotEmpty() &&
    releaseKeyAlias.isNotEmpty() &&
    releaseKeyPassword.isNotEmpty() &&
    file(releaseKeystorePath).exists()

  signingConfigs {
    create("release") {
      if (hasProductionSigning) {
        storeFile = file(releaseKeystorePath)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (hasProductionSigning) {
        signingConfigs.getByName("release")
      } else {
        signingConfigs.getByName("debugConfig")
      }
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  lint {
    abortOnError = false
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.video)
  implementation(libs.androidx.camera.extensions)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.video)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation(libs.media3.transformer)
  implementation(libs.media3.effect)
  implementation(libs.media3.common)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation(libs.mlkit.subjectsegmentation)
  implementation(libs.mlkit.objectdetection)
  implementation(libs.mlkit.face.detection)
  implementation(libs.onnxruntime.android)
  implementation(libs.tensorflow.lite)
  implementation(libs.tensorflow.lite.gpu)

  implementation(libs.tensorflow.lite.support) {
    exclude(group = "org.tensorflow", module = "tensorflow-lite-support-api")
  }

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation("com.microsoft.onnxruntime:onnxruntime:1.16.3")
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}