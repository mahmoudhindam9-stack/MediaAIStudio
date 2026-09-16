with open("app/build.gradle.kts", "r") as f:
    gradle = f.read()

deps = """
  implementation(libs.tensorflow.lite.support) {
      exclude(group = "org.tensorflow", module = "tensorflow-lite-support-api")
  }
"""

gradle = gradle.replace("  implementation(libs.tensorflow.lite.support)", deps)

with open("app/build.gradle.kts", "w") as f:
    f.write(gradle)
