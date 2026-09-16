import os

path = "app/src/main/java/com/example/videoeditor/VideoEditorViewModel.kt"
with open(path, "r") as f:
    content = f.read()

replacements = {
    '"Running Object Tracking..."': 'getApplication<Application>().getString(com.example.R.string.ai_msg_running_tracking)',
    '"Tracking Complete"': 'getApplication<Application>().getString(com.example.R.string.ai_msg_tracking_complete)',
    '"Analyzing for Smart Cuts..."': 'getApplication<Application>().getString(com.example.R.string.ai_msg_analyzing_cuts)',
    '"Smart Cuts Suggested"': 'getApplication<Application>().getString(com.example.R.string.ai_msg_cuts_suggested)',
    '"Generating Captions..."': 'getApplication<Application>().getString(com.example.R.string.ai_msg_generating_captions)',
    '"Captions Generated"': 'getApplication<Application>().getString(com.example.R.string.ai_msg_captions_generated)',
    '"Generating Reframe Paths..."': 'getApplication<Application>().getString(com.example.R.string.ai_msg_generating_reframe)',
    '"Smart Reframe generated ${result.cropPaths.size} paths."': 'getApplication<Application>().getString(com.example.R.string.ai_msg_reframe_generated, result.cropPaths.size)',
    '"Enhancing Video..."': 'getApplication<Application>().getString(com.example.R.string.ai_msg_enhancing)'
}

for k, v in replacements.items():
    content = content.replace(k, v)

with open(path, "w") as f:
    f.write(content)
