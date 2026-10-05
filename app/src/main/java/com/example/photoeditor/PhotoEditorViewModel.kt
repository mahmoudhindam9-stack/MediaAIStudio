
package com.example.photoeditor
import kotlinx.coroutines.flow.first

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun startObjectRemoval(startPoint: PointF) {
        val stroke = Drawing(
            path = listOf(startPoint),
            color = Color.RED,
            strokeWidth = state.value.objectRemovalBrushSize.coerceIn(0.01f, 0.15f)
        )
        updateState { it.copy(objectRemovalStrokes = it.objectRemovalStrokes + stroke) }
    }

    fun addObjectRemovalPoint(point: PointF) {
        val strokes = state.value.objectRemovalStrokes.toMutableList()
        if (strokes.isNotEmpty()) {
            val last = strokes.last()
            strokes[strokes.lastIndex] = last.copy(path = last.path + point)
            state.value = state.value.copy(objectRemovalStrokes = strokes)
        }
    }

    fun endObjectRemoval() {
        commitState()
    }

    fun clearObjectRemoval() {
        if (state.value.objectRemovalStrokes.isEmpty()) return
        updateState { it.copy(objectRemovalStrokes = emptyList()) }
        commitState()
    }

    fun buildObjectRemovalMaskData(size: Int = 512): String? {
        val strokes = state.value.objectRemovalStrokes
        if (strokes.isEmpty()) return null

        val safeSize = size.coerceIn(128, 1024)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            strokes.forEach { stroke ->
                if (stroke.path.isEmpty()) return@forEach
                paint.strokeWidth = (stroke.strokeWidth * safeSize).coerceAtLeast(1f)
                if (stroke.path.size == 1) {
                    val point = stroke.path.first()
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(
                        point.x.coerceIn(0f, 1f) * safeSize,
                        point.y.coerceIn(0f, 1f) * safeSize,
                        paint.strokeWidth / 2f,
                        paint
                    )
                    paint.style = Paint.Style.STROKE
                } else {
                    val path = Path()
                    stroke.path.forEachIndexed { index, point ->
                        val x = point.x.coerceIn(0f, 1f) * safeSize
                        val y = point.y.coerceIn(0f, 1f) * safeSize
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, paint)
                }
            }

            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Failed to encode object-removal mask"
            }
            return "mask_png_base64:" + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
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
        // Obsolete
    }

    suspend fun executeAssistantPlan(
        plan: com.example.ai.assistant.AssistantActionPlan
    ): com.example.ai.assistant.AssistantResult {
        val executor = PhotoEditorAssistantExecutor(this)
        for (action in plan.actions) {
            val result = executor.execute(action)
            if (result is com.example.ai.assistant.AssistantExecutionResult.Failed) {
                return com.example.ai.assistant.AssistantResult.Failed(result.message)
            }
            if (result is com.example.ai.assistant.AssistantExecutionResult.Unsupported) {
                return com.example.ai.assistant.AssistantResult.Unsupported(
                    plan.originalPrompt,
                    result.message
                )
            }
        }
        commitState()
        return com.example.ai.assistant.AssistantResult.Executed(plan)
    }

    fun buildAssistantContext(): com.example.ai.assistant.AssistantContext {
        val current = state.value
        return com.example.ai.assistant.AssistantContext(
            mediaType = com.example.ai.assistant.MediaType.PHOTO,
            hasSourceMedia = current.uriString.isNotBlank(),
            currentBrightness = current.brightness,
            currentContrast = current.contrast,
            currentSaturation = current.saturation,
            currentTemperature = current.temperature,
            canUndo = canUndo(),
            canRedo = canRedo(),
            isBusy = isProcessing
        )
    }

    fun acceptAiResult() {
        val outputUri = previewAiResultUri.value ?: return
        if (isProcessing) return

        viewModelScope.launch(Dispatchers.IO) {
            isProcessing = true
            aiError.value = null
            aiProgress.value = AIProgress(0f, "Saving AI result...")

            try {
                val persistedUri = persistAiResultToGallery(outputUri)
                if (persistedUri == null) {
                    aiError.value = "Failed to save AI result to Gallery."
                    return@launch
                }

                previewAiResultUri.value = null
                previewBitmap.value?.let { bitmap ->
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                previewBitmap.value = null
                setUri(persistedUri)
                aiProgress.value = null
            } finally {
                isProcessing = false
                aiProgress.value = null
            }
        }
    }

    suspend fun persistAiResultToGallery(sourceUri: String): String? = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val inputUri = Uri.parse(sourceUri)
        val mimeType = resolver.getType(inputUri) ?: "image/png"
        val extension = when {
            mimeType.equals("image/jpeg", ignoreCase = true) -> "jpg"
            mimeType.equals("image/webp", ignoreCase = true) -> "webp"
            else -> "png"
        }
        val displayName = "MediaAIStudio_AI_" + System.currentTimeMillis() + "." + extension

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MediaAIStudio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val outputUri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return@withContext null

        try {
            resolver.openInputStream(inputUri)?.use { input ->
                resolver.openOutputStream(outputUri)?.use { output ->
                    input.copyTo(output)
                } ?: throw IllegalStateException("Unable to open Gallery output.")
            } ?: throw IllegalStateException("Unable to open AI result.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(outputUri, publishValues, null, null)
            }
            outputUri.toString()
        } catch (_: Throwable) {
            resolver.delete(outputUri, null, null)
            null
        }
    }

    fun discardAiResult() {
        previewAiResultUri.value = null
        previewBitmap.value = null
    }
}
