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

class DownloadManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun downloadApk(url: String, tempFile: File): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Starting)
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadProgress.Error("DOWNLOAD_FAILED"))
                    return@use
                }
                
                val body = response.body
                if (body == null) {
                    emit(DownloadProgress.Error("DOWNLOAD_FAILED"))
                    return@use
                }

                val contentLength = body.contentLength()
                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)
                
                var bytesCopied: Long = 0
                val buffer = ByteArray(8 * 1024)
                var bytes = inputStream.read(buffer)
                
                var lastProgressEmit = 0L

                while (bytes >= 0) {
                    outputStream.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    
                    val now = System.currentTimeMillis()
                    if (now - lastProgressEmit > 100) {
                        emit(DownloadProgress.Downloading(bytesCopied, contentLength))
                        lastProgressEmit = now
                    }
                    bytes = inputStream.read(buffer)
                }
                
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                
                emit(DownloadProgress.Finished(tempFile))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emit(DownloadProgress.Error("NETWORK_ERROR"))
        }
    }.flowOn(Dispatchers.IO)
}

sealed class DownloadProgress {
    object Starting : DownloadProgress()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress()
    data class Finished(val file: File) : DownloadProgress()
    data class Error(val reason: String) : DownloadProgress()
}
