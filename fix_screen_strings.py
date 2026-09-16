import os

path = "app/src/main/java/com/example/videoeditor/ui/VideoEditorScreen.kt"
with open(path, "r") as f:
    content = f.read()

replacements = {
    '"AI Video Tools"': 'stringResource(com.example.R.string.ai_video_tools)',
    '"Smart Cut"': 'stringResource(com.example.R.string.ai_smart_cut)',
    '"Auto Captions"': 'stringResource(com.example.R.string.ai_auto_captions)',
    '"Object Tracking"': 'stringResource(com.example.R.string.ai_object_tracking)',
    '"Smart Reframe"': 'stringResource(com.example.R.string.ai_smart_reframe)',
    '"Enhance"': 'stringResource(com.example.R.string.ai_enhance)',
    '"Close"': 'stringResource(com.example.R.string.ai_close)',
    '"Review Smart Cuts"': 'stringResource(com.example.R.string.ai_review_cuts)',
    '"AI has suggested ${state.aiSuggestedCuts!!.size} cuts based on structural analysis. Apply these changes?"': 'stringResource(com.example.R.string.ai_cuts_suggested_msg, state.aiSuggestedCuts!!.size)',
    '"Apply"': 'stringResource(com.example.R.string.ai_apply)',
    '"Reject"': 'stringResource(com.example.R.string.ai_reject)'
}

for k, v in replacements.items():
    content = content.replace(k, v)

# Ensure stringResource is imported
if "import androidx.compose.ui.res.stringResource" not in content:
    content = content.replace("import androidx.compose.runtime.mutableStateOf", "import androidx.compose.ui.res.stringResource\nimport androidx.compose.runtime.mutableStateOf")

with open(path, "w") as f:
    f.write(content)
