// Top-level build file where you can add configuration options common to all sub-projects/modules.
val debugKeystore = rootProject.file("debug.keystore")
val debugKeystoreBase64 = rootProject.file("debug.keystore.base64")
if (!debugKeystore.exists() && debugKeystoreBase64.exists()) {
  try {
    val decoded = java.util.Base64.getMimeDecoder().decode(debugKeystoreBase64.readText().trim())
    debugKeystore.writeBytes(decoded)
  } catch (e: Exception) {
    logger.warn("Could not restore debug.keystore from debug.keystore.base64", e)
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}
