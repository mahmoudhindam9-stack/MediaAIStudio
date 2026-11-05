with open("app/src/main/java/com/example/ai/provider/OnDeviceAIProvider.kt", "r") as f:
    data = f.read()

data = data.replace(
    "LaMaInpaintingEngine(context).process(request.sourceUri, request.maskData, onProgress)",
    "com.example.ai.image.inpainting.LaMaInpaintingEngine(context).process(request.sourceUri, request.maskData, onProgress)"
)
data = data.replace(
    "RealEsrganUpscaleEngine(context).process(request.sourceUri, request.scaleFactor, onProgress)",
    "com.example.ai.image.upscale.RealEsrganUpscaleEngine(context).process(request.sourceUri, request.scaleFactor, onProgress)"
)
data = data.replace(
    "CpgaLowLightEngine(context).process(request.sourceUri, onProgress)",
    "com.example.ai.image.enhancement.CpgaLowLightEngine(context).process(request.sourceUri, onProgress)"
)

with open("app/src/main/java/com/example/ai/provider/OnDeviceAIProvider.kt", "w") as f:
    f.write(data)
