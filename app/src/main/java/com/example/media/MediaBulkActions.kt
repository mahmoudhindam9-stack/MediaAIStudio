package com.example.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

object MediaBulkActions {
    fun share(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = when {
                items.all { it.isVideo } -> "video/*"
                items.all { !it.isVideo } -> "image/*"
                else -> "*/*"
            }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("media", items.first().uri).apply {
                items.drop(1).forEach { addItem(android.content.ClipData.Item(it.uri)) }
            }
        }
        context.startActivity(Intent.createChooser(intent, "Send ${items.size} items"))
    }

    suspend fun mergeImages(context: Context, items: List<MediaItem>): Uri? {
        if (items.size < 2 || items.any { it.isVideo }) return null
        val bitmaps = mutableListOf<Bitmap>()
        return try {
            items.forEach { item -> decodeScaled(context, item.uri, 720)?.let(bitmaps::add) }
            if (bitmaps.size < 2) return null

            val columns = 2
            val rows = (bitmaps.size + columns - 1) / columns
            val cellWidth = 720
            val cellHeight = 720
            val gap = 12
            val outputWidth = columns * cellWidth + (columns - 1) * gap
            val outputHeight = rows * cellHeight + (rows - 1) * gap
            val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(output)
                canvas.drawColor(Color.BLACK)
                bitmaps.forEachIndexed { index, bitmap ->
                    val column = index % columns
                    val row = index / columns
                    val scale = min(cellWidth.toFloat() / bitmap.width, cellHeight.toFloat() / bitmap.height)
                    val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
                    val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
                    val left = column * (cellWidth + gap) + (cellWidth - width) / 2
                    val top = row * (cellHeight + gap) + (cellHeight - height) / 2
                    canvas.drawBitmap(bitmap, null, android.graphics.Rect(left, top, left + width, top + height), null)
                }

                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Merged_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MediaAIStudio/Merged/")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        check(output.compress(Bitmap.CompressFormat.JPEG, 94, out))
                    } ?: throw IllegalStateException("Unable to open output stream")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val done = android.content.ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                        context.contentResolver.update(uri, done, null, null)
                    }
                    uri
                } catch (t: Throwable) {
                    context.contentResolver.delete(uri, null, null)
                    throw t
                }
            } finally {
                output.recycle()
            }
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun decodeScaled(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val srcW = info.size.width
                val srcH = info.size.height
                val sample = max(1, max(srcW, srcH) / maxSize)
                decoder.setTargetSampleSize(sample)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            decodeLegacy(context, uri, maxSize)
        }
    }

    private fun decodeLegacy(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        var width = 0
        var height = 0
        var stream: InputStream? = context.contentResolver.openInputStream(uri)
        try {
            if (stream == null) return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            width = opts.outWidth
            height = opts.outHeight
        } finally {
            stream?.close()
        }
        val maxDimension = max(width, height).coerceAtLeast(1)
        val sample = max(1, maxDimension / maxSize)
        stream = context.contentResolver.openInputStream(uri) ?: return null
        return try {
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } finally {
            stream.close()
        }
    }
}
