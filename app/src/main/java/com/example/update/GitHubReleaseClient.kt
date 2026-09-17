package com.example.update

import com.example.update.models.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class GitHubReleaseClient {
    private val client = OkHttpClient()

    // This is the actual public repository used by MediaAIStudio updates.
    private val owner = "mahmoudhindam9-stack"
    private val repo = "MediaAIStudio"

    suspend fun getLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            // Use the releases collection instead of /releases/latest so prereleases
            // (such as the current development builds) are also considered.
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
                val releases = JSONArray(body)

                val candidates = mutableListOf<ReleaseInfo>()
                for (i in 0 until releases.length()) {
                    val release = releases.optJSONObject(i) ?: continue
                    if (release.optBoolean("draft", false)) continue

                    parseRelease(release)?.let { candidates += it }
                }

                // VersionCode is the authoritative Android update ordering.
                // Published time is only a deterministic tie-breaker.
                candidates
                    .filter { it.versionCode > 0 }
                    .maxWithOrNull(
                        compareBy<ReleaseInfo> { it.versionCode }
                            .thenBy { it.publishedAt }
                    )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo? {
        val releaseId = json.optLong("id", 0L)
        val tagName = json.optString("tag_name", "").trim()
        val releaseName = json.optString("name", "")
        val releaseNotes = json.optString("body", "")
        val publishedAt = json.optString("published_at", "")
        val htmlUrl = json.optString("html_url", "")
        val assets = json.optJSONArray("assets") ?: return null

        var apkUrl = ""
        var apkSize = 0L
        var apkDigest: String? = null

        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "").lowercase()
            if (name.endsWith(".apk") &&
                !name.contains("debug") &&
                !name.contains("test") &&
                !name.contains("unaligned") &&
                !name.endsWith(".idsig")
            ) {
                apkUrl = asset.optString("browser_download_url", "")
                apkSize = asset.optLong("size", 0L)
                apkDigest = asset.optString("digest", "")
                    .removePrefix("sha256:")
                    .takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
                break
            }
        }

        if (apkUrl.isEmpty()) return null

        val versionCode = parseVersionCode(releaseNotes, tagName)
        if (versionCode <= 0) return null

        val versionName = parseVersionName(releaseNotes, tagName)

        return ReleaseInfo(
            releaseId = releaseId,
            tagName = tagName,
            versionName = versionName,
            versionCode = versionCode,
            releaseName = releaseName,
            releaseNotes = releaseNotes,
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            apkAssetUrl = apkUrl,
            apkAssetSize = apkSize,
            apkAssetDigest = apkDigest
        )
    }

    private fun parseVersionCode(notes: String, tagName: String): Int {
        val notesMatch = Regex("(?i)\\bversionCode\\s*[:=]?\\s*(\\d+)\\b").find(notes)
        val notesCode = notesMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (notesCode != null) return notesCode

        // Backward-compatible fallback for tags such as v1.3.0-4.
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
