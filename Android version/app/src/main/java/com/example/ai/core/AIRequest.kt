package com.example.ai.core
sealed interface AIRequest {
    val sourceUri: String
    
    data class DetectObjects(override val sourceUri: String) : AIRequest

    data class BackgroundRemoval(override val sourceUri: String) : AIRequest
    data class ObjectRemoval(override val sourceUri: String, val maskData: String) : AIRequest
    data class Upscale(override val sourceUri: String, val scaleFactor: Int) : AIRequest
    data class Enhance(override val sourceUri: String, val enhanceType: String) : AIRequest
    data class Restyle(override val sourceUri: String, val prompt: String) : AIRequest
}
