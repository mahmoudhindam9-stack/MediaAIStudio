#!/bin/bash
SCREEN_FILE="./app/src/main/java/com/example/photoeditor/PhotoEditorScreen.kt"

# Use previewBitmap if available
sed -i 's/val originalBitmap by viewModel.originalBitmap.collectAsState()/val originalBitmapState by viewModel.originalBitmap.collectAsState()\n    val previewBitmapState by viewModel.previewBitmap.collectAsState()\n    val originalBitmap = previewBitmapState ?: originalBitmapState/g' $SCREEN_FILE

