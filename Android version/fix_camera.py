with open("app/src/main/java/com/example/camera/CameraScreen.kt", "r") as f:
    text = f.read()

# Remove the two imports from the very top
text = text.replace("import androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.ui.text.font.FontWeight\n", "")

# Add them after package com.example.camera
text = text.replace("package com.example.camera", "package com.example.camera\n\nimport androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.ui.text.font.FontWeight")

with open("app/src/main/java/com/example/camera/CameraScreen.kt", "w") as f:
    f.write(text)
