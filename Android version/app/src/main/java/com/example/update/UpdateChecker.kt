package com.example.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.update.models.UpdateInfo

class UpdateChecker(
    private val context: Context,
    private val gitHubClient: GitHubReleaseClient = GitHubReleaseClient(),
    private val currentVersionCodeProvider: (() -> Int)? = null
) {

    suspend fun checkForUpdate(): UpdateCheckResult {
        val latestRelease = try {
            gitHubClient.getLatestRelease()
        } catch (e: Exception) {
            return UpdateCheckResult.Error("NETWORK_ERROR: ${e.message ?: "Failed to connect"}")
        } ?: return UpdateCheckResult.Error("NO_RELEASE_FOUND")

        val currentVersionCode = getCurrentVersionCode()

        return if (latestRelease.versionCode > currentVersionCode) {
            UpdateCheckResult.UpdateAvailable(latestRelease)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    fun getCurrentVersionCode(): Int {
        if (currentVersionCodeProvider != null) {
            return currentVersionCodeProvider.invoke()
        }
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (_: PackageManager.NameNotFoundException) {
            0
        }
    }
}

sealed class UpdateCheckResult {
    data class UpdateAvailable(val releaseInfo: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val reason: String) : UpdateCheckResult()
}
