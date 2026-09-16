import os

path = "app/src/main/java/com/example/ai/model/ModelManager.kt"
with open(path, "r") as f:
    content = f.read()

if "AICapability.VIDEO_OBJECT_DETECTION" not in content:
    lines = content.split("\n")
    new_lines = []
    for line in lines:
        if "AICapability.ENHANCEMENT, AICapability.RESTYLE," in line:
            new_lines.append(line)
            new_lines.append("            AICapability.VIDEO_OBJECT_DETECTION -> AICapabilityStatus.AVAILABLE")
            new_lines.append("            AICapability.VIDEO_OBJECT_TRACKING -> AICapabilityStatus.AVAILABLE")
            new_lines.append("            AICapability.SMART_CUT -> AICapabilityStatus.AVAILABLE")
            new_lines.append("            AICapability.SMART_REFRAME -> AICapabilityStatus.AVAILABLE")
            new_lines.append("            AICapability.AUTO_CAPTIONS -> AICapabilityStatus.REQUIRES_CLOUD")
            new_lines.append("            AICapability.VIDEO_ENHANCEMENT -> AICapabilityStatus.DEVICE_NOT_SUPPORTED")
        else:
            new_lines.append(line)
    with open(path, "w") as f:
        f.write("\n".join(new_lines))
