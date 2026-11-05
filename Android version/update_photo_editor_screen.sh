#!/bin/bash
SCREEN_FILE="./app/src/main/java/com/example/photoeditor/PhotoEditorScreen.kt"

# First, import the AI tools strings and AIRequest stuff
sed -i '/import androidx.compose.ui.unit.dp/a \
import com.example.ai.core.*\
' $SCREEN_FILE

# Find the tabs definition and add AI
sed -i 's/"STICKER" to R.string.editor_sticker/"STICKER" to R.string.editor_sticker,\n                "AI" to R.string.ai_tools/g' $SCREEN_FILE

