package com.example.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.update.models.ReleaseInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

class UpdateManager(private val context: Context) {
    private val checker = UpdateChecker(context)
    private val downloadManager = DownloadManager(context)
    private val verifier = ApkVerifier(context)

    suspend fun checkForUpdate(): UpdateCheckResult {
        return checker.checkForUpdate()
    }

    fun downloadAndVerify(releaseInfo: ReleaseInfo): Flow<UpdateState> = flow {
        emit(UpdateState.Downloading(0, 100))
        
        val tempFile = File(context.cacheDir, "update_temp_${releaseInfo.versionCode}.apk")
        val finalFile = File(context.cacheDir, "update_verified_${releaseInfo.versionCode}.apk")
        
        if (finalFile.exists()) {
            finalFile.delete()
        }
        
        downloadManager.downloadApk(releaseInfo.apkAssetUrl, tempFile).collect { progress ->
            when (progress) {
                is DownloadProgress.Starting -> {
                    emit(UpdateState.Downloading(0, 100))
                }
                is DownloadProgress.Downloading -> {
                    emit(UpdateState.Downloading(progress.bytesDownloaded, progress.totalBytes))
                }
                is DownloadProgress.Error -> {
                    tempFile.delete()
                    emit(UpdateState.Error(progress.reason))
                }
                is DownloadProgress.Finished -> {
                    emit(UpdateState.Verifying)
                    val result = verifier.verifyApk(progress.file, releaseInfo.apkAssetDigest)
                    if (result is VerificationResult.Success) {
                        progress.file.renameTo(finalFile)
                        emit(UpdateState.ReadyToInstall(finalFile))
                    } else {
                        progress.file.delete()
                        val reason = (result as VerificationResult.Error).reason
                        emit(UpdateState.Error(reason))
                    }
                }
            }
        }
    }
    
    fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

sealed class UpdateState {
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : UpdateState()
    object Verifying : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val reason: String) : UpdateState()
}
