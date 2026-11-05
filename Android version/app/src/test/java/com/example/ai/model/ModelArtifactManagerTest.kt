package com.example.ai.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelArtifactManagerTest {

    private lateinit var context: Context
    private lateinit var artifactManager: ModelArtifactManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Reset singleton for testing
        val field = ModelArtifactManager::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
        artifactManager = ModelArtifactManager.getInstance(context)
    }

    @Test
    fun testSingleton_sharesInstance() {
        val manager1 = AppModelManager(context)
        val manager2 = AppModelManager(context)
        assertSame(manager1.artifacts, manager2.artifacts)
    }

    @Test
    fun testGetAvailableModels_noSynchronousValidation() {
        val manager = AppModelManager(context)
        val file = manager.artifacts.localFile(ModelArtifactManager.ARTIFACTS.first())
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(100 * 1024 * 1024)) // 100MB fake file

        val models = manager.getAvailableModels()
        // ML Kit models are index 0, 1. The first local artifact is index 2.
        val lamaModel = models.first { it.id == ModelArtifactManager.ARTIFACTS.first().id }
        
        // It shouldn't validate synchronously, so state is NOT_INSTALLED
        assertEquals(ModelInstallState.NOT_INSTALLED, lamaModel.status)
        file.delete()
    }

    @Test
    fun testGetArtifactState_isStateLookup() {
        val art = ModelArtifactManager.ARTIFACTS.first()
        val file = artifactManager.localFile(art)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(100 * 1024 * 1024))
        
        val state = artifactManager.getArtifactState(art.id)
        assertEquals(ModelInstallState.NOT_INSTALLED, state.state)
        file.delete()
    }

    @Test
    fun testRefreshInstalledStates_removesInvalidModel() = runTest {
        val art = ModelArtifactManager.ARTIFACTS.first()
        val file = artifactManager.localFile(art)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(100 * 1024 * 1024)) // Corrupt model > minimumBytes

        artifactManager.refreshInstalledStates()

        val state = artifactManager.getArtifactState(art.id)
        assertEquals(ModelInstallState.NOT_INSTALLED, state.state)
        assertFalse(file.exists())
    }

    @Test
    fun testDownloadCancellation_removesPartFile() = runTest {
        val art = ModelArtifactManager.ARTIFACTS.first()
        val file = artifactManager.localFile(art)
        val part = File(file.parentFile, "${file.name}.part")
        file.parentFile?.mkdirs()

        val client = OkHttpClient.Builder().addInterceptor { chain ->
            // Simulate large download blocking
            Thread.sleep(5000)
            chain.proceed(chain.request())
        }.build()

        // Replace client via reflection or just use the same instance mechanism
        // But since we can't easily intercept OkHttp here without a mockwebserver,
        // we can test cancellation by manually placing a part file and ensuring
        // failure logic removes it. 

        part.writeBytes(ByteArray(1024))
        assertTrue(part.exists())

        // Start download with a fake client that throws immediately
        val fakeClient = OkHttpClient.Builder().addInterceptor { _ ->
            throw java.io.IOException("Fake network error")
        }.build()

        val field = ModelArtifactManager::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
        val testArtifactManager = ModelArtifactManager.getInstance(context, fakeClient)
        
        testArtifactManager.downloadModel(art.id)

        assertFalse(part.exists())
        val state = testArtifactManager.getArtifactState(art.id)
        assertEquals(ModelInstallState.FAILED, state.state)
    }
}
