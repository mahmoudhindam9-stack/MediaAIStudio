#!/bin/bash
SCREEN_FILE="./app/src/main/java/com/example/photoeditor/PhotoEditorScreen.kt"

sed -i '/import androidx.compose.runtime.getValue/a \
import androidx.compose.runtime.setValue\
import androidx.compose.runtime.remember\
import androidx.compose.runtime.mutableStateOf\
' $SCREEN_FILE
