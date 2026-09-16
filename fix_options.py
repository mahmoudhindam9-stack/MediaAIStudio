with open("app/src/main/java/com/example/ai/image/enhancement/CpgaLowLightEngine.kt", "r") as f:
    data = f.read()

data = data.replace(
    "val options = Interpreter.Options()",
    "val options = org.tensorflow.lite.InterpreterApi.Options()"
)

with open("app/src/main/java/com/example/ai/image/enhancement/CpgaLowLightEngine.kt", "w") as f:
    f.write(data)
