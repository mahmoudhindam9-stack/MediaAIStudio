package com.example.core.navigation

import kotlinx.serialization.Serializable

sealed class Screen {
    @Serializable
    data object Home : Screen()
    
    @Serializable
    data object Camera : Screen()
    
    @Serializable
    data object Library : Screen()
    
    @Serializable
    data object AITools : Screen()
    
    @Serializable
    data object Projects : Screen()
    
    @Serializable
    data object Settings : Screen()

    @Serializable
    data object Update : Screen()

    @Serializable
    data class MediaDetail(val uriString: String) : Screen()

    @Serializable
    data class PhotoEditor(val uriString: String) : Screen()

    @Serializable
    data class VideoEditor(val uriString: String) : Screen()
}
