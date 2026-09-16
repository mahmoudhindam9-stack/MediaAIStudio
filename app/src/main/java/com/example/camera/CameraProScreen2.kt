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
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.DynamicRange
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
    var cameraGranted by remember { mutableStateOf(permissionManager.hasPermission(Manifest.permission.CAMERA)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cameraGranted = permissionManager.hasPermission(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) { if (!cameraGranted) permissionLauncher.launch(permissions) }

    if (!cameraGranted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.permission_camera_mic))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(permissions) }) { Text(stringResource(R.string.grant_permissions)) }
            }
        }
    } else {
        CameraProContent(scope, onMediaCaptured)
    }
}

@Composable
private fun CameraProContent(scope: CoroutineScope, onMediaCaptured: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    var faceTarget by remember { mutableStateOf<FaceTarget?>(null) }
    val faceAnalyzer = remember { FaceFocusAnalyzer { faceTarget = it } }

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recorder by remember { mutableStateOf<Recorder?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    var mode by remember { mutableStateOf(CameraMode.PHOTO) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var canSwitch by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var torchOn by remember { mutableStateOf(false) }
    var faceFocusEnabled by remember { mutableStateOf(true) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var exposureRange by remember { mutableStateOf<Range<Int>?>(null) }
    var gridEnabled by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var portraitBlur by remember { mutableFloatStateOf(0.7f) }
    var slowMotionFps by remember { mutableIntStateOf(120) }
    var supportedSlowFps by remember { mutableStateOf(emptyList<Int>()) }
    var showTools by remember { mutableStateOf(false) }
    var bokehAvailable by remember { mutableStateOf(false) }
    var nightAvailable by remember { mutableStateOf(false) }
    var hdrAvailable by remember { mutableStateOf(false) }
    var extensionsManager by remember { mutableStateOf<androidx.camera.extensions.ExtensionsManager?>(null) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ provider = future.get() }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(provider) {
        val p = provider ?: return@LaunchedEffect
        try {
            extensionsManager = androidx.camera.extensions.ExtensionsManager.getInstance(context, p)
        } catch (_: Exception) {
            extensionsManager = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { recording?.stop(); recording?.close() } catch (_: Exception) { }
            try { provider?.unbindAll() } catch (_: Exception) { }
            analysis.clearAnalyzer()
            faceAnalyzer.close()
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(provider, lensFacing, mode, slowMotionFps) {
        val p = provider ?: return@LaunchedEffect
        try {
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val hasBack = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val hasFront = p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            canSwitch = hasBack && hasFront
            val info = p.getCameraInfo(selector)
            hasFlash = info.hasFlashUnit()
            exposureRange = if (info.exposureState.isExposureCompensationSupported) info.exposureState.exposureCompensationRange else null

            bokehAvailable = extensionsManager?.isExtensionAvailable(selector, androidx.camera.extensions.ExtensionMode.BOKEH) == true
            nightAvailable = extensionsManager?.isExtensionAvailable(selector, androidx.camera.extensions.ExtensionMode.NIGHT) == true
            hdrAvailable = extensionsManager?.isExtensionAvailable(selector, androidx.camera.extensions.ExtensionMode.HDR) == true

            p.unbindAll()
            analysis.clearAnalyzer()
            faceTarget = null

            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

            when (mode) {
                CameraMode.SLOW_MO -> {
                    val caps = Recorder.getHighSpeedVideoCapabilities(info)
                    if (caps == null) { supportedSlowFps = emptyList(); return@LaunchedEffect }
                    val qualities = caps.getSupportedQualities(DynamicRange.SDR)
                    val quality = listOf(Quality.FHD, Quality.HD, Quality.SD).firstOrNull { it in qualities }
                        ?: run { supportedSlowFps = emptyList(); return@LaunchedEffect }
                    val hsRecorder = Recorder.Builder().setQualitySelector(QualitySelector.from(quality)).build()
                    val hsCapture = VideoCapture.withOutput(hsRecorder)
                    val builder = HighSpeedVideoSessionConfig.Builder(hsCapture)
                        .setPreview(preview)
                        .setSlowMotionEnabled(true)
                    val ranges = info.getSupportedFrameRateRanges(builder.build())
                    val options = ranges.flatMap { r -> listOf(120, 240).filter { r.contains(it) } }.distinct().sortedDescending()
                    supportedSlowFps = options
                    if (options.isEmpty()) return@LaunchedEffect
                    val selected = if (slowMotionFps in options) slowMotionFps else options.first()
                    if (selected != slowMotionFps) { slowMotionFps = selected; return@LaunchedEffect }
                    val exact = ranges.firstOrNull { it.lower == selected && it.upper == selected }
                    builder.setFrameRateRange(exact ?: ranges.first { it.contains(selected) })
                    recorder = hsRecorder
                    videoCapture = hsCapture
                    camera = p.bindToLifecycle(lifecycleOwner, selector, builder.build())
                }
                CameraMode.VIDEO -> {
                    val supported = QualitySelector.getSupportedQualities(info)
                    val preferred = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD).filter { it in supported }
                    val q = QualitySelector.fromOrderedList(preferred.ifEmpty { listOf(Quality.FHD, Quality.HD, Quality.SD) }, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                    val rec = Recorder.Builder().setQualitySelector(q).build()
                    val vc = VideoCapture.withOutput(rec)
                    recorder = rec
                    videoCapture = vc
                    analysis.setAnalyzer(cameraExecutor, faceAnalyzer)
                    camera = try {
                        p.bindToLifecycle(lifecycleOwner, selector, preview, vc, analysis)
                    } catch (_: Exception) {
                        analysis.clearAnalyzer()
                        p.bindToLifecycle(lifecycleOwner, selector, preview, vc)
                    }
                }
                else -> {
                    val cap = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(95)
                        .build()
                    cap.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    capture = cap
                    videoCapture = null
                    recorder = null

                    val extensionMode = when (mode) {
                        CameraMode.PORTRAIT -> if (bokehAvailable) androidx.camera.extensions.ExtensionMode.BOKEH else androidx.camera.extensions.ExtensionMode.NONE
                        CameraMode.NIGHT -> if (nightAvailable) androidx.camera.extensions.ExtensionMode.NIGHT else androidx.camera.extensions.ExtensionMode.NONE
                        CameraMode.HDR -> if (hdrAvailable) androidx.camera.extensions.ExtensionMode.HDR else androidx.camera.extensions.ExtensionMode.NONE
                        else -> androidx.camera.extensions.ExtensionMode.NONE
                    }
                    val extSelector = if (extensionMode != androidx.camera.extensions.ExtensionMode.NONE) {
                        extensionsManager?.getExtensionEnabledCameraSelector(selector, extensionMode) ?: selector
                    } else selector

                    val useAnalysis = extensionMode == androidx.camera.extensions.ExtensionMode.NONE
                    if (useAnalysis) {
                        analysis.setAnalyzer(cameraExecutor, faceAnalyzer)
                        camera = try {
                            p.bindToLifecycle(lifecycleOwner, extSelector, preview, cap, analysis)
                        } catch (_: Exception) {
                            analysis.clearAnalyzer()
                            p.bindToLifecycle(lifecycleOwner, extSelector, preview, cap)
                        }
                    } else {
                        camera = p.bindToLifecycle(lifecycleOwner, extSelector, preview, cap)
                    }
                    camera?.let { bound ->
                        cap.flashMode = flashMode
                        bound.cameraInfo.zoomState.value?.let { z ->
                            zoomRatio = z.zoomRatio
                            minZoom = z.minZoomRatio
                            maxZoom = z.maxZoomRatio
                        }
                    }
                }
            }

            camera?.let { bound ->
                bound.cameraInfo.zoomState.value?.let { z ->
                    zoomRatio = z.zoomRatio
                    minZoom = z.minZoomRatio
                    maxZoom = z.maxZoomRatio
                }
                exposureRange?.let { r -> exposureIndex = exposureIndex.coerceIn(r.lower, r.upper) }
            }
        } catch (t: Throwable) {
            android.util.Log.e("CameraPro", "Camera binding failed", t)
        }
    }

    LaunchedEffect(faceTarget, faceFocusEnabled, camera, mode) {
        if (!faceFocusEnabled || mode == CameraMode.SLOW_MO) return@LaunchedEffect
        val target = faceTarget ?: return@LaunchedEffect
        val bound = camera ?: return@LaunchedEffect
        if (previewView.width <= 0 || previewView.height <= 0) return@LaunchedEffect
        val point = Offset(target.normalizedX * previewView.width, target.normalizedY * previewView.height)
        val meter = previewView.meteringPointFactory.createPoint(point.x, point.y)
        bound.cameraControl.startFocusAndMetering(
            FocusMeteringAction.Builder(meter).setAutoCancelDuration(2, TimeUnit.SECONDS).build()
        )
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedSeconds = 0
            isPaused = false
            while (isActive && isRecording) {
                delay(1000)
                if (isRecording && !isPaused) elapsedSeconds++
            }
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
            if (countdown == 0) capturePhotoNow(capture, context, mode, portraitBlur, scope, onMediaCaptured)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { point ->
                        focusPoint = point
                        camera?.cameraControl?.startFocusAndMetering(
                            FocusMeteringAction.Builder(previewView.meteringPointFactory.createPoint(point.x, point.y))
                                .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                        )
                    }
                }
                .pointerInput(camera) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        val bound = camera ?: return@detectTransformGestures
                        val z = bound.cameraInfo.zoomState.value ?: return@detectTransformGestures
                        val next = (z.zoomRatio * gestureZoom).coerceIn(z.minZoomRatio, z.maxZoomRatio)
                        bound.cameraControl.setZoomRatio(next)
                        zoomRatio = next
                    }
                }
        ) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            if (gridEnabled) GridOverlay(Modifier.fillMaxSize())
            focusPoint?.let { p ->
                Box(Modifier.offset { IntOffset(p.x.toInt() - 24, p.y.toInt() - 24) }.size(48.dp).border(2.dp, Color.Yellow, CircleShape))
            }
            if (faceTarget != null && faceFocusEnabled && mode != CameraMode.SLOW_MO) {
                Box(Modifier.align(Alignment.Center).size(82.dp).border(2.dp, Color.Cyan, RoundedCornerShape(24.dp)))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 38.dp, start = 12.dp, end = 12.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (mode == CameraMode.VIDEO || mode == CameraMode.SLOW_MO) {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                } else {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    capture?.flashMode = flashMode
                }
            }, enabled = hasFlash) {
                Icon(
                    when {
                        torchOn -> Icons.Default.FlashOn
                        flashMode == ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        flashMode == ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    null,
                    tint = if (torchOn || flashMode != ImageCapture.FLASH_MODE_OFF) Color.Yellow else Color.White
                )
            }
            Text(if (mode == CameraMode.SLOW_MO) "${slowMotionFps}fps" else "${String.format(Locale.US, "%.1f", zoomRatio)}x", color = Color.White, fontWeight = FontWeight.Bold)
            IconButton(onClick = { faceFocusEnabled = !faceFocusEnabled }) {
                Text("FACE", color = if (faceFocusEnabled) Color.Cyan else Color.White.copy(alpha = 0.45f), fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { if (!isRecording && canSwitch) lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }, enabled = canSwitch && !isRecording) {
                Icon(Icons.Default.Cameraswitch, null, tint = Color.White)
            }
        }

        if (isRecording) {
            Surface(Modifier.align(Alignment.TopCenter).padding(top = 88.dp), color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(16.dp)) {
                Text(String.format(Locale.US, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }

        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 132.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            CameraMode.values().forEach { m ->
                val available = m != CameraMode.HDR || hdrAvailable
                if (available) TextButton(onClick = { if (!isRecording) mode = m }) {
                    Text(m.label, color = if (mode == m) Color.Yellow else Color.White.copy(alpha = 0.65f), fontWeight = if (mode == m) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        if (showTools) {
            Surface(Modifier.fillMaxWidth().padding(12.dp).align(Alignment.BottomCenter).padding(bottom = 188.dp), color = Color.Black.copy(alpha = 0.84f), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Timer", color = Color.White, modifier = Modifier.weight(1f))
                        listOf(0, 3, 5, 10).forEach { value -> TextButton(onClick = { timerSeconds = value }) { Text(if (value == 0) "OFF" else "${value}s", color = if (timerSeconds == value) Color.Yellow else Color.White) } }
                    }
                    if (mode == CameraMode.PORTRAIT && !bokehAvailable) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Background Blur", color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = { portraitBlur = (portraitBlur - 0.1f).coerceAtLeast(0f) }) { Text("−", color = Color.White) }
                            Text("${(portraitBlur * 100).toInt()}%", color = Color.White)
                            TextButton(onClick = { portraitBlur = (portraitBlur + 0.1f).coerceAtMost(1f) }) { Text("+", color = Color.White) }
                        }
                    }
                    exposureRange?.let { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("EV", color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = { exposureIndex = (exposureIndex - 1).coerceIn(r.lower, r.upper); camera?.cameraControl?.setExposureCompensationIndex(exposureIndex) }) { Text("−", color = Color.White) }
                            Text(exposureIndex.toString(), color = Color.White)
                            TextButton(onClick = { exposureIndex = (exposureIndex + 1).coerceIn(r.lower, r.upper); camera?.cameraControl?.setExposureCompensationIndex(exposureIndex) }) { Text("+", color = Color.White) }
                        }
                    }
                    if (mode == CameraMode.SLOW_MO && supportedSlowFps.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Slow Motion", color = Color.White, modifier = Modifier.weight(1f))
                            supportedSlowFps.forEach { fps -> TextButton(onClick = { slowMotionFps = fps }) { Text("${fps}fps", color = if (slowMotionFps == fps) Color.Yellow else Color.White) } }
                        }
                    }
                    Row {
                        listOf(1f, 2f, 3f).filter { it <= maxZoom + 0.01f }.forEach { z ->
                            TextButton(onClick = { val value = z.coerceIn(minZoom, maxZoom); camera?.cameraControl?.setZoomRatio(value); zoomRatio = value }) { Text("${z.toInt()}x", color = Color.White) }
                        }
                    }
                }
            }
        }

        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            IconButton(onClick = { showTools = !showTools }) { Icon(Icons.Default.Settings, null, tint = Color.White) }
            Box(
                Modifier.size(78.dp).border(4.dp, Color.White, CircleShape).padding(8.dp)
                    .background(if (isRecording) Color.Red else Color.White, if (isRecording) RoundedCornerShape(12.dp) else CircleShape)
                    .clickable(enabled = countdown == 0) {
                        when (mode) {
                            CameraMode.VIDEO, CameraMode.SLOW_MO -> {
                                if (recording == null) {
                                    startRecording(context, recorder, { r -> recording = r; isRecording = true }, { uri -> recording = null; isRecording = false; isPaused = false; uri?.let(onMediaCaptured) })
                                } else recording?.stop()
                            }
                            else -> {
                                if (timerSeconds > 0) countdown = timerSeconds
                                else scope.launch { capturePhotoNow(capture, context, mode, portraitBlur, scope, onMediaCaptured) }
                            }
                        }
                    }
            )
            if (isRecording) {
                IconButton(onClick = { recording?.let { if (isPaused) { it.resume(); isPaused = false } else { it.pause(); isPaused = true } } }) {
                    Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = Color.White)
                }
            }
        }
    }
}

private fun startRecording(
    context: Context,
    recorder: Recorder?,
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
    val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
    var pending = r.prepareRecording(context, output)
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
    val active = pending.start(ContextCompat.getMainExecutor(context)) { event ->
        if (event is VideoRecordEvent.Finalize) onFinalize(if (event.hasError()) null else event.outputResults.outputUri.toString())
    }
    onStart(active)
}

private suspend fun capturePhotoNow(
    capture: ImageCapture?,
    context: Context,
    mode: CameraMode,
    blurAmount: Float,
    scope: CoroutineScope,
    onMediaCaptured: (String) -> Unit
) {
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
                    CameraMode.PORTRAIT -> if (blurAmount > 0f) try { PortraitBlurProcessor.process(context, uri.toString(), blurAmount) } catch (_: Exception) { uri.toString() } else uri.toString()
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
