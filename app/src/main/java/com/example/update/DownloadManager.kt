package com.example.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class DownloadManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    fun downloadApk(url: String, tempFile: File): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Starting)

        // Security check: Only HTTPS URLs are permitted
        if (!url.startsWith("https://", ignoreCase = true)) {
            tempFile.delete()
            emit(DownloadProgress.Error("INSECURE_URL: Only HTTPS downloads are permitted"))
            return@flow
        }

        var success = false
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MediaAIStudio-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    tempFile.delete()
                    emit(DownloadProgress.Error("DOWNLOAD_FAILED_HTTP_${response.code}"))
                    return@use
                }

                val body = response.body
                if (body == null) {
                    tempFile.delete()
                    emit(DownloadProgress.Error("EMPTY_RESPONSE_BODY"))
                    return@use
                }

                val contentLength = body.contentLength()
                val parentDir = tempFile.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }

                var bytesCopied = 0L
                var lastProgressEmit = 0L

                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)

                outputStream.use { out ->
                    inputStream.use { inp ->
                        val buffer = ByteArray(8 * 1024)
                        var bytes = inp.read(buffer)
                        while (bytes >= 0) {
                            out.write(buffer, 0, bytes)
                            bytesCopied += bytes

                            val now = System.currentTimeMillis()
                            if (now - lastProgressEmit > 100) {
                                emit(DownloadProgress.Downloading(bytesCopied, contentLength))
                                lastProgressEmit = now
                            }
                            bytes = inp.read(buffer)
                        }
                        out.flush()
                    }
                }

                // Verify file is not empty or truncated
                if (contentLength > 0 && bytesCopied < contentLength) {
                    tempFile.delete()
                    emit(DownloadProgress.Error("INCOMPLETE_DOWNLOAD: expected $contentLength bytes, received $bytesCopied"))
                    return@use
                }

                if (!tempFile.exists() || tempFile.length() <= 0) {
                    tempFile.delete()
                    emit(DownloadProgress.Error("DOWNLOADED_FILE_EMPTY"))
                    return@use
                }

                success = true
                emit(DownloadProgress.Finished(tempFile))
            }
        } catch (e: Exception) {
            tempFile.delete()
            emit(DownloadProgress.Error("NETWORK_ERROR: ${e.message ?: "Unknown error"}"))
        } finally {
            if (!success && tempFile.exists()) {
                tempFile.delete()
            }
        }
    }.flowOn(Dispatchers.IO)
}

sealed class DownloadProgress {
    object Starting : DownloadProgress()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress()
    data class Finished(val file: File) : DownloadProgress()
    data class Error(val reason: String) : DownloadProgress()
}
