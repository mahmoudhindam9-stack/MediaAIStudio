import os

path1 = "app/src/main/java/com/example/photoeditor/PhotoEditorViewModel.kt"
with open(path1, "r") as f:
    content1 = f.read()

content1 = content1.replace("s.currentImageUri ?: return", "s.uriString.ifEmpty { null } ?: return")

with open(path1, "w") as f:
    f.write(content1)

path2 = "app/src/main/java/com/example/videoeditor/ui/VideoEditorScreen.kt"
with open(path2, "r") as f:
    content2 = f.read()

if "import com.example.ai.generative.GenerativeType" not in content2:
    content2 = content2.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport com.example.ai.generative.GenerativeType")
    content2 = content2.replace("import androidx.compose.material3.Divider", "")
    content2 = content2.replace("import com.example.ai.video.SuggestedCut\n", "")

with open(path2, "w") as f:
    f.write(content2)
