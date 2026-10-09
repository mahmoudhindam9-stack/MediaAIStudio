package com.example.ai.generative

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.ai.core.AIError
import com.example.ai.core.AIProgress
import com.example.ai.core.AIProviderType
import com.example.ai.core.AIRequest
import com.example.ai.core.AIResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class CloudGenerativeClient(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val appContext = context.applicationContext
    private val baseUrl = BuildConfig.MEDIA_AI_BACKEND_URL.trimEnd('/')
    private val apiKey = BuildConfig.MEDIA_AI_BACKEND_API_KEY.trim()

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    suspend fun processImageRequest(
        request: AIRequest,
        onProgress: (AIProgress) -> Unit
    ): AIResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext AIResult.Error(AIError.ProviderUnavailable)
        if (request.sourceUri.isBlank()) return@withContext AIResult.Error(AIError.InvalidInput)

        try {
            onProgress(AIProgress(0.05f, "Preparing cloud request"))
            val response = postMediaRequest(
                endpoint = "/image/process",
                sourceUri = request.sourceUri,
                mimeType = appContext.contentResolver.getType(Uri.parse(request.sourceUri)) ?: "image/*",
                fields = requestFields(request)
            )

            val jobId = response.optString("jobId", "").ifEmpty { response.optString("id", "") }
            if (jobId.isNotBlank()) {
                onProgress(AIProgress(0.15f, "Cloud job started"))
                return@withContext waitForImageJob(jobId, onProgress, request::class.simpleName ?: "CloudAI")
            }

            val output = response.optString("outputUrl", "")
                .ifEmpty { response.optString("outputUri", "") }
                .ifEmpty { response.optJSONObject("result")?.optString("outputUrl", "") ?: "" }

            if (output.isBlank()) {
                return@withContext AIResult.Error(
                    AIError.Unknown(response.optString("error", "Cloud backend returned no output"))
                )
            }

            onProgress(AIProgress(0.85f, "Downloading result"))
            val outputUri = materializeOutput(output, "cloud_image_result")
                ?: return@withContext AIResult.Error(AIError.ProcessingFailure)

            onProgress(AIProgress(1f, "Cloud processing complete"))
            AIResult.Success(
                outputUri = outputUri.toString(),
                processingType = request::class.simpleName ?: "CloudAI",
                providerUsed = AIProviderType.CLOUD
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AIResult.Error(AIError.NetworkFailure)
        } catch (e: Exception) {
            AIResult.Error(AIError.Unknown(e.message ?: "Cloud AI request failed"))
        }
    }

    suspend fun submitImageJob(request: GenerativeRequest): String? =
        submitGenerativeJob("/image/generate", request, "image/*")

    suspend fun submitVideoJob(request: GenerativeRequest): String? =
        submitGenerativeJob("/video/generate", request, "video/*")

    private suspend fun submitGenerativeJob(
        endpoint: String,
        request: GenerativeRequest,
        defaultMimeType: String
    ): String? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null

        var temporarySource: File? = null
        var maskFile: File? = null
        try {
            val sourceUri = request.sourceUri?.toString()?.takeIf { it.isNotBlank() }
            val sourceFile = sourceUri?.let {
                temporarySource = copyUriToCache(Uri.parse(it))
                temporarySource
            }

            val fields = linkedMapOf(
                "prompt" to request.prompt,
                "type" to request.type.name
            )
            request.parameters.forEach { (key, value) ->
                fields["param_$key"] = value.toString()
            }

            val maskUri = request.maskUri?.toString().orEmpty()
            if (maskUri.isNotBlank()) {
                maskFile = copyUriToCache(Uri.parse(maskUri))
            }

            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            fields.forEach { (key, value) -> builder.addFormDataPart(key, value) }

            sourceFile?.let {
                val mime = if (sourceUri != null) {
                    appContext.contentResolver.getType(Uri.parse(sourceUri)) ?: defaultMimeType
                } else {
                    defaultMimeType
                }
                builder.addFormDataPart("media", it.name, it.asRequestBody(mime.toMediaTypeOrNull()))
            }

            maskFile?.let {
                builder.addFormDataPart("mask", it.name, it.asRequestBody("image/png".toMediaTypeOrNull()))
            }

            val response = execute(
                Request.Builder()
                    .url("$baseUrl$endpoint")
                    .post(builder.build())
                    .build()
            )
            response.optString("jobId", "")
                .ifEmpty { response.optString("id", "") }
                .takeIf { it.isNotBlank() }
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Exception) {
            null
        } finally {
            maskFile?.delete()
            temporarySource?.delete()
        }
    }

    suspend fun transcribeVideo(
        sourceUri: Uri,
        language: String = "auto"
    ): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        var temporarySource: File? = null
        try {
            temporarySource = copyUriToCache(sourceUri)
            val mimeType = appContext.contentResolver.getType(sourceUri)?.toMediaTypeOrNull()
                ?: "video/mp4".toMediaTypeOrNull()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("language", language)
                .addFormDataPart(
                    "media",
                    temporarySource.name,
                    temporarySource.asRequestBody(mimeType)
                )
                .build()

            val response = execute(
                Request.Builder()
                    .url("$baseUrl/video/transcribe")
                    .post(body)
                    .build()
            )
            val jobId = response.optString("jobId", "")
                .ifEmpty { response.optString("id", "") }
            if (jobId.isBlank()) return@withContext response

            repeat(MAX_POLLS) { attempt ->
                coroutineContext.ensureActive()
                val job = execute(
                    authorizedRequest("$baseUrl/jobs/${Uri.encode(jobId)}")
                        .get()
                        .build()
                )
                when (job.optString("state", "PROCESSING").uppercase()) {
                    "COMPLETED", "COMPLETE", "SUCCEEDED", "SUCCESS" -> {
                        return@withContext job.optJSONObject("result") ?: job
                    }
                    "FAILED", "ERROR", "CANCELLED", "CANCELED" -> return@withContext null
                }
                delay(if (attempt < 10) 1_000L else 2_000L)
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            temporarySource?.delete()
        }
    }

    suspend fun getJobStatus(jobId: String): GenerativeJobResponse? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        try {
            val json = execute(
                authorizedRequest("$baseUrl/jobs/${Uri.encode(jobId)}")
                    .get()
                    .build()
            )
            val resultObj = json.optJSONObject("result")
            val nestedError = resultObj?.optString("error", "").orEmpty()
            val nestedOutput = resultObj?.optString("outputUrl", "").orEmpty()
            GenerativeJobResponse(
                state = json.optString("state", "FAILED"),
                message = json.optString("message", ""),
                progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                errorReason = json.optString("error", "").takeIf { it.isNotBlank() } ?: nestedError.takeIf { it.isNotBlank() },
                outputUrl = json.optString("outputUrl", "").takeIf { it.isNotBlank() } ?: nestedOutput.takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun cancelJob(jobId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext false
        try {
            execute(
                authorizedRequest("$baseUrl/jobs/${Uri.encode(jobId)}")
                    .delete()
                    .build()
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun materializeOutput(output: String, prefix: String = "cloud_result"): Uri? =
        withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(output) }.getOrNull() ?: return@withContext null
            if (uri.scheme == "content" || uri.scheme == "file") return@withContext uri
            if (uri.scheme != "http" && uri.scheme != "https") return@withContext null

            val response = client.newCall(authorizedRequest(output).get().build()).execute()
            response.use {
                if (!it.isSuccessful) return@withContext null
                val body = it.body ?: return@withContext null
                val remoteExtension = uri.lastPathSegment
                    ?.substringAfterLast('.', "")
                    ?.lowercase()
                val extension = when (remoteExtension) {
                    "glb" -> ".glb"
                    "gltf" -> ".gltf"
                    "obj" -> ".obj"
                    "ply" -> ".ply"
                    "mp4" -> ".mp4"
                    "webp" -> ".webp"
                    "jpg", "jpeg" -> ".jpg"
                    else -> when (body.contentType()?.subtype?.lowercase()) {
                        "mp4" -> ".mp4"
                        "webp" -> ".webp"
                        "jpeg", "jpg" -> ".jpg"
                        "gltf-binary" -> ".glb"
                        "gltf+json" -> ".gltf"
                        else -> ".png"
                    }
                }
                val file = File.createTempFile("${prefix}_", extension, appContext.cacheDir)
                try {
                    body.byteStream().use { input ->
                        file.outputStream().buffered().use { outputStream ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                outputStream.write(buffer, 0, read)
                            }
                        }
                    }
                    FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        file
                    )
                } catch (e: CancellationException) {
                    file.delete()
                    throw e
                } catch (_: Exception) {
                    file.delete()
                    null
                }
            }
        }

    private suspend fun waitForImageJob(
        jobId: String,
        onProgress: (AIProgress) -> Unit,
        processingType: String
    ): AIResult {
        repeat(MAX_POLLS) { attempt ->
            coroutineContext.ensureActive()
            val status = getJobStatus(jobId)
                ?: return AIResult.Error(AIError.NetworkFailure)

            onProgress(AIProgress(status.progress.coerceIn(0f, 1f), status.message.ifBlank { "Cloud processing…" }))

            when (status.state.uppercase()) {
                "COMPLETED", "COMPLETE", "SUCCEEDED", "SUCCESS" -> {
                    val output = status.outputUrl
                        ?: return AIResult.Error(AIError.ProcessingFailure)
                    val outputUri = materializeOutput(output, "cloud_image_result")
                        ?: return AIResult.Error(AIError.ProcessingFailure)
                    onProgress(AIProgress(1f, "Cloud processing complete"))
                    return AIResult.Success(
                        outputUri = outputUri.toString(),
                        processingType = processingType,
                        providerUsed = AIProviderType.CLOUD
                    )
                }
                "FAILED", "ERROR" -> {
                    return AIResult.Error(AIError.Unknown(status.errorReason ?: "Cloud job failed"))
                }
                "CANCELLED", "CANCELED" -> return AIResult.Error(AIError.Cancelled)
            }

            delay(if (attempt < 10) 1_000L else 2_000L)
        }

        return AIResult.Error(AIError.Timeout)
    }

    private fun requestFields(request: AIRequest): Map<String, String> = buildMap {
        put("operation", request::class.simpleName ?: "AI")
        when (request) {
            is AIRequest.Enhance -> put("enhanceType", request.enhanceType)
            is AIRequest.Upscale -> put("scaleFactor", request.scaleFactor.toString())
            is AIRequest.Restyle -> put("prompt", request.prompt)
            is AIRequest.ObjectRemoval -> put("maskData", request.maskData)
            is AIRequest.BackgroundRemoval -> Unit
            is AIRequest.DetectObjects -> Unit
        }
    }

    private suspend fun postMediaRequest(
        endpoint: String,
        sourceUri: String,
        mimeType: String,
        fields: Map<String, String>
    ): JSONObject {
        val temporarySource = copyUriToCache(Uri.parse(sourceUri))
        return try {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            fields.forEach { (key, value) -> builder.addFormDataPart(key, value) }
            builder.addFormDataPart("media", temporarySource.name, temporarySource.asRequestBody(mimeType.toMediaTypeOrNull()))
            execute(
                Request.Builder()
                    .url("$baseUrl$endpoint")
                    .post(builder.build())
                    .build()
            )
        } finally {
            temporarySource.delete()
        }
    }

    private fun copyUriToCache(uri: Uri): File {
        val extension = appContext.contentResolver.getType(uri)?.substringAfterLast('/')?.let { ".$it" } ?: ".bin"
        val file = File.createTempFile("cloud_input_", extension, appContext.cacheDir)
        val input = appContext.contentResolver.openInputStream(uri)
            ?: run {
                file.delete()
                throw IOException("Unable to read source media")
            }
        input.use { source ->
            file.outputStream().buffered().use { target -> source.copyTo(target) }
        }
        return file
    }

    private fun authorizedRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "MediaAIStudio/${BuildConfig.VERSION_NAME}")
            .apply {
                if (apiKey.isNotBlank() && isBackendUrl(url)) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }

    private fun isBackendUrl(url: String): Boolean {
        val backend = Uri.parse(baseUrl)
        val request = Uri.parse(url)
        return !backend.host.isNullOrBlank() &&
            backend.scheme.equals(request.scheme, ignoreCase = true) &&
            backend.host.equals(request.host, ignoreCase = true) &&
            backend.port == request.port
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request.newBuilder().apply {
            if (request.header("Accept") == null) addHeader("Accept", "application/json")
            if (request.header("User-Agent") == null) addHeader("User-Agent", "MediaAIStudio/${BuildConfig.VERSION_NAME}")
            if (apiKey.isNotBlank() && isBackendUrl(request.url.toString())) {
                addHeader("Authorization", "Bearer $apiKey")
            }
        }.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                throw IOException("HTTP ${response.code}${detail.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}")
            }
            if (body.isBlank()) throw IOException("Cloud backend returned an empty response")
            return JSONObject(body)
        }
    }

    companion object {
        private const val MAX_POLLS = 300
    }
}

data class GenerativeJobResponse(
    val state: String,
    val message: String,
    val progress: Float,
    val errorReason: String?,
    val outputUrl: String?
)