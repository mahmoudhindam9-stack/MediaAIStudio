#!/bin/bash
SCREEN_FILE="./app/src/main/java/com/example/photoeditor/PhotoEditorScreen.kt"

# We use sed to insert the AI block before the closing brace of the when expression.
# Note: the when block ends at the '}' just before 'ScrollableTabRow('.

sed -i '/"STICKER" -> {/,/}/!b;//!d;/"STICKER" -> {/i \
                "STICKER" -> {\
                    LazyRow {\
                        items(listOf("😀", "❤️", "🔥", "🌟", "🎉", "✨", "😎", "🐱")) { emoji ->\
                            Text(text = emoji, fontSize = 32.sp, modifier = Modifier\
                                .padding(8.dp)\
                                .clickable { viewModel.addSticker(emoji) })\
                        }\
                    }\
                }\
                "AI" -> {\
                    val aiProgress by viewModel.aiProgress.collectAsState()\
                    val aiError by viewModel.aiError.collectAsState()\
                    val previewAiUri by viewModel.previewAiResultUri.collectAsState()\
                    \
                    if (aiProgress != null) {\
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {\
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))\
                            Text(aiProgress?.statusMessage ?: stringResource(R.string.ai_processing), color = Color.White)\
                        }\
                    } else if (previewAiUri != null) {\
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {\
                            Text("Previewing AI Result", color = Color.Yellow)\
                            Row {\
                                Button(onClick = { viewModel.acceptAiResult() }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_apply)) }\
                                Button(onClick = { viewModel.discardAiResult() }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_cancel)) }\
                            }\
                        }\
                    } else {\
                        LazyRow(modifier = Modifier.fillMaxWidth()) {\
                            item { Button(onClick = { viewModel.processAITool(AIRequest.BackgroundRemoval(state.uriString)) }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_background_removal)) } }\
                            item { Button(onClick = { viewModel.processAITool(AIRequest.Enhance(state.uriString, "auto")) }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_enhance)) } }\
                            item { Button(onClick = { viewModel.processAITool(AIRequest.Upscale(state.uriString, 2)) }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_upscale)) } }\
                            item { \
                                var prompt by remember { mutableStateOf("") }\
                                Row(verticalAlignment = Alignment.CenterVertically) {\
                                    OutlinedTextField(\
                                        value = prompt,\
                                        onValueChange = { prompt = it },\
                                        label = { Text(stringResource(R.string.ai_prompt_hint), color = Color.White) },\
                                        modifier = Modifier.width(200.dp).padding(4.dp),\
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),\
                                        singleLine = true\
                                    )\
                                    Button(onClick = { if (prompt.isNotBlank()) viewModel.processAssistantInstruction(prompt) }, modifier = Modifier.padding(4.dp)) { Text(stringResource(R.string.ai_assistant)) }\
                                }\
                            }\
                        }\
                        if (aiError != null) {\
                            Text(aiError!!, color = Color.Red, modifier = Modifier.padding(4.dp))\
                        }\
                    }\
                }\
' $SCREEN_FILE

# And update the ScrollableTabRow selectedTabIndex
sed -i 's/listOf("ADJUST", "FILTERS", "CROP", "TEXT", "DRAW", "STICKER")/listOf("ADJUST", "FILTERS", "CROP", "TEXT", "DRAW", "STICKER", "AI")/g' $SCREEN_FILE

