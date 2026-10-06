package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.update.models.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class UpdateManager(
    private val context: Context,
    private val checker: UpdateChecker = UpdateChecker(context),
    private val downloadManager: DownloadManager = DownloadManager(context),
    private val verifier: ApkVerifier = ApkVerifier(context)
) {

    suspend fun checkForUpdate(): UpdateCheckResult {
        return checker.checkForUpdate()
    }

    fun downloadAndVerify(updateInfo: UpdateInfo): Flow<UpdateState> = flow {
        // Clean up obsolete or leftover update artifacts
        cleanUpOldUpdates(updateInfo.versionCode)

        val finalVerifiedFile = File(context.cacheDir, "update_verified_${updateInfo.versionCode}.apk")

        // 1. Reuse existing valid cached APK if already downloaded and verified
        if (finalVerifiedFile.exists() && finalVerifiedFile.length() > 0) {
            val cachedVerification = verifier.verifyApk(finalVerifiedFile, updateInfo.sha256)
            if (cachedVerification is VerificationResult.Success) {
                emit(UpdateState.ReadyToInstall(finalVerifiedFile))
                return@flow
            } else {
                finalVerifiedFile.delete()
            }
        }

        emit(UpdateState.Downloading(0, updateInfo.apkAssetSize))

        val tempFile = File(context.cacheDir, "update_temp_${updateInfo.versionCode}_${System.currentTimeMillis()}.apk.part")
        if (tempFile.exists()) tempFile.delete()

        var downloadSuccess = false
        try {
            downloadManager.downloadApk(updateInfo.downloadUrl, tempFile).collect { progress ->
                when (progress) {
                    is DownloadProgress.Starting -> {
                        emit(UpdateState.Downloading(0, updateInfo.apkAssetSize))
                    }
                    is DownloadProgress.Downloading -> {
                        emit(UpdateState.Downloading(progress.bytesDownloaded, progress.totalBytes))
                    }
                    is DownloadProgress.Error -> {
                        tempFile.delete()
                        emit(UpdateState.Error(progress.reason))
                    }
                    is DownloadProgress.Finished -> {
                        downloadSuccess = true
                        emit(UpdateState.Verifying)

                        val verifyResult = verifier.verifyApk(progress.file, updateInfo.sha256)
                        if (verifyResult is VerificationResult.Success) {
                            if (finalVerifiedFile.exists()) finalVerifiedFile.delete()
                            if (progress.file.renameTo(finalVerifiedFile)) {
                                emit(UpdateState.ReadyToInstall(finalVerifiedFile))
                            } else {
                                progress.file.delete()
                                emit(UpdateState.Error("FAILED_TO_FINALIZE_CACHED_APK"))
                            }
                        } else {
                            progress.file.delete()
                            val reason = (verifyResult as VerificationResult.Error).reason
                            emit(UpdateState.Error(reason))
                        }
                    }
                }
            }
        } finally {
            if (!downloadSuccess && tempFile.exists()) {
                tempFile.delete()
            }
        }
    }.flowOn(Dispatchers.IO)

    fun cleanUpOldUpdates(currentOrTargetVersionCode: Int) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith("update_verified_") && name.endsWith(".apk")) {
                    val code = Regex("\\d+").find(name)?.value?.toIntOrNull()
                    if (code != null && code < currentOrTargetVersionCode) {
                        file.delete()
                    }
                } else if (name.endsWith(".apk.part") || name.startsWith("update_temp_")) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun installApk(apkFile: File): InstallResult {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            return InstallResult.Error("FILE_NOT_FOUND")
        }

        // Handle modern unknown sources permission requirement (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return InstallResult.PermissionRequired
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            InstallResult.Success
        } catch (e: Exception) {
            InstallResult.Error(e.message ?: "INSTALLATION_LAUNCH_FAILED")
        }
    }
}

sealed class UpdateState {
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : UpdateState()
    object Verifying : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val reason: String) : UpdateState()
}

sealed class InstallResult {
    object Success : InstallResult()
    object PermissionRequired : InstallResult()
    data class Error(val message: String) : InstallResult()
}
