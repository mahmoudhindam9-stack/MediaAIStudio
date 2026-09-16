@file:OptIn(androidx.camera.video.ExperimentalHighSpeedVideo::class, androidx.camera.core.ExperimentalSessionConfig::class)

package com.example.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.Range
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.HighSpeedVideoSessionConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ai.image.enhancement.CpgaLowLightEngine
import com.example.core.permission.PermissionManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private enum class CameraMode(val label: String) {
    PHOTO("PHOTO"), PORTRAIT("PORTRAIT"), NIGHT("NIGHT"), HDR("HDR"), VIDEO("VIDEO"), SLOW_MO("SLOW-MO")
}

@Composable
fun CameraProScreen2(onMediaCaptured: (String) -> Unit) {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManagerImpl(context) }
    val scope = rememberCoroutineScope()
    val permissions = remember {
        mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.toTypedArray()
    }
    var granted by remember { mutableStateOf(permissionManager.hasPermission(Manifest.permission.CAMERA)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = permissionManager.hasPermission(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(permissions) }

    if (granted) {
        CameraProContent(scope, onMediaCaptured)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.permission_camera_mic))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(permissions) }) { Text(stringResource(R.string.grant_permissions)) }
            }
        }
    }
}

@Composable
private fun CameraProContent(scope: CoroutineScope, onMediaCaptured: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analysis = remember { ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build() }
    var faceTarget by remember { mutableStateOf<FaceTarget?>(null) }
    val faceAnalyzer = remember { FaceFocusAnalyzer { faceTarget = it } }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var recorder by remember { mutableStateOf<Recorder?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(CameraMode.PHOTO) }
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var canSwitch by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var torch by remember { mutableStateOf(false) }
    var faceFocus by remember { mutableStateOf(true) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var exposure by remember { mutableIntStateOf(0) }
    var exposureRange by remember { mutableStateOf<Range<Int>?>(null) }
    var grid by remember { mutableStateOf(false) }
    var timer by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var blurStrength by remember { mutableFloatStateOf(0.7f) }
    var slowFps by remember { mutableIntStateOf(120) }
    var supportedSlowFps by remember { mutableStateOf(emptyList<Int>()) }
    var showTools by remember { mutableStateOf(false) }
    var bokehAvailable by remember { mutableStateOf(false) }
    var nightAvailable by remember { mutableStateOf(false) }
    var hdrAvailable by remember { mutableStateOf(false) }
    var extensionsManager by remember { mutableStateOf<ExtensionsManager?>(null) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ provider = future.get() }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(provider) {
        val p = provider ?: return@LaunchedEffect
        try { extensionsManager = ExtensionsManager.getInstance(context, p) } catch (_: Exception) { extensionsManager = null }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { recording?.stop(); recording?.close() } catch (_: Exception) { }
            try { provider?.unbindAll() } catch (_: Exception) { }
            analysis.clearAnalyzer()
            faceAnalyzer.close()
            executor.shutdown()
        }
    }

    LaunchedEffect(provider, lens, mode, slowFps) {
        val p = provider ?: return@LaunchedEffect
        try {
            val selector = CameraSelector.Builder().requireLensFacing(lens).build()
            if (!p.hasCamera(selector)) return@LaunchedEffect
            val info = p.getCameraInfo(selector)
            canSwitch = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) && p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            hasFlash = info.hasFlashUnit()
            exposureRange = if (info.exposureState.isExposureCompensationSupported) info.exposureState.exposureCompensationRange else null
            bokehAvailable = extensionsManager?.isExtensionAvailable(selector, ExtensionMode.BOKEH) == true
            nightAvailable = extensionsManager?.isExtensionAvailable(selector, ExtensionMode.NIGHT) == true
            hdrAvailable = extensionsManager?.isExtensionAvailable(selector, ExtensionMode.HDR) == true
            p.unbindAll(); analysis.clearAnalyzer(); faceTarget = null
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

            when (mode) {
                CameraMode.SLOW_MO -> {
                    val caps = Recorder.getHighSpeedVideoCapabilities(info)
                    if (caps == null) { supportedSlowFps = emptyList(); return@LaunchedEffect }
                    val qualities = caps.getSupportedQualities(DynamicRange.SDR)
                    val quality = listOf(Quality.FHD, Quality.HD, Quality.SD).firstOrNull { it in qualities }
                        ?: run { supportedSlowFps = emptyList(); return@LaunchedEffect }
                    val highSpeedRecorder = Recorder.Builder().setQualitySelector(QualitySelector.from(quality)).build()
                    val highSpeedCapture = VideoCapture.withOutput(highSpeedRecorder)
                    val probe = HighSpeedVideoSessionConfig.Builder(highSpeedCapture).setPreview(preview).setSlowMotionEnabled(true).build()
                    val ranges = info.getSupportedFrameRateRanges(probe)
                    val options = ranges.flatMap { r -> listOf(120, 240).filter { r.contains(it) } }.distinct().sortedDescending()
                    supportedSlowFps = options
                    if (options.isEmpty()) return@LaunchedEffect
                    val selected = slowFps.takeIf { it in options } ?: options.first()
                    if (selected != slowFps) { slowFps = selected; return@LaunchedEffect }
                    val exactRange = ranges.firstOrNull { it.lower == selected && it.upper == selected } ?: ranges.first { it.contains(selected) }
                    val config = HighSpeedVideoSessionConfig.Builder(highSpeedCapture).setPreview(preview).setFrameRateRange(exactRange).setSlowMotionEnabled(true).build()
                    recorder = highSpeedRecorder
                    camera = p.bindToLifecycle(lifecycleOwner, selector, config)
                }
                CameraMode.VIDEO -> {
                    val supported = QualitySelector.getSupportedQualities(info)
                    val preferred = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD).filter { it in supported }
                    val qualitySelector = QualitySelector.fromOrderedList(preferred.ifEmpty { listOf(Quality.FHD, Quality.HD, Quality.SD) }, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                    val r = Recorder.Builder().setQualitySelector(qualitySelector).build()
                    val vc = VideoCapture.withOutput(r)
                    recorder = r
                    analysis.setAnalyzer(executor, faceAnalyzer)
                    camera = try { p.bindToLifecycle(lifecycleOwner, selector, preview, vc, analysis) }
                    catch (_: Exception) { analysis.clearAnalyzer(); p.bindToLifecycle(lifecycleOwner, selector, preview, vc) }
                }
                else -> {
                    val c = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(95).build()
                    c.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    capture = c
                    recorder = null
                    val extensionMode = when (mode) {
                        CameraMode.PORTRAIT -> if (bokehAvailable) ExtensionMode.BOKEH else ExtensionMode.NONE
                        CameraMode.NIGHT -> if (nightAvailable) ExtensionMode.NIGHT else ExtensionMode.NONE
                        CameraMode.HDR -> if (hdrAvailable) ExtensionMode.HDR else ExtensionMode.NONE
                        else -> ExtensionMode.NONE
                    }
                    val extensionSelector = if (extensionMode != ExtensionMode.NONE) extensionsManager?.getExtensionEnabledCameraSelector(selector, extensionMode) ?: selector else selector
                    if (extensionMode == ExtensionMode.NONE) analysis.setAnalyzer(executor, faceAnalyzer)
                    camera = try {
                        if (extensionMode == ExtensionMode.NONE) p.bindToLifecycle(lifecycleOwner, extensionSelector, preview, c, analysis)
                        else p.bindToLifecycle(lifecycleOwner, extensionSelector, preview, c)
                    } catch (_: Exception) {
                        analysis.clearAnalyzer(); p.bindToLifecycle(lifecycleOwner, selector, preview, c)
                    }
                    c.flashMode = flashMode
                }
            }
            camera?.cameraInfo?.zoomState?.value?.let { z -> zoom = z.zoomRatio; minZoom = z.minZoomRatio; maxZoom = z.maxZoomRatio }
            exposureRange?.let { exposure = exposure.coerceIn(it.lower, it.upper) }
        } catch (t: Throwable) {
            android.util.Log.e("CameraPro", "Camera binding failed", t)
        }
    }

    LaunchedEffect(faceTarget, faceFocus, camera, mode) {
        if (!faceFocus || mode == CameraMode.SLOW_MO) return@LaunchedEffect
        val target = faceTarget ?: return@LaunchedEffect
        val c = camera ?: return@LaunchedEffect
        if (previewView.width == 0 || previewView.height == 0) return@LaunchedEffect
        val x = target.normalizedX * previewView.width
        val y = target.normalizedY * previewView.height
        c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(previewView.meteringPointFactory.createPoint(x, y)).setAutoCancelDuration(2, TimeUnit.SECONDS).build())
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsed = 0
            paused = false
            while (isActive && isRecording) { delay(1000); if (!paused) elapsed++ }
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) { delay(1000); countdown--; if (countdown == 0) capturePhoto(capture, context, mode, blurStrength, scope, onMediaCaptured) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.fillMaxSize()
                .pointerInput(camera) { detectTapGestures { p ->
                    focusPoint = p
                    camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(previewView.meteringPointFactory.createPoint(p.x, p.y)).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
                } }
                .pointerInput(camera) { detectTransformGestures { _, _, factor, _ ->
                    val c = camera ?: return@detectTransformGestures
                    val z = c.cameraInfo.zoomState.value ?: return@detectTransformGestures
                    val next = (z.zoomRatio * factor).coerceIn(z.minZoomRatio, z.maxZoomRatio)
                    c.cameraControl.setZoomRatio(next); zoom = next
                } }
        ) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            if (grid) GridOverlay(Modifier.fillMaxSize())
            focusPoint?.let { p -> Box(Modifier.offset { IntOffset(p.x.toInt() - 24, p.y.toInt() - 24) }.size(48.dp).border(2.dp, Color.Yellow, CircleShape)) }
            if (faceTarget != null && faceFocus && mode != CameraMode.SLOW_MO) Box(Modifier.align(Alignment.Center).size(78.dp).border(2.dp, Color.Cyan, RoundedCornerShape(22.dp)))
        }

        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 36.dp, start = 10.dp, end = 10.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconButton(enabled = hasFlash, onClick = {
                if (mode == CameraMode.VIDEO || mode == CameraMode.SLOW_MO) { torch = !torch; camera?.cameraControl?.enableTorch(torch) }
                else { flashMode = when (flashMode) { ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON; ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO; else -> ImageCapture.FLASH_MODE_OFF }; capture?.flashMode = flashMode }
            }) { Icon(if (torch || flashMode == ImageCapture.FLASH_MODE_ON) Icons.Default.FlashOn else if (flashMode == ImageCapture.FLASH_MODE_AUTO) Icons.Default.FlashAuto else Icons.Default.FlashOff, null, tint = Color.White) }
            Text(if (mode == CameraMode.SLOW_MO) "${slowFps} FPS" else String.format(Locale.US, "%.1fx", zoom), color = Color.White, fontWeight = FontWeight.Bold)
            TextButton(onClick = { faceFocus = !faceFocus }) { Text("FACE", color = if (faceFocus) Color.Cyan else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold) }
            IconButton(enabled = canSwitch && !isRecording, onClick = { lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }) { Icon(Icons.Default.Cameraswitch, null, tint = Color.White) }
        }

        if (isRecording) Surface(Modifier.align(Alignment.TopCenter).padding(top = 84.dp), color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(16.dp)) { Text(String.format(Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) }

        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 128.dp), Arrangement.SpaceEvenly) {
            CameraMode.values().forEach { m ->
                if (m != CameraMode.HDR || hdrAvailable) TextButton(enabled = !isRecording, onClick = { mode = m }) { Text(m.label, color = if (mode == m) Color.Yellow else Color.White.copy(alpha = 0.65f), fontWeight = if (mode == m) FontWeight.Bold else FontWeight.Normal) }
            }
        }

        if (showTools) Surface(Modifier.fillMaxWidth().padding(12.dp).align(Alignment.BottomCenter).padding(bottom = 176.dp), color = Color.Black.copy(alpha = 0.88f), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Timer", color = Color.White, modifier = Modifier.weight(1f)); listOf(0,3,5,10).forEach { s -> TextButton(onClick = { timer = s }) { Text(if (s == 0) "OFF" else "${s}s", color = if (timer == s) Color.Yellow else Color.White) } } }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Grid", color = Color.White, modifier = Modifier.weight(1f)); TextButton(onClick = { grid = !grid }) { Text(if (grid) "ON" else "OFF", color = if (grid) Color.Yellow else Color.White) } }
                exposureRange?.let { r -> Row(verticalAlignment = Alignment.CenterVertically) { Text("EV", color = Color.White, modifier = Modifier.weight(1f)); TextButton(onClick = { exposure = (exposure - 1).coerceIn(r.lower,r.upper); camera?.cameraControl?.setExposureCompensationIndex(exposure) }) { Text("−", color = Color.White) }; Text(exposure.toString(), color = Color.White); TextButton(onClick = { exposure = (exposure + 1).coerceIn(r.lower,r.upper); camera?.cameraControl?.setExposureCompensationIndex(exposure) }) { Text("+", color = Color.White) } } }
                if (mode == CameraMode.PORTRAIT && !bokehAvailable) Row(verticalAlignment = Alignment.CenterVertically) { Text("Blur", color = Color.White, modifier = Modifier.weight(1f)); TextButton(onClick = { blurStrength = (blurStrength - .1f).coerceAtLeast(0f) }) { Text("−", color = Color.White) }; Text("${(blurStrength*100).toInt()}%", color = Color.White); TextButton(onClick = { blurStrength = (blurStrength + .1f).coerceAtMost(1f) }) { Text("+", color = Color.White) } }
                if (mode == CameraMode.SLOW_MO) Row(verticalAlignment = Alignment.CenterVertically) { Text("Slow", color = Color.White, modifier = Modifier.weight(1f)); supportedSlowFps.forEach { f -> TextButton(onClick = { slowFps = f }) { Text("${f}", color = if (slowFps == f) Color.Yellow else Color.White) } } }
                Row { listOf(1f,2f,3f).filter { it <= maxZoom + .01f }.forEach { z -> TextButton(onClick = { val v=z.coerceIn(minZoom,maxZoom); camera?.cameraControl?.setZoomRatio(v); zoom=v }) { Text("${z.toInt()}x", color = Color.White) } } }
            }
        }

        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            IconButton(onClick = { showTools = !showTools }) { Icon(Icons.Default.Settings, null, tint = Color.White) }
            Box(Modifier.size(78.dp).border(4.dp, Color.White, CircleShape).padding(8.dp).background(if (isRecording) Color.Red else Color.White, if (isRecording) RoundedCornerShape(12.dp) else CircleShape).clickable(enabled = countdown == 0) {
                if (mode == CameraMode.VIDEO || mode == CameraMode.SLOW_MO) {
                    if (recording == null) startRecording(context, recorder, { recording = it; isRecording = true }, { uri -> recording = null; isRecording = false; paused = false; uri?.let(onMediaCaptured) }) else recording?.stop()
                } else {
                    if (timer > 0) countdown = timer else scope.launch { capturePhoto(capture, context, mode, blurStrength, scope, onMediaCaptured) }
                }
            })
            if (isRecording) IconButton(onClick = { recording?.let { if (paused) { it.resume(); paused=false } else { it.pause(); paused=true } } }) { Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = Color.White) }
        }
    }
}

private fun startRecording(context: Context, recorder: Recorder?, onStart: (Recording) -> Unit, onFinish: (String?) -> Unit) {
    val r = recorder ?: return
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MediaAIStudio")
    }
    var pending = r.prepareRecording(context, MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build())
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
    val active = pending.start(ContextCompat.getMainExecutor(context)) { event -> if (event is VideoRecordEvent.Finalize) onFinish(if (event.hasError()) null else event.outputResults.outputUri.toString()) }
    onStart(active)
}

private suspend fun capturePhoto(capture: ImageCapture?, context: Context, mode: CameraMode, blur: Float, scope: CoroutineScope, onMediaCaptured: (String) -> Unit) {
    val c = capture ?: return
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MediaAIStudio")
    }
    val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
    c.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onError(exception: ImageCaptureException) { android.util.Log.e("CameraPro", "Photo capture failed", exception) }
        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
            val uri = result.savedUri ?: return
            scope.launch(Dispatchers.Default) {
                val finalUri = when (mode) {
                    CameraMode.PORTRAIT -> if (blur > 0f) try { PortraitBlurProcessor.process(context, uri.toString(), blur) } catch (_: Exception) { uri.toString() } else uri.toString()
                    CameraMode.NIGHT -> try {
                        when (val ai = CpgaLowLightEngine(context.applicationContext).process(uri.toString()) { }) {
                            is com.example.ai.core.AIResult.Success -> ai.outputUri
                            else -> uri.toString()
                        }
                    } catch (_: Exception) { uri.toString() }
                    else -> uri.toString()
                }
                onMediaCaptured(finalUri)
            }
        }
    })
}

@Composable
private fun GridOverlay(modifier: Modifier) {
    Box(modifier) {
        Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.Center).background(Color.White.copy(alpha = 0.18f)))
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.16f)))
    }
}
