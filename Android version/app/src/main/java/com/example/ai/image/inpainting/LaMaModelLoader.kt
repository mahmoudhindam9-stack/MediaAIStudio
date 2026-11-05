package com.example.ai.image.inpainting

import android.content.Context
import com.example.ai.model.ModelArtifactManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LaMaModelLoader(context: Context) {
    private val appContext = context.applicationContext
    private val artifacts = ModelArtifactManager(appContext)

    suspend fun getModelFile(): File = withContext(Dispatchers.IO) {
        val artifact = artifacts.artifact("llama/inpainting_lama_2025jan")
            ?: error("MODEL_NOT_CONFIGURED: LaMa artifact is missing")
        artifacts.ensureInstalled(artifact)
    }
}
