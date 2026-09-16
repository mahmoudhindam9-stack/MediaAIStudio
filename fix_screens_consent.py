import os

def fix_photo():
    path = "app/src/main/java/com/example/photoeditor/PhotoEditorScreen.kt"
    with open(path, "r") as f:
        content = f.read()

    if "val showConsentDialog = remember { mutableStateOf<GenerativeType?>(null) }" not in content:
        # Add state
        body = "val showPromptDialog = remember { mutableStateOf<GenerativeType?>(null) }"
        new_body = """
    val showConsentDialog = remember { mutableStateOf<GenerativeType?>(null) }
    val showPromptDialog = remember { mutableStateOf<GenerativeType?>(null) }"""
        content = content.replace(body, new_body)

        # Update button clicks to show consent dialog first instead of prompt dialog directly
        content = content.replace("onClick = { showPromptDialog.value = GenerativeType.FILL }", "onClick = { showConsentDialog.value = GenerativeType.FILL }")
        content = content.replace("onClick = { showPromptDialog.value = GenerativeType.OBJECT_REMOVAL }", "onClick = { showConsentDialog.value = GenerativeType.OBJECT_REMOVAL }")
        content = content.replace("onClick = { showPromptDialog.value = GenerativeType.IMAGE_TO_IMAGE }", "onClick = { showConsentDialog.value = GenerativeType.IMAGE_TO_IMAGE }")
        
        # Add consent dialog
        consent_dialog = """
    if (showConsentDialog.value != null) {
        AlertDialog(
            onDismissRequest = { showConsentDialog.value = null },
            title = { Text(stringResource(com.example.R.string.ai_advanced_tools)) },
            text = { Text(stringResource(com.example.R.string.ai_cloud_privacy_consent)) },
            confirmButton = {
                TextButton(onClick = { 
                    val type = showConsentDialog.value
                    showConsentDialog.value = null
                    showPromptDialog.value = type
                }) { Text("Agree") }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog.value = null }) { Text(stringResource(com.example.R.string.ai_close)) }
            }
        )
    }
"""
        content = content.replace("if (showPromptDialog.value != null) {", consent_dialog + "\n    if (showPromptDialog.value != null) {")
        
        with open(path, "w") as f:
            f.write(content)

def fix_video():
    path = "app/src/main/java/com/example/videoeditor/ui/VideoEditorScreen.kt"
    with open(path, "r") as f:
        content = f.read()

    if "val showConsentDialog = remember { mutableStateOf<GenerativeType?>(null) }" not in content:
        # Add state
        body = "val showPromptDialog = remember { mutableStateOf<GenerativeType?>(null) }"
        new_body = """
    val showConsentDialog = remember { mutableStateOf<GenerativeType?>(null) }
    val showPromptDialog = remember { mutableStateOf<GenerativeType?>(null) }"""
        content = content.replace(body, new_body)

        # Update button clicks
        content = content.replace("showPromptDialog.value = GenerativeType.IMAGE_TO_VIDEO", "showConsentDialog.value = GenerativeType.IMAGE_TO_VIDEO")
        content = content.replace("showPromptDialog.value = GenerativeType.VIDEO_TO_VIDEO", "showConsentDialog.value = GenerativeType.VIDEO_TO_VIDEO")
        content = content.replace("showPromptDialog.value = GenerativeType.VIDEO_EXTENSION", "showConsentDialog.value = GenerativeType.VIDEO_EXTENSION")
        
        # Add consent dialog
        consent_dialog = """
    if (showConsentDialog.value != null) {
        AlertDialog(
            onDismissRequest = { showConsentDialog.value = null },
            title = { Text(stringResource(com.example.R.string.ai_advanced_tools)) },
            text = { Text(stringResource(com.example.R.string.ai_cloud_privacy_consent)) },
            confirmButton = {
                TextButton(onClick = { 
                    val type = showConsentDialog.value
                    showConsentDialog.value = null
                    showPromptDialog.value = type
                }) { Text("Agree") }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog.value = null }) { Text(stringResource(com.example.R.string.ai_close)) }
            }
        )
    }
"""
        content = content.replace("if (showPromptDialog.value != null) {", consent_dialog + "\n    if (showPromptDialog.value != null) {")
        
        with open(path, "w") as f:
            f.write(content)

fix_photo()
fix_video()
