package com.example.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class MediaStoreRepository(private val context: Context) : MediaRepository {
    override suspend fun getImages(): Flow<List<MediaItem>> = flow {
        emit(queryImages())
    }.flowOn(Dispatchers.IO)

    override suspend fun getVideos(): Flow<List<MediaItem>> = flow {
        emit(queryVideos())
    }.flowOn(Dispatchers.IO)

    suspend fun getAllMedia(): Flow<List<MediaItem>> = flow {
        val images = queryImages()
        val videos = queryVideos()
        val all = (images + videos).sortedByDescending { it.dateAdded }
        emit(all)
    }.flowOn(Dispatchers.IO)

    suspend fun deleteUris(uris: List<android.net.Uri>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        uris.forEach { uri ->
            try {
                deleted += context.contentResolver.delete(uri, null, null)
            } catch (_: SecurityException) {
                // Android 11+ may require the platform delete confirmation flow.
            }
        }
        deleted
    }

    suspend fun moveToAlbum(items: List<MediaItem>, albumName: String): Int = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext 0
        val cleanName = albumName.trim().replace(Regex("[/\\\\]+"), "_").take(80)
        if (cleanName.isBlank()) return@withContext 0

        var moved = 0
        items.forEach { item ->
            try {
                val values = ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        if (item.isVideo) "Movies/MediaAIStudio/$cleanName/" else "Pictures/MediaAIStudio/$cleanName/"
                    )
                }
                moved += if (context.contentResolver.update(item.uri, values, null, null) > 0) 1 else 0
            } catch (_: Exception) {
                // Keep processing the remaining selection.
            }
        }
        moved
    }

    private suspend fun queryImages(): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaItems = mutableListOf<MediaItem>()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Images.Media.RELATIVE_PATH)
        }.toTypedArray()

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val mimeType = cursor.getString(mimeTypeColumn) ?: "image/jpeg"
                val size = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val relativePath = if (relativePathColumn >= 0) cursor.getString(relativePathColumn) else null
                val uri = ContentUris.withAppendedId(collection, id)

                mediaItems.add(MediaItem(uri, name, mimeType, size, dateAdded, width = width, height = height, relativePath = relativePath))
            }
        }
        return@withContext mediaItems
    }

    private suspend fun queryVideos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaItems = mutableListOf<MediaItem>()
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Video.Media.RELATIVE_PATH)
        }.toTypedArray()

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val mimeType = cursor.getString(mimeTypeColumn) ?: "video/mp4"
                val size = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val duration = cursor.getLong(durationColumn)
                val relativePath = if (relativePathColumn >= 0) cursor.getString(relativePathColumn) else null
                val uri = ContentUris.withAppendedId(collection, id)

                mediaItems.add(MediaItem(uri, name, mimeType, size, dateAdded, duration, width, height, relativePath = relativePath))
            }
        }
        return@withContext mediaItems
    }
}
