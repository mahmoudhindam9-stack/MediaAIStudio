import re

with open("gradle/libs.versions.toml", "r") as f:
    toml = f.read()

# Let's downgrade onnxruntime if 1.18.1 is missing or update group
toml = toml.replace('onnxruntime = "1.18.1"', 'onnxruntime = "1.18.0"')

with open("gradle/libs.versions.toml", "w") as f:
    f.write(toml)
