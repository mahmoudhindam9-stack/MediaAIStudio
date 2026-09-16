import os

path = "app/src/main/java/com/example/ai/model/AICapability.kt"
with open(path, "r") as f:
    content = f.read()

if "VIDEO_OBJECT_DETECTION" not in content:
    content = content.replace("RESTYLE", "RESTYLE,\n    VIDEO_OBJECT_DETECTION,\n    VIDEO_OBJECT_TRACKING,\n    SMART_CUT,\n    AUTO_CAPTIONS,\n    VIDEO_ENHANCEMENT,\n    SMART_REFRAME")
    with open(path, "w") as f:
        f.write(content)
