import os

path = "app/src/main/java/com/example/ai/ui/AIToolsScreen.kt"
with open(path, "r") as f:
    content = f.read()

if "AICapability.GENERATIVE_FILL" not in content:
    old_tools = """
            val tools = listOf(
                Pair(R.string.ai_background_removal, AICapability.BACKGROUND_REMOVAL),
                Pair(R.string.ai_object_detection, AICapability.OBJECT_DETECTION),
                Pair(R.string.ai_object_removal, AICapability.OBJECT_REMOVAL),
                Pair(R.string.ai_upscale, AICapability.UPSCALE),
                Pair(R.string.ai_enhance, AICapability.ENHANCEMENT),
                Pair(R.string.ai_restyle, AICapability.RESTYLE)
            )
"""
    new_tools = """
            val tools = listOf(
                Pair(R.string.ai_background_removal, AICapability.BACKGROUND_REMOVAL),
                Pair(R.string.ai_object_detection, AICapability.OBJECT_DETECTION),
                Pair(R.string.ai_object_removal, AICapability.OBJECT_REMOVAL),
                Pair(R.string.ai_upscale, AICapability.UPSCALE),
                Pair(R.string.ai_enhance, AICapability.ENHANCEMENT),
                Pair(R.string.ai_restyle, AICapability.RESTYLE),
                Pair(R.string.ai_gen_fill, AICapability.GENERATIVE_FILL),
                Pair(R.string.ai_img_to_img, AICapability.IMAGE_TO_IMAGE)
            )
"""
    content = content.replace(old_tools, new_tools)
    
    # Also add the missing enums for AICapabilityStatus to when statement
    old_when = """
                        val (statusText, statusColor) = when (status) {
                            AICapabilityStatus.AVAILABLE -> "Available" to Color.Green
                            AICapabilityStatus.UNAVAILABLE -> "Unavailable" to Color.Red
                            AICapabilityStatus.REQUIRES_MODEL -> "Requires Model" to Color.Yellow
                            AICapabilityStatus.REQUIRES_CLOUD -> "Requires Cloud" to Color.Yellow
                            AICapabilityStatus.DEVICE_NOT_SUPPORTED -> "Not Supported" to Color.Red
                        }
"""
    new_when = """
                        val (statusText, statusColor) = when (status) {
                            AICapabilityStatus.AVAILABLE -> "Available" to Color.Green
                            AICapabilityStatus.UNAVAILABLE -> "Unavailable" to Color.Red
                            AICapabilityStatus.REQUIRES_MODEL -> "Requires Model" to Color.Yellow
                            AICapabilityStatus.REQUIRES_CLOUD -> "Requires Cloud" to Color.Yellow
                            AICapabilityStatus.DEVICE_NOT_SUPPORTED -> "Not Supported" to Color.Red
                            AICapabilityStatus.REQUIRES_PROVIDER -> "Requires Configured Provider" to Color.Yellow
                            AICapabilityStatus.PROCESSING -> "Processing" to Color.Cyan
                            AICapabilityStatus.FAILED -> "Failed" to Color.Red
                            AICapabilityStatus.CANCELLED -> "Cancelled" to Color.Gray
                        }
"""
    content = content.replace(old_when, new_when)
    
    with open(path, "w") as f:
        f.write(content)

