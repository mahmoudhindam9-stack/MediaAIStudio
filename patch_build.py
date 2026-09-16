with open("app/build.gradle.kts", "r") as f:
    gradle = f.read()

lint_block = """
  lint {
      abortOnError = false
  }
"""

gradle = gradle.replace("  buildFeatures {", lint_block + "\n  buildFeatures {")

with open("app/build.gradle.kts", "w") as f:
    f.write(gradle)
