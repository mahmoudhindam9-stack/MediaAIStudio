import os

for path in [
    "app/src/main/java/com/example/ai/image/inpainting/LaMaInpaintingEngine.kt",
    "app/src/main/java/com/example/ai/image/upscale/RealEsrganUpscaleEngine.kt",
    "app/src/main/java/com/example/ai/image/enhancement/CpgaLowLightEngine.kt"
]:
    with open(path, "r") as f:
        code = f.read()
    code = code.replace("com.example.ai.provider.AIProviderType.ON_DEVICE", "com.example.ai.core.AIProviderType.ON_DEVICE")
    code = code.replace("com.example.ai.core.AIProviderType.ON_DEVICE", "com.example.ai.provider.AIProviderType.ON_DEVICE")
    
    # Actually wait, AIProviderType is probably in com.example.ai.provider
    code = code.replace("com.example.ai.provider.AIProviderType.ON_DEVICE", "com.example.ai.provider.AIProviderType.ON_DEVICE")
    with open(path, "w") as f:
        f.write(code)

with open("app/src/main/java/com/example/ai/ui/AIToolsScreen.kt", "r") as f:
    screen_code = f.read()

screen_code = screen_code.replace("val context = androidx.compose.ui.platform.LocalContext.current\n    val context = androidx.compose.ui.platform.LocalContext.current", "val context = androidx.compose.ui.platform.LocalContext.current")
screen_code = screen_code.replace("val context = LocalContext.current\n    val context = androidx.compose.ui.platform.LocalContext.current", "val context = LocalContext.current")
screen_code = screen_code.replace("    val context = androidx.compose.ui.platform.LocalContext.current\n    val modelManager = remember { AppModelManager(context) }", "    // context is already defined\n    val modelManager = remember { AppModelManager(context) }")

with open("app/src/main/java/com/example/ai/ui/AIToolsScreen.kt", "w") as f:
    f.write(screen_code)
