package com.example.update.models

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val fileName: String,
    val sha256: String? = null,
    val releaseNotes: String = "",
    val publishedAt: String = "",
    val isPrerelease: Boolean = false,
    val assetSize: Long = 0L,
    val releaseName: String = "",
    val tagName: String = "v$versionName",
    val releaseId: Long = 0L,
    val htmlUrl: String = ""
) {
    // Backward-compatible accessors for legacy callers
    val apkAssetUrl: String get() = downloadUrl
    val apkAssetSize: Long get() = assetSize
    val apkAssetDigest: String? get() = sha256
}

typealias ReleaseInfo = UpdateInfo
