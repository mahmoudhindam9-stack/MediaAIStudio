package com.example.ai.image.inpainting

import android.content.Context
import com.example.ai.model.ModelArtifactManager
import kotlinx.coroutines.runBlocking
import java.io.File

class LaMaModelLoader(private val context: Context) {
    private val artifacts = ModelArtifactManager(context)

    fun getModelFile(): File {
        val artifact = artifacts.artifact("llama/inpainting_lama_2025jan")
            ?: error("MODEL_NOT_CONFIGURED: LaMa artifact is missing")
        val file = artifacts.localFile(artifact)
        if (artifacts.isValid(file, artifact)) return file

        return runBlocking {
            artifacts.ensureInstalled(artifact)
        }
    }
}
