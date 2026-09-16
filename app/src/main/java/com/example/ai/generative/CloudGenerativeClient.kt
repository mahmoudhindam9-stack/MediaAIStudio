package com.example.ai.generative

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

class CloudGenerativeClient {
    private val client = OkHttpClient()
    private val baseUrl = "http://10.0.2.2:3000/v1/ai"

    suspend fun submitImageJob(prompt: String, type: String, file: File? = null): String? = withContext(Dispatchers.IO) {
        try {
            val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("prompt", prompt)
                .addFormDataPart("type", type)
            
            file?.let {
                val reqFile = it.asRequestBody("image/*".toMediaTypeOrNull())
                requestBodyBuilder.addFormDataPart("media", it.name, reqFile)
            }
            
            val request = Request.Builder()
                .url("$baseUrl/image/generate")
                .post(requestBodyBuilder.build())
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val resBody = response.body?.string() ?: return@use null
                val json = JSONObject(resBody)
                return@use json.optString("jobId", "").ifEmpty { null }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun submitVideoJob(prompt: String, type: String, file: File? = null): String? = withContext(Dispatchers.IO) {
        try {
            val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("prompt", prompt)
                .addFormDataPart("type", type)
            
            file?.let {
                val reqFile = it.asRequestBody("video/*".toMediaTypeOrNull())
                requestBodyBuilder.addFormDataPart("media", it.name, reqFile)
            }
                
            val request = Request.Builder()
                .url("$baseUrl/video/generate")
                .post(requestBodyBuilder.build())
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val resBody = response.body?.string() ?: return@use null
                val json = JSONObject(resBody)
                return@use json.optString("jobId", "").ifEmpty { null }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getJobStatus(jobId: String): GenerativeJobResponse? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/jobs/$jobId")
                .get()
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val resBody = response.body?.string() ?: return@use null
                val json = JSONObject(resBody)
                
                val state = json.optString("state", "FAILED")
                val message = json.optString("message", "")
                val progress = json.optDouble("progress", 0.0).toFloat()
                
                var errorReason: String? = null
                var outputUrl: String? = null
                
                if (json.has("result")) {
                    val resultObj = json.optJSONObject("result")
                    if (resultObj != null) {
                        errorReason = resultObj.optString("error", "").ifEmpty { null }
                        outputUrl = resultObj.optString("outputUrl", "").ifEmpty { null }
                    }
                }
                
                GenerativeJobResponse(state, message, progress, errorReason, outputUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class GenerativeJobResponse(
    val state: String,
    val message: String,
    val progress: Float,
    val errorReason: String?,
    val outputUrl: String?
)
