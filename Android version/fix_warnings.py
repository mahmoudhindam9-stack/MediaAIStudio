import os

# Fix CloudGenerativeClient
path_client = "app/src/main/java/com/example/ai/generative/CloudGenerativeClient.kt"
with open(path_client, "r") as f:
    content = f.read()

content = content.replace('json.optString("jobId", null)', 'json.optString("jobId", "").ifEmpty { null }')
content = content.replace('resultObj.optString("error", null)', 'resultObj.optString("error", "").ifEmpty { null }')
content = content.replace('resultObj.optString("outputUrl", null)', 'resultObj.optString("outputUrl", "").ifEmpty { null }')

with open(path_client, "w") as f:
    f.write(content)

# Fix VideoEditorScreen Divider
path_video = "app/src/main/java/com/example/videoeditor/ui/VideoEditorScreen.kt"
with open(path_video, "r") as f:
    content = f.read()
content = content.replace('Divider(', 'androidx.compose.material3.HorizontalDivider(')
with open(path_video, "w") as f:
    f.write(content)
