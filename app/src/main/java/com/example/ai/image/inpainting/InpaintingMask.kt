package com.example.ai.image.inpainting

data class InpaintingMask(
    val width: Int,
    val height: Int,
    val alpha: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as InpaintingMask
        if (width != other.width) return false
        if (height != other.height) return false
        if (!alpha.contentEquals(other.alpha)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + alpha.contentHashCode()
        return result
    }
}
