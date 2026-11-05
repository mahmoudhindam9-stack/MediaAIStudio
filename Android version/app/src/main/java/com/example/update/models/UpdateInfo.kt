package com.example.update.models

/**
 * Encapsulates update metadata fetched from the distribution repository.
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val sha256: String?,
    val releaseNotes: String,
    val publishedAt: String,
    val isPrerelease: Boolean = false,
    val releaseName: String = "",
    val releaseId: Long = 0L,
    val apkAssetSize: Long = 0L,
    val tagName: String = "",
    val fileName: String = ""
) {
    val apkAssetUrl: String get() = downloadUrl
    val apkAssetDigest: String? get() = sha256
}
