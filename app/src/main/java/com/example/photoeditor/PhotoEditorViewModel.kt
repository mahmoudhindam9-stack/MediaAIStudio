package com.example.photoeditor

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

class PhotoEditorViewModel(application: Application) : AndroidViewModel(application) {
    val state = MutableStateFlow(EditorState())
    val aiProgress = MutableStateFlow<AIProgress?>(null)
    val aiError = MutableStateFlow<String?>(null)
    val aiEngine = AIImageEngine(AIProviderManager(application))
    val generativeEngine = GenerativeEngine(application)
    val aiAssistant = AIAssistant()
    val previewAiResultUri = MutableStateFlow<String?>(null)
    val previewBitmap = MutableStateFlow<Bitmap?>(null)


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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
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
        if (historyIndex > 0) {
            historyIndex--
            state.value = history[historyIndex]
        }
    }
    
    fun redo() {
        if (historyIndex < history.size - 1) {
            historyIndex++
            state.value = history[historyIndex]
        }
    }
    
    fun updateState(transform: (EditorState) -> EditorState) {
        state.value = transform(state.value)
    }
    
    fun commitState() {
        pushHistory()
    }
    
    // Draw
    private var currentDrawingPath = mutableListOf<PointF>()
    fun startDrawing(point: PointF) {
        currentDrawingPath = mutableListOf(point)
    }
    fun addDrawingPoint(point: PointF) {
        currentDrawingPath.add(point)
        val newDrawing = Drawing(currentDrawingPath.toList(), android.graphics.Color.RED, 0.01f)
        val drawings = state.value.drawings.toMutableList()
        if (drawings.isNotEmpty() && drawings.last().path.first() == currentDrawingPath.first()) {
            drawings[drawings.lastIndex] = newDrawing
        } else {
            drawings.add(newDrawing)
        }
        state.value = state.value.copy(drawings = drawings)
    }
    fun endDrawing() {
        commitState()
    }
    
    // Text
    fun addText(text: String) {
        val newText = TextOverlay(text = text, x = 0.5f, y = 0.5f, color = android.graphics.Color.WHITE, size = 0.1f)
        updateState { it.copy(texts = it.texts + newText) }
        commitState()
    }
    fun moveText(id: String, dx: Float, dy: Float) {
        val texts = state.value.texts.map {
            if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
        }
        state.value = state.value.copy(texts = texts)
    }
    
    // Sticker
    fun addSticker(emoji: String) {
        val newSticker = Sticker(emoji = emoji, x = 0.5f, y = 0.5f, scale = 0.2f)
        updateState { it.copy(stickers = it.stickers + newSticker) }
        commitState()
    }
    fun moveSticker(id: String, dx: Float, dy: Float) {
        val stickers = state.value.stickers.map {
            if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
        }
        state.value = state.value.copy(stickers = stickers)
    }

    fun processAITool(request: AIRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            aiProgress.value = AIProgress(0f, "Preparing...")
            aiError.value = null
            val result = aiEngine.processImage(request) { progress ->
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
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, previewUri))
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, previewUri)
                            }
                        }
                        previewBitmap.value = b
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                is AIResult.Error -> {
                    aiError.value = result.error.message
                }
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
    fun clearAiError() {
        aiError.value = null
    }

    fun runGenerativeImage(type: GenerativeType, prompt: String, maskUri: String? = null) {
        val s = state.value
        val uri = s.uriString.ifEmpty { null } ?: return
        val req = GenerativeRequest(
            type = type,
            sourceUri = Uri.parse(uri),
            maskUri = maskUri?.let { Uri.parse(it) },
            prompt = prompt
        )
        val jobId = generativeEngine.submitJob(req)
        viewModelScope.launch {
            generativeEngine.jobs.collect { jobs ->
                val job = jobs[jobId]
                if (job != null) {
                    if (job.state == JobState.FAILED) {
                        aiError.value = job.message
                    } else if (job.state == JobState.PROCESSING || job.state == JobState.PREPARING) {
                        aiProgress.value = AIProgress(job.progress, job.message)
                    } else if (job.state == JobState.COMPLETED) {
                        aiProgress.value = null
                        val res = job.result
                        if (res is GenerativeResult.Success) {
                            previewAiResultUri.value = res.outputUri.toString()
                        }
                    }
                }
            }
        }
    }
    
}