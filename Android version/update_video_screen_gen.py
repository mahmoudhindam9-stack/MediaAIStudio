import os

path = "app/src/main/java/com/example/videoeditor/ui/VideoEditorScreen.kt"
with open(path, "r") as f:
    content = f.read()

if "GenerativeType" not in content:
    content = content.replace("import com.example.ai.video.SuggestedCut", "import com.example.ai.video.SuggestedCut\nimport com.example.ai.generative.GenerativeType\nimport androidx.compose.material3.OutlinedTextField\nimport androidx.compose.material3.Divider")
    
    # We need to add prompts state to VideoEditorScreen
    body = """
    val state by viewModel.state.collectAsState()
    val showAiDialog = remember { mutableStateOf(false) }
"""
    new_body = """
    val state by viewModel.state.collectAsState()
    val showAiDialog = remember { mutableStateOf(false) }
    val showPromptDialog = remember { mutableStateOf<GenerativeType?>(null) }
    val promptText = remember { mutableStateOf("") }
"""
    content = content.replace(body, new_body)
    
    # Add Advanced tools to the dialog
    ai_dialog_text = """
                    TextButton(onClick = { viewModel.runSmartReframe(); showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_smart_reframe)) }
                    TextButton(onClick = { viewModel.runEnhancement(); showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_video_enhance)) }
"""
    new_ai_dialog_text = ai_dialog_text + """
                    Divider()
                    Text(stringResource(com.example.R.string.ai_advanced_tools), color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp, modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = { showPromptDialog.value = GenerativeType.IMAGE_TO_VIDEO; showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_img_to_vid)) }
                    TextButton(onClick = { showPromptDialog.value = GenerativeType.VIDEO_TO_VIDEO; showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_vid_to_vid)) }
                    TextButton(onClick = { showPromptDialog.value = GenerativeType.VIDEO_EXTENSION; showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_vid_ext)) }
"""
    content = content.replace(ai_dialog_text, new_ai_dialog_text)
    
    # Add Prompt dialog
    prompt_dialog = """
    if (showPromptDialog.value != null) {
        AlertDialog(
            onDismissRequest = { showPromptDialog.value = null },
            title = { Text(showPromptDialog.value!!.name) },
            text = {
                OutlinedTextField(
                    value = promptText.value,
                    onValueChange = { promptText.value = it },
                    label = { Text(stringResource(com.example.R.string.ai_prompt_hint)) }
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.runGenerativeVideo(showPromptDialog.value!!, promptText.value)
                    showPromptDialog.value = null
                    promptText.value = ""
                }) { Text(stringResource(com.example.R.string.ai_generate)) }
            },
            dismissButton = {
                TextButton(onClick = { showPromptDialog.value = null }) { Text(stringResource(com.example.R.string.ai_close)) }
            }
        )
    }
"""
    # Insert it right before the preview dialog for cuts
    content = content.replace(
        "if (state.aiSuggestedCuts != null) {",
        prompt_dialog + "\n    if (state.aiSuggestedCuts != null) {"
    )
    
    with open(path, "w") as f:
        f.write(content)
