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
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
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
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    var granted by remember { mutableStateOf(permissionManager.hasPermission(Manifest.permission.CAMERA)) }
    val permissions = remember {
        mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = permissionManager.hasPermission(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(permissions) }

    if (granted) {
        CameraProContent2(onMediaCaptured, scope)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.permission_camera_mic))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { launcher.launch(permissions) }) { Text(stringResource(R.string.grant_permissions)) }
            }
        }
    }
}

@Composable
private fun CameraProContent2(onMediaCaptured: (String) -> Unit, scope: kotlinx.coroutines.CoroutineScope) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    var mode by remember { mutableStateOf(CameraMode.PHOTO) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recorder by remember { mutableStateOf<Recorder?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var faceTarget by remember { mutableStateOf<FaceTarget?>(null) }
    var faceFocus by remember { mutableStateOf(true) }
    var lastFacePoint by remember { mutableStateOf<Offset?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var exposure by remember { mutableIntStateOf(0) }
    var exposureRange by remember { mutableStateOf<Range<Int>?>(null) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var torch by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(false) }
    var timer by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var blurAmount by remember { mutableFloatStateOf(0.7f) }
    var slowFps by remember { mutableIntStateOf(120) }
    var supportedSlowFps by remember { mutableStateOf(emptyList<Int>()) }
    var showTools by remember { mutableStateOf(false) }
    var bokehSupported by remember { mutableStateOf(false) }
    var nightSupported by remember { mutableStateOf(false) }
    var hdrSupported by remember { mutableStateOf(false) }
    var extensions by remember { mutableStateOf<androidx.camera.extensions.ExtensionsManager?>(null) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ provider = future.get() }, ContextCompat.getMainExecutor(context))
    }
    LaunchedEffect(provider) {
        provider?.let { p ->
            try { extensions = androidx.camera.extensions.ExtensionsManager.getInstance(context, p) } catch (_: Exception) {}
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try { recording?.stop(); recording?.close() } catch (_: Exception) {}
            try { provider?.unbindAll() } catch (_: Exception) {}
            executor.shutdown()
        }
    }

    LaunchedEffect(provider, lensFacing, mode, slowFps) {
        val p = provider ?: return@LaunchedEffect
        try {
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val info = p.getCameraInfo(selector)
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            bokehSupported = extensions?.isExtensionAvailable(selector, androidx.camera.extensions.ExtensionMode.BOKEH) == true
            nightSupported = extensions?.isExtensionAvailable(selector, androidx.camera.extensions.ExtensionMode.NIGHT) == true
            hdrSupported = extensions?.isExtensionAvailable(selector, androidx.camera.extensions.ExtensionMode.HDR) == true
            hasFlash = info.hasFlashUnit()
            exposureRange = if (info.exposureState.isExposureCompensationSupported) info.exposureState.exposureCompensationRange else null
            val hasBack = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val hasFront = p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            p.unbindAll()

            if (mode == CameraMode.SLOW_MO) {
                val caps = Recorder.getHighSpeedVideoCapabilities(info)
                if (caps == null) { supportedSlowFps = emptyList(); return@LaunchedEffect }
                val hsQualities = caps.getSupportedQualities(androidx.camera.core.DynamicRange.SDR)
                val quality = listOf(Quality.FHD, Quality.HD, Quality.SD).firstOrNull { it in hsQualities }
                    ?: return@LaunchedEffect
                val probe = VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(quality)).build())
                val probeConfig = HighSpeedVideoSessionConfig.Builder(probe).setPreview(preview).setSlowMotionEnabled(true).build()
                val ranges = info.getSupportedFrameRateRanges(probeConfig)
                val fpsOptions = ranges.flatMap { r -> listOf(120, 240).filter { r.contains(it) } }.distinct().sortedDescending()
                supportedSlowFps = fpsOptions
                if (fpsOptions.isEmpty()) return@LaunchedEffect
                val fps = if (slowFps in fpsOptions) slowFps else fpsOptions.first()
                if (fps != slowFps) { slowFps = fps; return@LaunchedEffect }
                val hsRecorder = Recorder.Builder().setQualitySelector(QualitySelector.from(quality)).build()
                val hsCapture = VideoCapture.withOutput(hsRecorder)
                recorder = hsRecorder
                videoCapture = hsCapture
                val session = HighSpeedVideoSessionConfig.Builder(hsCapture)
                    .setPreview(preview)
                    .setFrameRateRange(Range(fps, fps))
                    .setSlowMotionEnabled(true)
                    .build()
                camera = p.bindToLifecycle(lifecycleOwner, selector, session)
            } else if (mode == CameraMode.VIDEO) {
                val qualities = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD).filter { QualitySelector.getSupportedQualities(info).contains(it) }
                val sel = QualitySelector.fromOrderedList(qualities.ifEmpty { listOf(Quality.FHD, Quality.HD, Quality.SD) }, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                val rec = Recorder.Builder().setQualitySelector(sel).build()
                val vc = VideoCapture.withOutput(rec)
                recorder = rec
                videoCapture = vc
                val bound = p.bindToLifecycle(lifecycleOwner, selector, preview, vc)
                camera = bound
            } else {
                val cap = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(95).build()
                imageCapture = cap
                val extMode = when (mode) {
                    CameraMode.PORTRAIT -> if (bokehSupported) androidx.camera.extensions.ExtensionMode.BOKEH else androidx.camera.extensions.ExtensionMode.NONE
                    CameraMode.NIGHT -> if (nightSupported) androidx.camera.extensions.ExtensionMode.NIGHT else androidx.camera.extensions.ExtensionMode.NONE
                    CameraMode.HDR -> if (hdrSupported) androidx.camera.extensions.ExtensionMode.HDR else androidx.camera.extensions.ExtensionMode.NONE
                    else -> androidx.camera.extensions.ExtensionMode.NONE
                }
                val extSelector = if (extMode != androidx.camera.extensions.ExtensionMode.NONE) {
                    extensions?.getExtensionEnabledCameraSelector(selector, extMode) ?: selector
                } else selector
                val bound = p.bindToLifecycle(lifecycleOwner, extSelector, preview, cap)
                camera = bound
                cap.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                cap.flashMode = flashMode
                faceTarget = null
            }
            val cam = camera ?: return@LaunchedEffect
            cam.cameraInfo.zoomState.value?.let { z -> zoom = z.zoomRatio; minZoom = z.minZoomRatio; maxZoom = z.maxZoomRatio }
            exposureRange?.let { exposure = exposure.coerceIn(it.lower, it.upper) }
        } catch (e: Exception) {
            android.util.Log.e("CameraPro", "bind failed", e)
        }
    }

    // Face detection + autofocus. Disabled automatically for extension and slow-motion sessions.
    LaunchedEffect(mode, camera, faceFocus) {
        if (mode == CameraMode.NIGHT || mode == CameraMode.HDR || mode == CameraMode.PORTRAIT && bokehSupported || mode == CameraMode.SLOW_MO) return@LaunchedEffect
    }

    LaunchedEffect(faceTarget, faceFocus, camera) {
        if (!faceFocus) { lastFacePoint = null; return@LaunchedEffect }
        val target = faceTarget ?: return@LaunchedEffect
        val cam = camera ?: return@LaunchedEffect
        if (previewView.width <= 0 || previewView.height <= 0) return@LaunchedEffect
        val pt = Offset(target.normalizedX * previewView.width, target.normalizedY * previewView.height)
        if (lastFacePoint == null || (pt - lastFacePoint!!).getDistance() > 45f) {
            lastFacePoint = pt
            val meter = previewView.meteringPointFactory.createPoint(pt.x, pt.y)
            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(meter).setAutoCancelDuration(2, TimeUnit.SECONDS).build())
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsed = 0
            paused = false
            while (isActive && isRecording) {
                delay(1000)
                if (isRecording && !paused) elapsed++
            }
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
            if (countdown == 0) scope.launch { capturePhoto(imageCapture, context, mode, blurAmount, onMediaCaptured) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.fillMaxSize()
                .pointerInput(camera) { detectTapGestures { off ->
                    focusPoint = off
                    val cam = camera ?: return@detectTapGestures
                    val meter = previewView.meteringPointFactory.createPoint(off.x, off.y)
                    cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(meter).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
                } }
                .pointerInput(camera) { detectTransformGestures { _, _, gestureZoom, _ ->
                    val cam = camera ?: return@detectTransformGestures
                    val z = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
                    val next = (z.zoomRatio * gestureZoom).coerceIn(z.minZoomRatio, z.maxZoomRatio)
                    cam.cameraControl.setZoomRatio(next)
                    zoom = next
                } }
        ) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            if (grid) GridLines(Modifier.fillMaxSize())
            focusPoint?.let { off ->
                Box(Modifier.offset { IntOffset(off.x.toInt() - 24, off.y.toInt() - 24) }.size(48.dp).border(2.dp, Color.Yellow, CircleShape))
            }
            faceTarget?.let { Box(Modifier.align(Alignment.Center).size(78.dp).border(2.dp, Color.Cyan, RoundedCornerShape(22.dp))) }
        }

        Row(Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp).align(Alignment.TopCenter), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = {
                if (mode == CameraMode.VIDEO || mode == CameraMode.SLOW_MO) { torch = !torch; camera?.cameraControl?.enableTorch(torch) }
                else { flashMode = when (flashMode) { ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON; ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO; else -> ImageCapture.FLASH_MODE_OFF }; imageCapture?.flashMode = flashMode }
            }, enabled = hasFlash) {
                Icon(when { torch -> Icons.Default.FlashOn; flashMode == ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn; flashMode == ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto; else -> Icons.Default.FlashOff }, null, tint = if (hasFlash && (torch || flashMode != ImageCapture.FLASH_MODE_OFF)) Color.Yellow else Color.White)
            }
            IconButton(onClick = { grid = !grid }) { Icon(Icons.Default.GridOn, null, tint = if (grid) Color.Yellow else Color.White) }
            Text("${String.format(Locale.US, "%.1f", zoom)}x", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            IconButton(onClick = { faceFocus = !faceFocus }) { Text("FACE", color = if (faceFocus) Color.Cyan else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold) }
            IconButton(onClick = { if (!isRecording) lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }) { Icon(Icons.Default.Cameraswitch, null, tint = Color.White) }
        }

        if (isRecording) Surface(Modifier.align(Alignment.TopCenter).padding(top = 92.dp), color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp)) {
            Text(String.format(Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).align(Alignment.BottomCenter).padding(bottom = 130.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            CameraMode.values().forEach { m ->
                val enabled = when (m) { CameraMode.HDR -> hdrSupported; CameraMode.NIGHT -> true; CameraMode.SLOW_MO -> supportedSlowFps.isNotEmpty(); else -> true }
                if (enabled) TextButton(onClick = { if (!isRecording) mode = m }) { Text(m.label, color = if (mode == m) Color.Yellow else Color.White.copy(alpha = 0.65f), fontWeight = if (mode == m) FontWeight.Bold else FontWeight.Normal) }
            }
        }

        if (showTools) {
            Surface(Modifier.fillMaxWidth().padding(12.dp).align(Alignment.BottomCenter).padding(bottom = 210.dp), color = Color.Black.copy(alpha = 0.84f), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Timer", color = Color.White, modifier = Modifier.weight(1f))
                        listOf(0, 3, 5, 10).forEach { v -> TextButton(onClick = { timer = v }) { Text(if (v == 0) "OFF" else "${v}s", color = if (timer == v) Color.Yellow else Color.White) } }
                    }
                    if (mode == CameraMode.PORTRAIT && !bokehSupported) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Blur", color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = { blurAmount = (blurAmount - 0.1f).coerceAtLeast(0f) }) { Text("−", color = Color.White) }
                            Text("${(blurAmount * 100).toInt()}%", color = Color.White)
                            TextButton(onClick = { blurAmount = (blurAmount + 0.1f).coerceAtMost(1f) }) { Text("+", color = Color.White) }
                        }
                    }
                    exposureRange?.let { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("EV", color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = { exposure = (exposure - 1).coerceIn(r.lower, r.upper); camera?.cameraControl?.setExposureCompensationIndex(exposure) }) { Text("−", color = Color.White) }
                            Text(exposure.toString(), color = Color.White)
                            TextButton(onClick = { exposure = (exposure + 1).coerceIn(r.lower, r.upper); camera?.cameraControl?.setExposureCompensationIndex(exposure) }) { Text("+", color = Color.White) }
                        }
                    }
                    if (mode == CameraMode.SLOW_MO) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("FPS", color = Color.White, modifier = Modifier.weight(1f))
                            supportedSlowFps.forEach { fps -> TextButton(onClick = { slowFps = fps }) { Text(fps.toString(), color = if (slowFps == fps) Color.Yellow else Color.White) } }
                        }
                    }
                    Row {
                        listOf(1f, 2f, 3f).filter { it <= maxZoom }.forEach { z -> TextButton(onClick = { camera?.cameraControl?.setZoomRatio(z.coerceAtLeast(minZoom)); zoom = z.coerceAtLeast(minZoom) }) { Text("${z.toInt()}x", color = Color.White) } }
                    }
                }
            }
        }

        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            IconButton(onClick = { showTools = !showTools }) { Icon(Icons.Default.Settings, null, tint = Color.White) }
            Box(Modifier.size(78.dp).border(4.dp, Color.White, CircleShape).padding(8.dp).background(if (isRecording) Color.Red else Color.White, if (isRecording) RoundedCornerShape(12.dp) else CircleShape).clickable {
                if (mode == CameraMode.VIDEO || mode == CameraMode.SLOW_MO) {
                    val r = recording
                    if (r == null) {
                        startRecording(context, recorder, mode, { active -> recording = active; isRecording = true }, { uri -> recording = null; isRecording = false; paused = false; uri?.let(onMediaCaptured) })
                    } else r.stop()
                } else {
                    if (timer > 0) countdown = timer else scope.launch { capturePhoto(imageCapture, context, mode, blurAmount, onMediaCaptured) }
                }
            })
            if (isRecording) IconButton(onClick = { recording?.let { if (paused) { it.resume(); paused = false } else { it.pause(); paused = true } } }) { Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = Color.White) }
        }
    }
}

private fun startRecording(
    context: Context,
    recorder: Recorder?,
    mode: CameraMode,
    onStart: (Recording) -> Unit,
    onFinalize: (String?) -> Unit
) {
    val r = recorder ?: return
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MediaAIStudio")
    }
    var pending = r.prepareRecording(context, MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build())
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
    onStart(pending.start(ContextCompat.getMainExecutor(context)) { event ->
        if (event is VideoRecordEvent.Finalize) onFinalize(if (event.hasError()) null else event.outputResults.outputUri.toString())
    })
}

private suspend fun capturePhoto(
    capture: ImageCapture?,
    context: Context,
    mode: CameraMode,
    blur: Float,
    onMediaCaptured: (String) -> Unit
) {
    val c = capture ?: return
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MediaAIStudio")
    }
    val output = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
    c.takePicture(output, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onError(exception: ImageCaptureException) = Unit
        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
            val uri = result.savedUri ?: return
            // Post-process portrait background and night enhancement off the UI thread.
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                val finalUri = when (mode) {
                    CameraMode.PORTRAIT -> try { PortraitBlurProcessor.process(context, uri.toString(), blur) } catch (_: Exception) { uri.toString() }
                    CameraMode.NIGHT -> try {
                        when (val resultAi = CpgaLowLightEngine(context.applicationContext).process(uri.toString()) { }) {
                            is com.example.ai.core.AIResult.Success -> resultAi.outputUri
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
private fun GridLines(modifier: Modifier) {
    Box(modifier) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Box(Modifier.fillMaxSize().weight(1f).border(0.dp, Color.Transparent))
            Box(Modifier.fillMaxSize().weight(1f).border(1.dp, Color.White.copy(alpha = 0.18f)))
            Box(Modifier.fillMaxSize().weight(1f).border(0.dp, Color.Transparent))
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            Box(Modifier.fillMaxWidth().height(1.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.18f)))
            Box(Modifier.fillMaxWidth().height(1.dp))
        }
    }
}
