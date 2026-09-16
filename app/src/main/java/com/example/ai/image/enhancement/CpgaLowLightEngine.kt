package com.example.ai.image.enhancement

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.graphics.ImageDecoder
import android.os.Build
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import com.example.ai.core.AIError
import com.example.ai.core.AIProgress
import com.example.ai.core.AIResult
import com.example.ai.core.AIProviderType
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CpgaLowLightEngine(private val context: Context) {
    private var interpreter: Interpreter? = null
    
    private var inputShape: IntArray = intArrayOf()
    private var outputShape: IntArray = intArrayOf()
    private var isGpuAccelerated = false

    fun initialize() {
        if (interpreter != null) return
        
        val file = File(context.filesDir, "models/cpga_fp16.tflite")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            try {
                context.assets.open("models/cpga_fp16.tflite").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                throw IllegalStateException("MODEL_NOT_FOUND: Failed to copy CPGA model.")
            }
        }
        
        if (file.length() < 1024) {
            throw IllegalStateException("MODEL_INVALID: CPGA model is missing or invalid.")
        }
        
        try {
            // Fallback for classpath issues
            val tflite = Interpreter(file)
            isGpuAccelerated = false
            interpreter = tflite
            
            val inputTensor = tflite.getInputTensor(0)
            val outputTensor = tflite.getOutputTensor(0)
            
            inputShape = inputTensor.shape()
            outputShape = outputTensor.shape()
            
            if (inputShape.size != 4 || outputShape.size != 4) {
                throw IllegalStateException("MODEL_SIGNATURE_UNSUPPORTED: Expected 4D tensors.")
            }
            
        } catch (e: Exception) {
            interpreter?.close()
            interpreter = null
            throw IllegalStateException("MODEL_RUNTIME_ERROR: ${e.message}", e)
        }
    }

    suspend fun process(sourceUri: String, onProgress: (AIProgress) -> Unit): AIResult {
        return try {
            onProgress(AIProgress(0.1f, "Initializing CPGA..."))
            initialize()
            val tflite = interpreter ?: return AIResult.Error(AIError.Unknown("CPGA not initialized"))
            
            onProgress(AIProgress(0.3f, "Loading Image..."))
            val sourceBitmap = getBitmap(Uri.parse(sourceUri))
            
            val batch = inputShape[0]
            val h = if (inputShape[1] == 3) inputShape[2] else inputShape[1]
            val w = if (inputShape[1] == 3) inputShape[3] else inputShape[2]
            val isNCHW = (inputShape[1] == 3)
            
            val processW = if (w == -1) 256 else w
            val processH = if (h == -1) 256 else h
            
            onProgress(AIProgress(0.5f, "Enhancing Low Light (GPU: $isGpuAccelerated)..."))
            val scaledSource = Bitmap.createScaledBitmap(sourceBitmap, processW, processH, true)
            val inputBuffer = bitmapToByteBuffer(scaledSource, processW, processH, isNCHW)
            val outputBuffer = ByteBuffer.allocateDirect(1 * processW * processH * 3 * 4)
            outputBuffer.order(ByteOrder.nativeOrder())
            
            tflite.run(inputBuffer, outputBuffer)
            
            onProgress(AIProgress(0.8f, "Rendering Results..."))
            val outBitmap = byteBufferToBitmap(outputBuffer, processW, processH, isNCHW)
            
            val finalBitmap = Bitmap.createScaledBitmap(outBitmap, sourceBitmap.width, sourceBitmap.height, true)
            
            val cacheFileName = "ai_cpga_out_${System.currentTimeMillis()}.png"
            val cacheFile = File(context.cacheDir, cacheFileName)
            FileOutputStream(cacheFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            onProgress(AIProgress(1.0f, "Complete"))
            AIResult.Success(
                outputUri = Uri.fromFile(cacheFile).toString(),
                processingType = "Enhance",
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
    
    private fun bitmapToByteBuffer(bitmap: Bitmap, w: Int, h: Int, isNCHW: Boolean): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * w * h * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        
        if (isNCHW) {
            for (c in 0..2) {
                for (color in pixels) {
                    val v = when(c) { 0 -> Color.red(color); 1 -> Color.green(color); else -> Color.blue(color) }
                    buffer.putFloat(v / 255f)
                }
            }
        } else {
            for (color in pixels) {
                buffer.putFloat(Color.red(color) / 255f)
                buffer.putFloat(Color.green(color) / 255f)
                buffer.putFloat(Color.blue(color) / 255f)
            }
        }
        
        buffer.rewind()
        return buffer
    }
    
    private fun byteBufferToBitmap(buffer: ByteBuffer, w: Int, h: Int, isNCHW: Boolean): Bitmap {
        buffer.rewind()
        val pixels = IntArray(w * h)
        
        if (isNCHW) {
            val channelSize = w * h
            for (i in 0 until channelSize) {
                val r = (buffer.getFloat(i * 4) * 255f).coerceIn(0f, 255f).toInt()
                val g = (buffer.getFloat((channelSize + i) * 4) * 255f).coerceIn(0f, 255f).toInt()
                val b = (buffer.getFloat((2 * channelSize + i) * 4) * 255f).coerceIn(0f, 255f).toInt()
                pixels[i] = Color.rgb(r, g, b)
            }
        } else {
            for (i in pixels.indices) {
                val r = (buffer.float * 255f).coerceIn(0f, 255f).toInt()
                val g = (buffer.float * 255f).coerceIn(0f, 255f).toInt()
                val b = (buffer.float * 255f).coerceIn(0f, 255f).toInt()
                pixels[i] = Color.rgb(r, g, b)
            }
        }
        
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }
    
    fun release() {
        interpreter?.close()
        interpreter = null
    }
}
