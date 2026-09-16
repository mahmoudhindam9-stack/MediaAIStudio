package com.example.update

import com.example.update.models.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GitHubReleaseClient {
    private val client = OkHttpClient()
    // Using a placeholder repository if none is configured
    private val owner = "placeholder_owner"
    private val repo = "placeholder_repo"

    suspend fun getLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("X-GitHub-Api-Version", "2026-03-10")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                
                val releaseId = json.optLong("id", 0L)
                val tagName = json.optString("tag_name", "")
                val releaseName = json.optString("name", "")
                val releaseNotes = json.optString("body", "")
                val publishedAt = json.optString("published_at", "")
                val htmlUrl = json.optString("html_url", "")
                
                // Parse assets to find the APK
                val assets = json.optJSONArray("assets") ?: return@use null
                var apkUrl = ""
                var apkSize = 0L
                var apkDigest: String? = null
                
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "").lowercase()
                    if (name.endsWith(".apk") && !name.contains("debug") && !name.contains("test") && !name.contains("unaligned")) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        // Optionally extract digest from release notes or another asset, though usually it's in a .sha256 file
                        break
                    }
                }
                
                if (apkUrl.isEmpty()) return@use null

                // For this placeholder logic, try to parse versionCode/versionName from the tag
                // Assuming tag format like "v1.0.0-15" (versionName-versionCode)
                // If we can't parse it, we default to 0 to prevent accidental updates
                var vName = tagName.removePrefix("v")
                var vCode = 0
                val parts = vName.split("-")
                if (parts.size >= 2) {
                    vName = parts[0]
                    vCode = parts[1].toIntOrNull() ?: 0
                }
                
                ReleaseInfo(
                    releaseId = releaseId,
                    tagName = tagName,
                    versionName = vName,
                    versionCode = vCode,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    publishedAt = publishedAt,
                    htmlUrl = htmlUrl,
                    apkAssetUrl = apkUrl,
                    apkAssetSize = apkSize,
                    apkAssetDigest = apkDigest
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
