package com.example.media

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val duration: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val isVideo: Boolean = mimeType.startsWith("video/")
)
