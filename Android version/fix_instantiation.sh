#!/bin/bash
VM="app/src/main/java/com/example/photoeditor/PhotoEditorViewModel.kt"
SCREEN="app/src/main/java/com/example/ai/ui/AIToolsScreen.kt"

sed -i 's/AIProviderManager()/AIProviderManager(application)/g' $VM
sed -i 's/val providerManager = remember { AIProviderManager() }/val context = androidx.compose.ui.platform.LocalContext.current\n    val providerManager = remember { AIProviderManager(context) }/g' $SCREEN

