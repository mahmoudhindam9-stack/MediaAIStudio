package com.example.photoeditor

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object PhotoExport {
    suspend fun export(context: Context, state: EditorState): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val originalUri = Uri.parse(state.uriString)
        
        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, originalUri)) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(resolver, originalUri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(resolver, originalUri)
            }
        }.copy(Bitmap.Config.ARGB_8888, true)
        
        val matrix = Matrix()
        matrix.postRotate(state.rotation + state.straighten)
        matrix.postScale(if (state.flipHorizontal) -1f else 1f, if (state.flipVertical) -1f else 1f)
        
        val transformedBitmap = Bitmap.createBitmap(
            originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
        )
        if (transformedBitmap != originalBitmap) {
            originalBitmap.recycle()
        }

        val cropX = (state.cropRect.left * transformedBitmap.width).toInt().coerceIn(0, transformedBitmap.width - 1)
        val cropY = (state.cropRect.top * transformedBitmap.height).toInt().coerceIn(0, transformedBitmap.height - 1)
        val cropW = (state.cropRect.width * transformedBitmap.width).toInt().coerceIn(1, transformedBitmap.width - cropX)
        val cropH = (state.cropRect.height * transformedBitmap.height).toInt().coerceIn(1, transformedBitmap.height - cropY)
        
        val croppedBitmap = Bitmap.createBitmap(transformedBitmap, cropX, cropY, cropW, cropH)
        if (croppedBitmap != transformedBitmap) {
            transformedBitmap.recycle()
        }

        val outputBitmap = Bitmap.createBitmap(croppedBitmap.width, croppedBitmap.height, Bitmap.Config.ARGB_8888)
        val outputCanvas = Canvas(outputBitmap)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(buildColorMatrix(state))
        outputCanvas.drawBitmap(croppedBitmap, 0f, 0f, paint)
        croppedBitmap.recycle()

        val drawPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (drawing in state.drawings) {
            drawPaint.color = drawing.color
            drawPaint.strokeWidth = drawing.strokeWidth * outputBitmap.width
            val path = Path()
            if (drawing.path.isNotEmpty()) {
                val start = drawing.path.first()
                path.moveTo(start.x * outputBitmap.width, start.y * outputBitmap.height)
                for (i in 1 until drawing.path.size) {
                    val p = drawing.path[i]
                    path.lineTo(p.x * outputBitmap.width, p.y * outputBitmap.height)
                }
            }
            outputCanvas.drawPath(path, drawPaint)
        }

        val textPaint = Paint().apply {
            textAlign = Paint.Align.CENTER
        }
        for (text in state.texts) {
            textPaint.color = text.color
            textPaint.textSize = text.size * outputBitmap.width
            outputCanvas.save()
            outputCanvas.translate(text.x * outputBitmap.width, text.y * outputBitmap.height)
            outputCanvas.rotate(text.rotation)
            outputCanvas.drawText(text.text, 0f, 0f, textPaint)
            outputCanvas.restore()
        }
        
        val stickerPaint = Paint().apply {
            textAlign = Paint.Align.CENTER
        }
        for (sticker in state.stickers) {
            stickerPaint.textSize = sticker.scale * outputBitmap.width
            outputCanvas.save()
            outputCanvas.translate(sticker.x * outputBitmap.width, sticker.y * outputBitmap.height)
            outputCanvas.rotate(sticker.rotation)
            outputCanvas.drawText(sticker.emoji, 0f, 0f, stickerPaint)
            outputCanvas.restore()
        }
        
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Edited_$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MediaAIStudio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        
        val outUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return@withContext null
            
        resolver.openOutputStream(outUri)?.use { stream ->
            outputBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(outUri, contentValues, null, null)
        }
        
        outputBitmap.recycle()
        outUri
    }

    fun buildColorMatrix(state: EditorState): ColorMatrix {
        val matrix = ColorMatrix()
        
        val satMatrix = ColorMatrix().apply { setSaturation(state.saturation) }
        matrix.postConcat(satMatrix)
        
        val scale = state.contrast
        val translate = (-.5f * scale + .5f) * 255f + state.brightness
        val contrastMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)
        
        val tempMatrix = ColorMatrix(floatArrayOf(
            1f + state.temperature * 0.2f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - state.temperature * 0.2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(tempMatrix)
        
        when (state.filter) {
            FilterType.SEPIA -> {
                val sepiaMatrix = ColorMatrix(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(sepiaMatrix)
            }
            FilterType.GRAYSCALE -> {
                val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
                matrix.postConcat(grayMatrix)
            }
            FilterType.INVERT -> {
                val invertMatrix = ColorMatrix(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(invertMatrix)
            }
            FilterType.NONE -> {}
        }
        
        return matrix
    }
}
