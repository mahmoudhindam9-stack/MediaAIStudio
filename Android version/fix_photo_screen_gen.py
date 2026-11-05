import os

path = "app/src/main/java/com/example/photoeditor/PhotoEditorScreen.kt"
with open(path, "r") as f:
    content = f.read()

if "GenerativeType" not in content:
    content = content.replace("import com.example.ai.core.AIError", "import com.example.ai.core.AIError\nimport com.example.ai.generative.GenerativeType\nimport androidx.compose.material3.OutlinedTextField\nimport androidx.compose.material3.Divider")
    
    # Add prompt state
    body = """
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val originalBitmap by viewModel.originalBitmap.collectAsState()
"""
    new_body = """
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val originalBitmap by viewModel.originalBitmap.collectAsState()
    
    val showPromptDialog = remember { mutableStateOf<GenerativeType?>(null) }
    val promptText = remember { mutableStateOf("") }
"""
    content = content.replace(body, new_body)

    # Advanced AI dialog options
    ai_buttons = """
                                        }
                                        item {
                                            if (state.isProcessingAi) {
                                                CircularProgressIndicator(color = Color.Cyan)
                                            }
                                        }
"""
    new_ai_buttons = """
                                        }
                                        item {
                                            Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                                            Text(stringResource(com.example.R.string.ai_advanced_tools), color = Color.Gray, fontSize = 12.sp)
                                        }
                                        item {
                                            Button(
                                                onClick = { showPromptDialog.value = GenerativeType.FILL },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta)
                                            ) {
                                                Text(stringResource(com.example.R.string.ai_gen_fill), color = Color.White)
                                            }
                                        }
                                        item {
                                            Button(
                                                onClick = { showPromptDialog.value = GenerativeType.OBJECT_REMOVAL },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta)
                                            ) {
                                                Text(stringResource(com.example.R.string.ai_obj_removal), color = Color.White)
                                            }
                                        }
                                        item {
                                            Button(
                                                onClick = { showPromptDialog.value = GenerativeType.IMAGE_TO_IMAGE },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta)
                                            ) {
                                                Text(stringResource(com.example.R.string.ai_img_to_img), color = Color.White)
                                            }
                                        }
                                        item {
                                            if (state.isProcessingAi) {
                                                CircularProgressIndicator(color = Color.Cyan)
                                            }
                                        }
"""
    content = content.replace(ai_buttons, new_ai_buttons)

    # Add Prompt dialog rendering
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
                    viewModel.runGenerativeImage(showPromptDialog.value!!, promptText.value)
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
    content = content.replace(
        "if (previewAiResultUri != null) {",
        prompt_dialog + "\n    if (previewAiResultUri != null) {"
    )

    with open(path, "w") as f:
        f.write(content)

