#!/bin/bash
SCREEN="app/src/main/java/com/example/photoeditor/PhotoEditorScreen.kt"

sed -i '/item { Button(onClick = { viewModel.processAITool(AIRequest.BackgroundRemoval/a \
                            item { Button(onClick = { viewModel.processAITool(AIRequest.DetectObjects(state.uriString)) }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_object_detection)) } }\
' $SCREEN

