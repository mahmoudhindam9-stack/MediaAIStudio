package com.example.ai.image.inpainting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.graphics.ImageDecoder
import android.os.Build
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.ai.core.AIError
import com.example.ai.core.AIProgress
import com.example.ai.core.AIResult
import com.example.ai.core.AIProviderType
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

class LaMaInpaintingEngine(private val context: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    
    private var imageInputName: String = ""
    private var maskInputName: String = ""
    private var outputName: String = ""

    fun initialize() {
        if (session != null) return
        val loader = LaMaModelLoader(context)
        val file = loader.getModelFile()
        
        if (!file.exists() || file.length() < 1024) {
            throw IllegalStateException("MODEL_INVALID: LaMa model is missing or invalid placeholder.")
        }
        
        try {
            session = env.createSession(file.absolutePath)
            val inputNames = session?.inputNames?.toList() ?: emptyList()
            if (inputNames.size < 2) {
                throw IllegalStateException("MODEL_SIGNATURE_UNSUPPORTED: Expected at least 2 inputs (image, mask).")
            }
            imageInputName = inputNames.firstOrNull { it.contains("image", true) } ?: inputNames[0]
            maskInputName = inputNames.firstOrNull { it.contains("mask", true) } ?: inputNames[1]
            outputName = session?.outputNames?.firstOrNull() ?: throw IllegalStateException("MODEL_SIGNATURE_UNSUPPORTED: No output found.")
        } catch (e: Exception) {
            session?.close()
            session = null
            throw IllegalStateException("MODEL_RUNTIME_ERROR: ${e.message}", e)
        }
    }

    suspend fun process(sourceUri: String, maskData: String, onProgress: (AIProgress) -> Unit): AIResult {
        return try {
            onProgress(AIProgress(0.1f, "Initializing LaMa..."))
            initialize()
            val sess = session ?: return AIResult.Error(AIError.Unknown("LaMa session not initialized"))
            
            onProgress(AIProgress(0.2f, "Loading Image..."))
            val sourceBitmap = getBitmap(Uri.parse(sourceUri))
            
            onProgress(AIProgress(0.3f, "Preprocessing Tensors..."))
            val targetSize = 512
            val scale = Math.min(targetSize.toFloat() / sourceBitmap.width, targetSize.toFloat() / sourceBitmap.height)
            var w = (sourceBitmap.width * scale).toInt()
            var h = (sourceBitmap.height * scale).toInt()
            w = (w / 8) * 8
            h = (h / 8) * 8
            
            val scaledSource = Bitmap.createScaledBitmap(sourceBitmap, w, h, true)
            val maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val maskCanvas = android.graphics.Canvas(maskBitmap)
            maskCanvas.drawColor(Color.BLACK)
            
            // Basic mask parsing from string (e.g., "[left,top,right,bottom]")
            val paint = android.graphics.Paint().apply { color = Color.WHITE; style = android.graphics.Paint.Style.FILL }
            if (maskData.isNotBlank() && maskData.startsWith("[")) {
                try {
                    val parts = maskData.removeSurrounding("[", "]").split(",")
                    if (parts.size == 4) {
                        val rect = android.graphics.Rect(
                            (parts[0].toFloat() * w).toInt(), (parts[1].toFloat() * h).toInt(),
                            (parts[2].toFloat() * w).toInt(), (parts[3].toFloat() * h).toInt()
                        )
                        maskCanvas.drawRect(rect, paint)
                    }
                } catch (e: Exception) {
                    // fall back to empty mask
                }
            }
            
            val imgData = bitmapToFloatBuffer(scaledSource)
            val maskDataBuf = maskToFloatBuffer(maskBitmap)
            
            val imgTensor = OnnxTensor.createTensor(env, imgData, longArrayOf(1, 3, h.toLong(), w.toLong()))
            val maskTensor = OnnxTensor.createTensor(env, maskDataBuf, longArrayOf(1, 1, h.toLong(), w.toLong()))
            
            val inputs = mapOf(imageInputName to imgTensor, maskInputName to maskTensor)
            
            onProgress(AIProgress(0.5f, "Inpainting..."))
            val results = sess.run(inputs)
            val outputTensor = results.get(outputName) as? OnnxTensor
                ?: return AIResult.Error(AIError.Unknown("MODEL_RUNTIME_ERROR: Null output tensor"))
            
            onProgress(AIProgress(0.8f, "Postprocessing..."))
            val outBuffer = outputTensor.floatBuffer
            val outBitmap = floatBufferToBitmap(outBuffer, w, h)
            
            imgTensor.close()
            maskTensor.close()
            results.close()
            
            val finalBitmap = Bitmap.createScaledBitmap(outBitmap, sourceBitmap.width, sourceBitmap.height, true)
            
            val cacheFileName = "ai_lama_out_${System.currentTimeMillis()}.png"
            val cacheFile = File(context.cacheDir, cacheFileName)
            FileOutputStream(cacheFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            onProgress(AIProgress(1.0f, "Complete"))
            AIResult.Success(
                outputUri = Uri.fromFile(cacheFile).toString(),
                processingType = "ObjectRemoval",
                providerUsed = AIProviderType.ON_DEVICE
            )
            
        } catch (e: Exception) {
            AIResult.Error(AIError.Unknown("MODEL_RUNTIME_ERROR: ${e.message}"))
        }
    }
    
    private fun getBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }
    
    private fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val buffer = FloatBuffer.allocate(1 * 3 * h * w)
        for (c in 0..2) {
            for (i in pixels.indices) {
                val color = pixels[i]
                val v = when(c) {
                    0 -> Color.red(color)
                    1 -> Color.green(color)
                    else -> Color.blue(color)
                }
                buffer.put((v / 255f))
            }
        }
        buffer.rewind()
        return buffer
    }
    
    private fun maskToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val buffer = FloatBuffer.allocate(1 * 1 * h * w)
        for (color in pixels) {
            buffer.put(Color.red(color) / 255f)
        }
        buffer.rewind()
        return buffer
    }
    
    private fun floatBufferToBitmap(buffer: FloatBuffer, w: Int, h: Int): Bitmap {
        val pixels = IntArray(w * h)
        val channelSize = w * h
        buffer.rewind()
        for (i in 0 until channelSize) {
            val r = (buffer.get(i) * 255f).coerceIn(0f, 255f).toInt()
            val g = (buffer.get(i + channelSize) * 255f).coerceIn(0f, 255f).toInt()
            val b = (buffer.get(i + 2 * channelSize) * 255f).coerceIn(0f, 255f).toInt()
            pixels[i] = Color.rgb(r, g, b)
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }
    
    fun release() {
        session?.close()
        session = null
    }
}
