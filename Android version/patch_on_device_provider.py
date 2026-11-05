with open("app/src/main/java/com/example/ai/provider/OnDeviceAIProvider.kt", "r") as f:
    code = f.read()

imports = """
import com.example.ai.image.inpainting.LaMaInpaintingEngine
import com.example.ai.image.upscale.RealEsrganUpscaleEngine
import com.example.ai.image.enhancement.CpgaLowLightEngine
"""

code = code.replace("import com.example.ai.core.*", "import com.example.ai.core.*\n" + imports)

lama_case = """
                is AIRequest.ObjectRemoval -> {
                    LaMaInpaintingEngine(context).process(request.sourceUri, request.maskData, onProgress)
                }
                is AIRequest.Upscale -> {
                    RealEsrganUpscaleEngine(context).process(request.sourceUri, request.scaleFactor, onProgress)
                }
                is AIRequest.Enhance -> {
                    if (request.enhanceType == "low_light") {
                        CpgaLowLightEngine(context).process(request.sourceUri, onProgress)
                    } else {
                        AIResult.Error(AIError.ModelUnavailable)
                    }
                }
"""

code = code.replace("else -> AIResult.Error(AIError.ModelUnavailable)", lama_case + "                else -> AIResult.Error(AIError.ModelUnavailable)")

with open("app/src/main/java/com/example/ai/provider/OnDeviceAIProvider.kt", "w") as f:
    f.write(code)
