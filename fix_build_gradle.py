import os

path = "app/build.gradle.kts"
with open(path, "r") as f:
    content = f.read()

# Update release signing to purely use env vars without defaulting to a dummy file that might not exist 
# if we just want it to fall back safely or require it explicitly.
# Actually, if we just set it up safely:
signing_config_replacement = """    create("release") {
      val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
      if (keystorePath != null && file(keystorePath).exists()) {
          storeFile = file(keystorePath)
          storePassword = System.getenv("RELEASE_STORE_PASSWORD")
          keyAlias = System.getenv("RELEASE_KEY_ALIAS")
          keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
      }
    }"""
content = content.replace("""    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }""", signing_config_replacement)

# Enable minify in release
content = content.replace("isMinifyEnabled = false", "isMinifyEnabled = true")

with open(path, "w") as f:
    f.write(content)
