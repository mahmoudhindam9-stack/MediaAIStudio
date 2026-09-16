import re

with open("gradle/libs.versions.toml", "r") as f:
    toml = f.read()

# Add versions
toml = toml.replace('[versions]', '[versions]\nonnxruntime = "1.18.1"\ntensorflowLite = "2.16.1"\n')

# Add libraries
libs_block = """
onnxruntime-android = { group = "com.microsoft.onnxruntime", name = "onnxruntime-android", version.ref = "onnxruntime" }
tensorflow-lite = { group = "org.tensorflow", name = "tensorflow-lite", version.ref = "tensorflowLite" }
tensorflow-lite-gpu = { group = "org.tensorflow", name = "tensorflow-lite-gpu", version.ref = "tensorflowLite" }
tensorflow-lite-support = { group = "org.tensorflow", name = "tensorflow-lite-support", version = "0.4.4" }
"""
toml = toml.replace('[libraries]', '[libraries]' + libs_block)

with open("gradle/libs.versions.toml", "w") as f:
    f.write(toml)

with open("app/build.gradle.kts", "r") as f:
    gradle = f.read()

deps = """  implementation(libs.onnxruntime.android)
  implementation(libs.tensorflow.lite)
  implementation(libs.tensorflow.lite.gpu)
  implementation(libs.tensorflow.lite.support)
"""
gradle = gradle.replace('  implementation(libs.mlkit.subjectsegmentation)', '  implementation(libs.mlkit.subjectsegmentation)\n' + deps)

with open("app/build.gradle.kts", "w") as f:
    f.write(gradle)
