
package com.example.photoeditor
import kotlinx.coroutines.flow.first

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

import com.example.ai.core.*
import com.example.ai.provider.*
import com.example.ai.image.*
import com.example.ai.assistant.*
import com.example.ai.generative.*
import com.example.ai.model.AppModelManager

class PhotoEditorViewModel(application: Application) : AndroidViewModel(application) {
    val state = MutableStateFlow(EditorState())
    val aiProgress = MutableStateFlow<AIProgress?>(null)
    val aiError = MutableStateFlow<String?>(null)
    val aiEngine = AIImageEngine(AIProviderManager(application))
    val generativeEngine = GenerativeEngine(application)
    val aiAssistant = AIAssistant()
    val previewAiResultUri = MutableStateFlow<String?>(null)
    val previewBitmap = MutableStateFlow<Bitmap?>(null)
    val modelManager = AppModelManager(application)
    val modelStates = modelManager.artifacts.states
    private var isProcessing = false

    val originalBitmap = MutableStateFlow<Bitmap?>(null)
    
    private val history = mutableListOf<EditorState>()
    private var historyIndex = -1
    
    fun setUri(uriString: String) {
        state.value = EditorState(uriString = uriString)
        pushHistory()
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val uri = Uri.parse(uriString)
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            originalBitmap.value = bitmap
        }
    }
    
    fun pushHistory() {
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(state.value.copy())
        historyIndex = history.size - 1
    }
    
    fun undo() {
        if (canUndo()) {
            historyIndex--
            state.value = history[historyIndex].copy()
        }
    }
    
    fun redo() {
        if (canRedo()) {
            historyIndex++
            state.value = history[historyIndex].copy()
        }
    }
    
    fun canUndo(): Boolean = historyIndex > 0
    fun canRedo(): Boolean = historyIndex < history.size - 1
    
    fun updateState(transform: (EditorState) -> EditorState) {
        state.value = transform(state.value)
    }

    fun commitState() {
        pushHistory()
    }
    
    fun startDrawing(startPoint: PointF, color: Int = android.graphics.Color.RED, strokeWidth: Float = 5f) {
        val newDrawing = Drawing(path = listOf(startPoint), color = color, strokeWidth = strokeWidth)
        updateState { it.copy(drawings = it.drawings + newDrawing) }
    }

    fun addDrawingPoint(point: PointF) {
        val drawings = state.value.drawings.toMutableList()
        if (drawings.isNotEmpty()) {
            val last = drawings.last()
            drawings[drawings.size - 1] = last.copy(path = last.path + point)
            state.value = state.value.copy(drawings = drawings)
        }
    }

    fun endDrawing() {
        commitState()
    }

    fun addText(text: String) {
        val newText = TextOverlay(text = text, x = 0.5f, y = 0.5f, color = android.graphics.Color.WHITE, size = 24f)
        updateState { it.copy(texts = it.texts + newText) }
        commitState()
    }

    fun updateText(id: String, text: String) {
        val texts = state.value.texts.map {
            if (it.id == id) it.copy(text = text) else it
        }
        state.value = state.value.copy(texts = texts)
    }

    fun moveText(id: String, dx: Float, dy: Float) {
        val texts = state.value.texts.map {
            if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
        }
        state.value = state.value.copy(texts = texts)
    }

    fun addSticker(emoji: String) {
        val newSticker = Sticker(emoji = emoji, x = 0.5f, y = 0.5f, scale = 1f)
        updateState { it.copy(stickers = it.stickers + newSticker) }
        commitState()
    }

    fun moveSticker(id: String, dx: Float, dy: Float) {
        val stickers = state.value.stickers.map {
            if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
        }
        state.value = state.value.copy(stickers = stickers)
    }

    fun downloadModel(id: String) {
        if (isProcessing) return
        viewModelScope.launch {
            aiProgress.value = AIProgress(0f, "Starting download...")
            aiError.value = null
            modelManager.downloadModel(id) { pct ->
                aiProgress.value = AIProgress(pct / 100f, "Downloading model: $pct%")
            }.onSuccess {
                aiProgress.value = null
            }.onFailure { e ->
                aiProgress.value = null
                aiError.value = "Failed to download model: ${e.message}"
            }
        }
    }

    fun processAITool(request: AIRequest) {
        if (isProcessing) return
        viewModelScope.launch(Dispatchers.IO) {
            isProcessing = true
            try {
                // Pre-check for on-device models
                val requiredModel = when (request) {
                    is AIRequest.ObjectRemoval -> "llama/inpainting_lama_2025jan"
                    is AIRequest.Upscale -> "realesrgan_x2plus"
                    is AIRequest.Enhance -> "cpga_fp16"
                    else -> null
                }
                if (requiredModel != null && !modelManager.isInstalled(requiredModel)) {
                    aiError.value = "Model is not installed. Please download it first."
                    return@launch
                }

                aiProgress.value = AIProgress(0f, "Preparing...")
                aiError.value = null
                val pref = com.example.settings.SettingsPreferences(getApplication())
                val preferredAiMode = pref.aiMode.first()

                val result = aiEngine.processImage(request, preferredAiMode) { progress ->
                    aiProgress.value = progress
                }
                aiProgress.value = null
                when (result) {
                    is AIResult.Success -> {
                        previewAiResultUri.value = result.outputUri
                        val previewUri = Uri.parse(result.outputUri)
                        val context = getApplication<Application>()
                        try {
                            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, previewUri)) { decoder, _, _ -> decoder.isMutableRequired = true }
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, previewUri)
                            }
                            previewBitmap.value = b
                        } catch (e: Exception) {
                            aiError.value = e.message
                        }
                    }
                    is AIResult.Error -> {
                        aiError.value = result.error.message
                    }
                }
            } finally {
                isProcessing = false
            }
        }
    }

    fun processAssistantInstruction(instruction: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = aiAssistant.processInstruction(instruction)
            when (action) {
                is EditorAction.AdjustBrightness -> updateState { it.copy(brightness = it.brightness + action.value) }
                is EditorAction.AdjustContrast -> updateState { it.copy(contrast = it.contrast + action.value) }
                is EditorAction.AdjustSaturation -> updateState { it.copy(saturation = it.saturation + action.value) }
                is EditorAction.AIBackgroundRemoval -> processAITool(AIRequest.BackgroundRemoval(state.value.uriString))
                is EditorAction.AIEnhancement -> processAITool(AIRequest.Enhance(state.value.uriString, "auto"))
                else -> { aiError.value = "Unrecognized instruction" }
            }
            if (action !is EditorAction.AIBackgroundRemoval && action !is EditorAction.AIEnhancement && action !is EditorAction.Unknown) {
                commitState()
            }
        }
    }

    fun acceptAiResult() {
        previewAiResultUri.value?.let { 
            setUri(it)
            previewAiResultUri.value = null
            previewBitmap.value = null
        }
    }

    fun discardAiResult() {
        previewAiResultUri.value = null
        previewBitmap.value = null
    }
}
