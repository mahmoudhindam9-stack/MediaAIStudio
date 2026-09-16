package com.example.ai.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.ai.core.*
import com.example.ai.image.inpainting.LaMaInpaintingEngine
import com.example.ai.image.upscale.RealEsrganUpscaleEngine
import com.example.ai.image.enhancement.CpgaLowLightEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class OnDeviceAIProvider(context: Context) : AIProvider {
    private val appContext = context.applicationContext
    override val type = AIProviderType.ON_DEVICE
    override val isAvailable = true 

    private val segmenter: SubjectSegmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        SubjectSegmentation.getClient(options)
    }

    private val objectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        ObjectDetection.getClient(options)
    }

    override suspend fun process(request: AIRequest, onProgress: (AIProgress) -> Unit): AIResult {
        return withContext(Dispatchers.IO) {
            when (request) {
                is AIRequest.BackgroundRemoval -> {
                    try {
                        val cacheFileName = "ai_bg_remove_${request.sourceUri.hashCode()}.png"
                        val cacheFile = File(appContext.cacheDir, cacheFileName)
                        if (cacheFile.exists()) {
                            onProgress(AIProgress(1.0f, "Loaded from Cache"))
                            val cachedUri = FileProvider.getUriForFile(
                                appContext,
                                "${appContext.packageName}.fileprovider",
                                cacheFile
                            )
                            return@withContext AIResult.Success(
                                outputUri = cachedUri.toString(),
                                processingType = "BackgroundRemoval",
                                providerUsed = AIProviderType.ON_DEVICE
                            )
                        }

                        onProgress(AIProgress(0.1f, "Loading Image..."))
                        val uri = Uri.parse(request.sourceUri)
                        val bitmap = getBitmap(uri)

                        onProgress(AIProgress(0.3f, "Analyzing Subject..."))
                        val image = InputImage.fromBitmap(bitmap, 0)
                        
                        val result = segmenter.process(image).await()
                        
                        onProgress(AIProgress(0.8f, "Extracting Foreground..."))
                        val foreground = result.foregroundBitmap
                        
                        if (foreground != null) {
                            FileOutputStream(cacheFile).use { out ->
                                foreground.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            onProgress(AIProgress(1.0f, "Complete"))
                            val contentUri = FileProvider.getUriForFile(
                                appContext,
                                "${appContext.packageName}.fileprovider",
                                cacheFile
                            )
                            AIResult.Success(
                                outputUri = contentUri.toString(),
                                processingType = "BackgroundRemoval",
                                providerUsed = AIProviderType.ON_DEVICE
                            )
                        } else {
                            AIResult.Error(AIError.ProcessingFailure)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AIResult.Error(AIError.Unknown(e.message ?: "Background removal failed"))
                    }
                }
                is AIRequest.DetectObjects -> {
                    try {
                        val cacheFileName = "ai_obj_detect_${request.sourceUri.hashCode()}.jpg"
                        val cacheFile = File(appContext.cacheDir, cacheFileName)
                        val metaCacheFile = File(appContext.cacheDir, "${cacheFileName}_meta.txt")
                        
                        if (cacheFile.exists() && metaCacheFile.exists()) {
                            onProgress(AIProgress(1.0f, "Loaded from Cache"))
                            val cachedMeta = metaCacheFile.readText()
                            val cachedUri = FileProvider.getUriForFile(
                                appContext,
                                "${appContext.packageName}.fileprovider",
                                cacheFile
                            )
                            return@withContext AIResult.Success(
                                outputUri = cachedUri.toString(),
                                processingType = "ObjectDetection",
                                providerUsed = AIProviderType.ON_DEVICE,
                                metadata = mapOf("detections" to cachedMeta)
                            )
                        }

                        onProgress(AIProgress(0.1f, "Loading Image for Detection..."))
                        val uri = Uri.parse(request.sourceUri)
                        val bitmap = getBitmap(uri)
                        
                        onProgress(AIProgress(0.4f, "Detecting Objects..."))
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val detectedObjects = objectDetector.process(image).await()

                        onProgress(AIProgress(0.8f, "Rendering Results..."))
                        val outBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = Canvas(outBitmap)
                        val paint = Paint().apply {
                            color = Color.GREEN
                            style = Paint.Style.STROKE
                            strokeWidth = 8f
                        }
                        val textPaint = Paint().apply {
                            color = Color.RED
                            textSize = 48f
                            style = Paint.Style.FILL
                        }

                        val metaBuilder = java.lang.StringBuilder("[")
                        
                        detectedObjects.forEachIndexed { index, obj ->
                            val bounds = obj.boundingBox
                            canvas.drawRect(bounds, paint)
                            val label = obj.labels.firstOrNull()?.text ?: "Object ${index+1}"
                            val conf = obj.labels.firstOrNull()?.confidence ?: 1.0f
                            canvas.drawText("$label ${(conf*100).toInt()}%", bounds.left.toFloat(), bounds.top.toFloat() - 10f, textPaint)
                            
                            metaBuilder.append("""{"label":"$label", "bounds": [${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom}], "confidence": $conf}""")
                            if (index < detectedObjects.size - 1) metaBuilder.append(",")
                        }
                        metaBuilder.append("]")

                        FileOutputStream(cacheFile).use { out ->
                            outBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        metaCacheFile.writeText(metaBuilder.toString())

                        onProgress(AIProgress(1.0f, "Complete"))
                        val contentUri = FileProvider.getUriForFile(
                            appContext,
                            "${appContext.packageName}.fileprovider",
                            cacheFile
                        )
                        AIResult.Success(
                            outputUri = contentUri.toString(),
                            processingType = "ObjectDetection",
                            providerUsed = AIProviderType.ON_DEVICE,
                            metadata = mapOf("detections" to metaBuilder.toString())
                        )

                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AIResult.Error(AIError.Unknown(e.message ?: "Object detection failed"))
                    }
                }
                
                is AIRequest.ObjectRemoval -> {
                    LaMaInpaintingEngine(appContext).process(request.sourceUri, request.maskData, onProgress)
                }
                is AIRequest.Upscale -> {
                    RealEsrganUpscaleEngine(appContext).process(request.sourceUri, request.scaleFactor, onProgress)
                }
                is AIRequest.Enhance -> {
                    if (request.enhanceType == "low_light" || request.enhanceType == "auto") {
                        CpgaLowLightEngine(appContext).process(request.sourceUri, onProgress)
                    } else {
                        AIResult.Error(AIError.ModelUnavailable)
                    }
                }
                else -> AIResult.Error(AIError.ModelUnavailable)
            }
        }
    }

    private fun getBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(appContext.contentResolver, uri)
        }
    }
}
