import os

for path in [
    "app/src/main/java/com/example/ai/image/inpainting/LaMaInpaintingEngine.kt",
    "app/src/main/java/com/example/ai/image/upscale/RealEsrganUpscaleEngine.kt",
    "app/src/main/java/com/example/ai/image/enhancement/CpgaLowLightEngine.kt"
]:
    with open(path, "r") as f:
        code = f.read()
    code = code.replace("com.example.ai.provider.AIProviderType.ON_DEVICE", "com.example.ai.core.AIProviderType.ON_DEVICE")
    with open(path, "w") as f:
        f.write(code)
