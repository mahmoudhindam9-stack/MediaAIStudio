package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class FaceTarget(
    val normalizedX: Float,
    val normalizedY: Float,
    val imageX: Float,
    val imageY: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val bounds: RectF,
    val trackingId: Int?
)

/** Reusable real-time ML Kit face detector for CameraX ImageAnalysis frames. */
class FaceFocusAnalyzer(
    private val onTargetChanged: (FaceTarget?) -> Unit
) : androidx.camera.core.ImageAnalysis.Analyzer {

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.12f)
            .enableTracking()
            .build()
    )

    private var lastTimestamp = 0L
    private var lastX = -1f
    private var lastY = -1f

    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastTimestamp < 140L) {
            imageProxy.close()
            return
        }
        lastTimestamp = now

        try {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val target = selectTarget(faces, image.width, image.height)
                    if (target == null) {
                        lastX = -1f
                        lastY = -1f
                        onTargetChanged(null)
                    } else if (lastX < 0f || distance(target.normalizedX, target.normalizedY, lastX, lastY) > 0.025f) {
                        lastX = target.normalizedX
                        lastY = target.normalizedY
                        onTargetChanged(target)
                    }
                }
                .addOnFailureListener {
                    // Keep the camera running even when one analysis frame fails.
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (_: Throwable) {
            imageProxy.close()
        }
    }

    private fun selectTarget(faces: List<Face>, width: Int, height: Int): FaceTarget? {
        val face = faces.maxByOrNull { currentFace ->
            currentFace.boundingBox.width().toLong() * currentFace.boundingBox.height().toLong()
        } ?: return null

        val bounds = RectF(face.boundingBox)
        val imageX = bounds.centerX().coerceIn(0f, width.toFloat())
        val imageY = bounds.centerY().coerceIn(0f, height.toFloat())
        
        val centerX = (imageX / max(width, 1)).coerceIn(0f, 1f)
        val centerY = (imageY / max(height, 1)).coerceIn(0f, 1f)

        return FaceTarget(
            normalizedX = centerX,
            normalizedY = centerY,
            imageX = imageX,
            imageY = imageY,
            imageWidth = width,
            imageHeight = height,
            bounds = bounds,
            trackingId = face.trackingId
        )
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun close() {
        try {
            detector.close()
        } catch (_: Exception) {}
    }
}

/** Real on-device portrait background blur using ML Kit Subject Segmentation. */
object PortraitBlurProcessor {
    suspend fun process(
        context: Context,
        sourceUri: String,
        blurStrength: Float
    ): String {
        val appContext = context.applicationContext
        val source = loadBitmap(appContext, Uri.parse(sourceUri))
            ?: throw IllegalArgumentException("Unable to decode captured image")

        val segmenter: SubjectSegmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
        )

        try {
            val image = InputImage.fromBitmap(source, 0)
            val segmentation = segmenter.process(image).await()
            val foreground = segmentation.foregroundBitmap
                ?: throw IllegalStateException("Foreground segmentation returned no bitmap")

            val background = createBlurredBackground(source, blurStrength)

            val composed = Bitmap.createBitmap(
                source.width,
                source.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(composed)
            canvas.drawBitmap(background, 0f, 0f, null)

            val fg = if (foreground.width == source.width && foreground.height == source.height) {
                foreground
            } else {
                Bitmap.createScaledBitmap(foreground, source.width, source.height, true)
            }
            canvas.drawBitmap(fg, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

            if (fg !== foreground) foreground.recycle()
            background.recycle()
            source.recycle()

            val output = File(appContext.cacheDir, "portrait_${System.currentTimeMillis()}.png")
            FileOutputStream(output).use { stream ->
                check(composed.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Unable to encode portrait image"
                }
            }
            composed.recycle()

            return FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                output
            ).toString()
        } finally {
            segmenter.close()
        }
    }

    private fun createBlurredBackground(source: Bitmap, strength: Float): Bitmap {
        val clamped = strength.coerceIn(0f, 1f)
        if (clamped <= 0.01f) return source.copy(Bitmap.Config.ARGB_8888, false)

        // Downsample/upsample creates a real soft-focus background while keeping the subject sharp.
        val shortest = minOf(source.width, source.height)
        val divisor = (1f + clamped * 14f)
        val reducedSize = (shortest / divisor).toInt().coerceIn(24, 512)
        val ratio = reducedSize.toFloat() / shortest.toFloat()
        val reducedW = (source.width * ratio).toInt().coerceAtLeast(1)
        val reducedH = (source.height * ratio).toInt().coerceAtLeast(1)

        val reduced = Bitmap.createScaledBitmap(source, reducedW, reducedH, true)
        val blurred = Bitmap.createScaledBitmap(reduced, source.width, source.height, true)
        reduced.recycle()

        return blurred
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri)
            ) { decoder, _, _ ->
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }
}
