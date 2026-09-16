package com.example.photoeditor

import android.graphics.Color
import android.graphics.RectF
import java.util.UUID

enum class FilterType { NONE, SEPIA, GRAYSCALE, INVERT }

enum class CropHandle { TL, TR, BL, BR, T, B, L, R, CENTER }

data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val width get() = right - left
    val height get() = bottom - top
}

data class PointF(val x: Float, val y: Float)

data class Drawing(
    val path: List<PointF>,
    val color: Int, // ARGB
    val strokeWidth: Float
)

data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val x: Float,
    val y: Float,
    val color: Int,
    val size: Float,
    val rotation: Float = 0f
)

data class Sticker(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float = 0f
)

data class EditorState(
    val uriString: String = "",
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val rotation: Float = 0f,
    val straighten: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val drawings: List<Drawing> = emptyList(),
    val texts: List<TextOverlay> = emptyList(),
    val stickers: List<Sticker> = emptyList(),
    val filter: FilterType = FilterType.NONE,
    val cropRect: CropRect = CropRect(),
    val cropAspectRatio: Float? = null
) {
    fun toComposeColorMatrix(): androidx.compose.ui.graphics.ColorMatrix {
        val androidMatrix = PhotoExport.buildColorMatrix(this)
        return androidx.compose.ui.graphics.ColorMatrix(androidMatrix.array)
    }
}
