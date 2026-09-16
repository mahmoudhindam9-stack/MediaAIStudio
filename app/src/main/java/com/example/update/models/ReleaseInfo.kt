package com.example.update.models

import java.util.Date

data class ReleaseInfo(
    val releaseId: Long,
    val tagName: String,
    val versionName: String,
    val versionCode: Int,
    val releaseName: String,
    val releaseNotes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkAssetUrl: String,
    val apkAssetSize: Long,
    val apkAssetDigest: String?
)
