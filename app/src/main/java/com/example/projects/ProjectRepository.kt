package com.example.projects

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persistent local project storage. Project data lives in app preferences and survives normal APK updates. */
data class MediaProject(
    val id: String,
    val name: String,
    val mediaType: MediaProjectType,
    val sourceUri: String?,
    val createdAt: Long,
    val updatedAt: Long
)

enum class MediaProjectType {
    PHOTO,
    VIDEO
}

class ProjectRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProjects(): List<MediaProject> = decode(prefs.getString(KEY_PROJECTS, null))
        .sortedByDescending { it.updatedAt }

    fun getProject(id: String): MediaProject? = getProjects().firstOrNull { it.id == id }

    fun createProject(
        name: String,
        mediaType: MediaProjectType,
        sourceUri: String? = null
    ): MediaProject {
        val now = System.currentTimeMillis()
        val project = MediaProject(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { defaultName(mediaType, now) },
            mediaType = mediaType,
            sourceUri = sourceUri,
            createdAt = now,
            updatedAt = now
        )
        saveProjects(getProjects() + project)
        return project
    }

    fun renameProject(id: String, name: String): MediaProject? {
        val cleaned = name.trim()
        if (cleaned.isBlank()) return getProject(id)
        val current = getProject(id) ?: return null
        val updated = current.copy(name = cleaned, updatedAt = System.currentTimeMillis())
        saveProjects(getProjects().map { if (it.id == id) updated else it })
        return updated
    }

    fun updateSource(id: String, sourceUri: String, mediaType: MediaProjectType): MediaProject? {
        val current = getProject(id) ?: return null
        val updated = current.copy(
            sourceUri = sourceUri,
            mediaType = mediaType,
            updatedAt = System.currentTimeMillis()
        )
        saveProjects(getProjects().map { if (it.id == id) updated else it })
        return updated
    }

    fun duplicateProject(id: String): MediaProject? {
        val current = getProject(id) ?: return null
        return createProject(
            name = "${current.name} Copy",
            mediaType = current.mediaType,
            sourceUri = current.sourceUri
        )
    }

    fun deleteProject(id: String) {
        saveProjects(getProjects().filterNot { it.id == id })
    }

    private fun saveProjects(projects: List<MediaProject>) {
        val array = JSONArray()
        projects.forEach { project ->
            array.put(
                JSONObject().apply {
                    put("id", project.id)
                    put("name", project.name)
                    put("mediaType", project.mediaType.name)
                    put("sourceUri", project.sourceUri ?: JSONObject.NULL)
                    put("createdAt", project.createdAt)
                    put("updatedAt", project.updatedAt)
                }
            )
        }
        prefs.edit().putString(KEY_PROJECTS, array.toString()).apply()
    }

    private fun decode(raw: String?): List<MediaProject> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val source = item.optString("sourceUri", "").takeIf { it.isNotBlank() }
                    val mediaType = runCatching {
                        MediaProjectType.valueOf(item.optString("mediaType", MediaProjectType.PHOTO.name))
                    }.getOrDefault(MediaProjectType.PHOTO)
                    add(
                        MediaProject(
                            id = item.getString("id"),
                            name = item.optString("name", "Untitled Project"),
                            mediaType = mediaType,
                            sourceUri = source,
                            createdAt = item.optLong("createdAt", 0L),
                            updatedAt = item.optLong("updatedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun defaultName(type: MediaProjectType, now: Long): String =
        when (type) {
            MediaProjectType.PHOTO -> "Photo Project ${now % 10000}"
            MediaProjectType.VIDEO -> "Video Project ${now % 10000}"
        }

    private companion object {
        const val PREFS_NAME = "media_ai_studio_projects"
        const val KEY_PROJECTS = "projects"
    }
}
