#!/bin/bash
VM_FILE="./app/src/main/java/com/example/photoeditor/PhotoEditorViewModel.kt"
# We'll use sed to insert the AI logic inside the view model class.

sed -i '/class PhotoEditorViewModel/i import com.example.ai.core.*\nimport com.example.ai.provider.*\nimport com.example.ai.image.*\nimport com.example.ai.assistant.*\n' $VM_FILE

sed -i '/val state = MutableStateFlow(EditorState())/a \
    val aiProgress = MutableStateFlow<AIProgress?>(null)\
    val aiError = MutableStateFlow<String?>(null)\
    val aiEngine = AIImageEngine(AIProviderManager())\
    val aiAssistant = AIAssistant()\
    val previewAiResultUri = MutableStateFlow<String?>(null)\
' $VM_FILE

sed -i '/fun moveSticker/a \
    fun processAITool(request: AIRequest) {\
        viewModelScope.launch(Dispatchers.IO) {\
            aiProgress.value = AIProgress(0f, "Preparing...")\
            aiError.value = null\
            val result = aiEngine.processImage(request) { progress ->\
                aiProgress.value = progress\
            }\
            aiProgress.value = null\
            when (result) {\
                is AIResult.Success -> {\
                    previewAiResultUri.value = result.outputUri\
                }\
                is AIResult.Error -> {\
                    aiError.value = result.error.message\
                }\
            }\
        }\
    }\
    fun processAssistantInstruction(instruction: String) {\
        viewModelScope.launch(Dispatchers.IO) {\
            val action = aiAssistant.processInstruction(instruction)\
            when (action) {\
                is EditorAction.AdjustBrightness -> updateState { it.copy(brightness = it.brightness + action.value) }\
                is EditorAction.AdjustContrast -> updateState { it.copy(contrast = it.contrast + action.value) }\
                is EditorAction.AdjustSaturation -> updateState { it.copy(saturation = it.saturation + action.value) }\
                is EditorAction.AIBackgroundRemoval -> processAITool(AIRequest.BackgroundRemoval(state.value.uriString))\
                is EditorAction.AIEnhancement -> processAITool(AIRequest.Enhance(state.value.uriString, "auto"))\
                else -> { aiError.value = "Unrecognized instruction" }\
            }\
            if (action !is EditorAction.AIBackgroundRemoval && action !is EditorAction.AIEnhancement && action !is EditorAction.Unknown) {\
                commitState()\
            }\
        }\
    }\
    fun acceptAiResult() {\
        previewAiResultUri.value?.let { \
            setUri(it)\
            previewAiResultUri.value = null\
        }\
    }\
    fun discardAiResult() {\
        previewAiResultUri.value = null\
    }\
    fun clearAiError() {\
        aiError.value = null\
    }\
' $VM_FILE
