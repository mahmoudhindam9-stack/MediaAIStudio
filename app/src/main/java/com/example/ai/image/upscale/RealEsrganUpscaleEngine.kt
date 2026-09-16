package com.example.ai.image.upscale

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

class RealEsrganUpscaleEngine(private val context: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    
    private var inputName: String = ""
    private var outputName: String = ""

    fun initialize() {
        if (session != null) return
        val file = File(context.filesDir, "models/RealESRGAN_x2plus.onnx")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            try {
                context.assets.open("models/RealESRGAN_x2plus.onnx").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                throw IllegalStateException("MODEL_NOT_FOUND: Failed to copy RealESRGAN model.")
            }
        }
        
        if (file.length() < 1024) {
            throw IllegalStateException("MODEL_INVALID: RealESRGAN model is missing or invalid placeholder.")
        }
        
        try {
            session = env.createSession(file.absolutePath)
            inputName = session?.inputNames?.firstOrNull() ?: throw IllegalStateException("MODEL_SIGNATURE_UNSUPPORTED: No inputs")
            outputName = session?.outputNames?.firstOrNull() ?: throw IllegalStateException("MODEL_SIGNATURE_UNSUPPORTED: No outputs")
        } catch (e: Exception) {
            session?.close()
            session = null
            throw IllegalStateException("MODEL_RUNTIME_ERROR: ${e.message}", e)
        }
    }

    suspend fun process(sourceUri: String, scaleFactor: Int, onProgress: (AIProgress) -> Unit): AIResult {
        return try {
            onProgress(AIProgress(0.1f, "Initializing Real-ESRGAN..."))
            initialize()
            val sess = session ?: return AIResult.Error(AIError.Unknown("RealESRGAN not initialized"))
            
            onProgress(AIProgress(0.3f, "Loading Image..."))
            val sourceBitmap = getBitmap(Uri.parse(sourceUri))
            val w = sourceBitmap.width
            val h = sourceBitmap.height
            
            if (w * h > 1024 * 1024) {
                return AIResult.Error(AIError.Unknown("Insufficient memory for on-device upscale."))
            }
            
            onProgress(AIProgress(0.5f, "Upscaling Image (x2)..."))
            val imgData = bitmapToFloatBuffer(sourceBitmap)
            val imgTensor = OnnxTensor.createTensor(env, imgData, longArrayOf(1, 3, h.toLong(), w.toLong()))
            
            val inputs = mapOf(inputName to imgTensor)
            val results = sess.run(inputs)
            val outputTensor = results.get(outputName) as? OnnxTensor
                ?: return AIResult.Error(AIError.Unknown("MODEL_RUNTIME_ERROR: Null output tensor"))
            
            onProgress(AIProgress(0.8f, "Rendering Results..."))
            val outBuffer = outputTensor.floatBuffer
            val outW = w * 2
            val outH = h * 2
            
            val outBitmap = floatBufferToBitmap(outBuffer, outW, outH)
            
            imgTensor.close()
            results.close()
            
            val cacheFileName = "ai_esrgan_out_${System.currentTimeMillis()}.png"
            val cacheFile = File(context.cacheDir, cacheFileName)
            FileOutputStream(cacheFile).use { out ->
                outBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            onProgress(AIProgress(1.0f, "Complete"))
            AIResult.Success(
                outputUri = Uri.fromFile(cacheFile).toString(),
                processingType = "Upscale",
                providerUsed = AIProviderType.ON_DEVICE
            )
            
        } catch (e: Exception) {
            AIResult.Error(AIError.Unknown(e.message ?: "Unknown MODEL_RUNTIME_ERROR"))
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
