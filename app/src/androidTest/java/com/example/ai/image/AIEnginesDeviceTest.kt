package com.example.ai.image

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ai.core.AIProgress
import com.example.ai.core.AIResult
import com.example.ai.image.inpainting.LaMaInpaintingEngine
import com.example.ai.image.upscale.RealEsrganUpscaleEngine
import com.example.ai.image.enhancement.CpgaLowLightEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class AIEnginesDeviceTest {

    private fun createDummyBitmapUri(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val file = File(context.cacheDir, "dummy_input.png")
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return Uri.fromFile(file).toString()
    }

    @Test
    fun testLaMaModelInference() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LaMaInpaintingEngine(context)
        val uri = createDummyBitmapUri()
        
        val result = engine.process(uri, "[0.25,0.25,0.75,0.75]") { progress ->
            // ignore
        }
        
        assertTrue("LaMa inference should succeed", result is AIResult.Success)
        val success = result as AIResult.Success
        assertNotNull(success.outputUri)
        engine.release()
    }

    @Test
    fun testRealEsrganModelInference() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = RealEsrganUpscaleEngine(context)
        val uri = createDummyBitmapUri()
        
        val result = engine.process(uri, 2) { progress ->
            // ignore
        }
        
        assertTrue("Real-ESRGAN inference should succeed", result is AIResult.Success)
        val success = result as AIResult.Success
        assertNotNull(success.outputUri)
        engine.release()
    }

    @Test
    fun testCpgaModelInference() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = CpgaLowLightEngine(context)
        val uri = createDummyBitmapUri()
        
        val result = engine.process(uri) { progress ->
            // ignore
        }
        
        assertTrue("CPGA inference should succeed", result is AIResult.Success)
        val success = result as AIResult.Success
        assertNotNull(success.outputUri)
        engine.release()
    }
}
