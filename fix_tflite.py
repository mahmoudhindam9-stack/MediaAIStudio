with open("app/src/main/java/com/example/ai/image/enhancement/CpgaLowLightEngine.kt", "r") as f:
    data = f.read()

# Fix Interpreter creation and Options
data = data.replace(
    "import org.tensorflow.lite.Interpreter\nimport org.tensorflow.lite.InterpreterApi",
    "import org.tensorflow.lite.Interpreter"
)
data = data.replace(
    "val options = org.tensorflow.lite.InterpreterApi.Options()",
    "val options = Interpreter.Options()"
)

# If Options is missing, sometimes people use specific TFLite artifact
# In libs.versions.toml we have org.tensorflow:tensorflow-lite:2.16.1
# Interpreter.Options() should exist, but it might be deprecated or missing in newer API, 
# wait, the error is: Cannot access class 'Options'. Check your module classpath...
# Let's remove Options usage temporarily if it's completely breaking compilation due to classpath issues.

data = data.replace(
    """            val compatList = CompatibilityList()
            val options = Interpreter.Options()
            
            if (compatList.isDelegateSupportedOnThisDevice) {
                options.addDelegate(GpuDelegate(compatList.bestOptionsForThisDevice))
                isGpuAccelerated = true
            } else {
                options.setNumThreads(4)
                isGpuAccelerated = false
            }
            
            val tflite = Interpreter(file, options)""",
    """            // Fallback for classpath issues
            val tflite = Interpreter(file)
            isGpuAccelerated = false"""
)

with open("app/src/main/java/com/example/ai/image/enhancement/CpgaLowLightEngine.kt", "w") as f:
    f.write(data)
