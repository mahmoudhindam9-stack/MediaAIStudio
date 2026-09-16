import os

def fix_strings(path):
    with open(path, "r") as f:
        content = f.read()
    
    # We want to replace the SECOND occurrence of <string name="ai_enhance"> with ai_video_enhance
    # But since it's easier, let's just find and replace the video tools block
    content = content.replace('<string name="ai_enhance">Enhance</string>', '<string name="ai_video_enhance">Enhance</string>')
    content = content.replace('<string name="ai_enhance">تحسين</string>', '<string name="ai_video_enhance">تحسين</string>')
    
    with open(path, "w") as f:
        f.write(content)

fix_strings("app/src/main/res/values/strings.xml")
fix_strings("app/src/main/res/values-ar/strings.xml")

# And update the UI file to use ai_video_enhance
screen_path = "app/src/main/java/com/example/videoeditor/ui/VideoEditorScreen.kt"
with open(screen_path, "r") as f:
    content = f.read()
content = content.replace("com.example.R.string.ai_enhance", "com.example.R.string.ai_video_enhance")
with open(screen_path, "w") as f:
    f.write(content)
