import os

with open("gradle/libs.versions.toml", "r") as f:
    content = f.read()

if "workRuntimeKtx" not in content:
    content = content.replace("[versions]", "[versions]\nworkRuntimeKtx = \"2.9.0\"")
    content = content.replace("[libraries]", "[libraries]\nandroidx-work-runtime-ktx = { group = \"androidx.work\", name = \"work-runtime-ktx\", version.ref = \"workRuntimeKtx\" }")
    with open("gradle/libs.versions.toml", "w") as f:
        f.write(content)

with open("app/build.gradle.kts", "r") as f:
    content = f.read()

if "libs.androidx.work.runtime.ktx" not in content:
    content = content.replace("implementation(libs.androidx.datastore.preferences)", "implementation(libs.androidx.datastore.preferences)\n  implementation(libs.androidx.work.runtime.ktx)")
    with open("app/build.gradle.kts", "w") as f:
        f.write(content)

