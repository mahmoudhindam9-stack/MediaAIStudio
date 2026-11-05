package com.example.media

import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    suspend fun getImages(): Flow<List<MediaItem>>
    suspend fun getVideos(): Flow<List<MediaItem>>
}
