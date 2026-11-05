import re

# Fix CameraScreen.kt
with open("app/src/main/java/com/example/camera/CameraScreen.kt", "r") as f:
    cam_text = f.read()
cam_text = "import androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.ui.text.font.FontWeight\n" + cam_text
with open("app/src/main/java/com/example/camera/CameraScreen.kt", "w") as f:
    f.write(cam_text)

# Fix AppComponents.kt
with open("app/src/main/java/com/example/ui/components/AppComponents.kt", "r") as f:
    comp_text = f.read()
comp_text = comp_text.replace(
    'Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,',
    'Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,'
)
comp_text = comp_text.replace(
    'import androidx.compose.material.icons.filled.Add',
    'import androidx.compose.material.icons.filled.Add\nimport androidx.compose.material.icons.automirrored.filled.ArrowBack'
)
with open("app/src/main/java/com/example/ui/components/AppComponents.kt", "w") as f:
    f.write(comp_text)

# Fix VideoEditorScreen.kt
with open("app/src/main/java/com/example/videoeditor/ui/VideoEditorScreen.kt", "r") as f:
    vid_text = f.read()
# The issue is probably missing RowScope or the actions lambda definition in AppTopBar is not being matched correctly.
vid_text = vid_text.replace('AppTopBar(', 'com.example.ui.components.AppTopBar(')
with open("app/src/main/java/com/example/videoeditor/ui/VideoEditorScreen.kt", "w") as f:
    f.write(vid_text)
