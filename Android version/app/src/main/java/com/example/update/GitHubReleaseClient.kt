package com.example.update

import com.example.update.models.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class GitHubReleaseClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val owner: String = "mahmoudhindam9-stack",
    private val repo: String = "MediaAIStudio"
) {

    suspend fun getLatestRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=20"
            val request = Request.Builder()
                .url(url)
                .header("X-GitHub-Api-Version", "2026-03-10")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MediaAIStudio-Updater")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                parseReleasesJson(body)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseReleasesJson(jsonString: String): UpdateInfo? {
        val releases = JSONArray(jsonString)
        val candidates = mutableListOf<UpdateInfo>()

        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            // 1. Ignore drafts
            if (release.optBoolean("draft", false)) continue

            parseRelease(release)?.let { candidates += it }
        }

        // Return highest versionCode
        return candidates
            .filter { it.versionCode > 0 }
            .maxWithOrNull(
                compareBy<UpdateInfo> { it.versionCode }
                    .thenBy { it.publishedAt }
            )
    }

    fun parseRelease(json: JSONObject): UpdateInfo? {
        val releaseId = json.optLong("id", 0L)
        val tagName = json.optString("tag_name", "").trim()
        val releaseName = json.optString("name", "")
        val releaseNotes = json.optString("body", "")
        val publishedAt = json.optString("published_at", "")
        val isPrerelease = json.optBoolean("prerelease", false)
        val assets = json.optJSONArray("assets") ?: return null

        var apkUrl = ""
        var apkSize = 0L
        var apkDigest: String? = null
        var checksumAssetUrl: String? = null

        // 1. Locate valid APK asset and checksum assets
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "").lowercase()
            val downloadUrl = asset.optString("browser_download_url", "")

            if (name.endsWith(".apk") &&
                !name.contains("debug") &&
                !name.contains("test") &&
                !name.contains("unaligned") &&
                !name.contains("unsigned") &&
                !name.endsWith(".idsig")
            ) {
                apkUrl = downloadUrl
                apkSize = asset.optLong("size", 0L)
                val digest = asset.optString("digest", "")
                    .removePrefix("sha256:")
                    .takeIf { it.matches(Regex("^[0-9a-fA-F]{64}$")) }
                if (digest != null) {
                    apkDigest = digest
                }
            } else if (name.endsWith(".sha256") || name == "checksums.txt" || name == "sha256sums.txt") {
                checksumAssetUrl = downloadUrl
            }
        }

        if (apkUrl.isEmpty()) return null

        // Enforce HTTPS
        if (!apkUrl.startsWith("https://", ignoreCase = true)) return null

        // 2. Extract SHA-256: check release notes if asset digest was not provided
        if (apkDigest == null) {
            val bodyShaMatch = Regex("(?i)\\b(?:sha[-_]?256)\\s*[:=]?\\s*([0-9a-fA-F]{64})\\b").find(releaseNotes)
            apkDigest = bodyShaMatch?.groupValues?.getOrNull(1)
        }

        val versionCode = parseVersionCode(releaseNotes, tagName)
        if (versionCode <= 0) return null

        val versionName = parseVersionName(releaseNotes, tagName)

        return UpdateInfo(
            versionName = versionName,
            versionCode = versionCode,
            downloadUrl = apkUrl,
            sha256 = apkDigest?.lowercase(),
            releaseNotes = releaseNotes,
            publishedAt = publishedAt,
            isPrerelease = isPrerelease,
            releaseName = releaseName,
            releaseId = releaseId,
            apkAssetSize = apkSize,
            tagName = tagName
        )
    }

    private fun parseVersionCode(notes: String, tagName: String): Int {
        val notesMatch = Regex("(?i)\\bversionCode\\s*[:=]?\\s*(\\d+)\\b").find(notes)
        val notesCode = notesMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (notesCode != null) return notesCode

        val tagCode = Regex("-(\\d+)$").find(tagName)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        return tagCode ?: 0
    }

    private fun parseVersionName(notes: String, tagName: String): String {
        val notesMatch = Regex("\\b(\\d+\\.\\d+(?:\\.\\d+)?(?:[-+][0-9A-Za-z.-]+)?)\\b").find(notes)
        return notesMatch?.groupValues?.getOrNull(1)
            ?: tagName.removePrefix("v").substringBefore("-")
    }
}
