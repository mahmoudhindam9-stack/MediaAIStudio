package com.example.update

import android.content.Context
import android.content.pm.PackageManager
import com.example.update.models.ReleaseInfo

class UpdateChecker(private val context: Context) {
    private val gitHubClient = GitHubReleaseClient()

    suspend fun checkForUpdate(): UpdateCheckResult {
        val latestRelease = gitHubClient.getLatestRelease() ?: return UpdateCheckResult.Error("NO_RELEASE")
        
        val currentVersionCode = getCurrentVersionCode()
        
        if (latestRelease.versionCode > currentVersionCode) {
            return UpdateCheckResult.UpdateAvailable(latestRelease)
        }
        
        return UpdateCheckResult.UpToDate
    }
    
    private fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }
}

sealed class UpdateCheckResult {
    data class UpdateAvailable(val releaseInfo: ReleaseInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val reason: String) : UpdateCheckResult()
}
