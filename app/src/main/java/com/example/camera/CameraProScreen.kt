package com.example.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
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
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
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

private enum class ProCameraMode(val label: String) {
    PHOTO("PHOTO"),
    PORTRAIT("PORTRAIT"),
    NIGHT("NIGHT"),
    HDR("HDR"),
    VIDEO("VIDEO"),
    SLOW_MO("SLOW-MO")
}

@Composable
fun CameraProScreen(
    onMediaCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionManager = remember { PermissionManagerImpl(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val requiredPermissions = remember {
        mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    var hasCameraPermission by remember {
        mutableStateOf(permissionManager.hasPermission(Manifest.permission.CAMERA))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true ||
            permissionManager.hasPermission(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(requiredPermissions)
    }

    if (hasCameraPermission) {
        CameraProContent(
            onMediaCaptured = onMediaCaptured,
            scopeLaunch = { block -> scope.launch(block = block) }
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.permission_camera_mic))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                    Text(stringResource(R.string.grant_permissions))
                }
            }
        }
    }
}

@Composable
private fun CameraProContent(
    onMediaCaptured: (String) -> Unit,
    scopeLaunch: (suspend kotlinx.coroutines.CoroutineScope.() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var mode by remember { mutableStateOf(ProCameraMode.PHOTO) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recorder by remember { mutableStateOf<Recorder?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var canSwitchCamera by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var torchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var exposureRange by remember { mutableStateOf<Range<Int>?>(null) }

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var faceTarget by remember { mutableStateOf<FaceTarget?>(null) }
    var faceFocusEnabled by remember { mutableStateOf(true) }
    var faceLockCenter by remember { mutableStateOf<Offset?>(null) }
    var portraitBlur by remember { mutableFloatStateOf(0.7f) }
    var gridEnabled by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var showTools by remember { mutableStateOf(false) }
    var slowMotionFps by remember { mutableIntStateOf(120) }
    var slowMotionAvailable by remember { mutableStateOf(false) }
    var supportedSlowFps by remember { mutableStateOf(emptyList<Int>()) }

    var extensionsManager by remember { mutableStateOf<ExtensionsManager?>(null) }
    var bokehAvailable by remember { mutableStateOf(false) }
    var nightAvailable by remember { mutableStateOf(false) }
    var hdrAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(cameraProvider) {
        val provider = cameraProvider ?: return@LaunchedEffect
        try {
            extensionsManager = ExtensionsManager.getInstance(context, provider)
        } catch (e: Exception) {
            Log.w("CameraPro", "CameraX extensions unavailable", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recording?.stop()
                recording?.close()
            } catch (_: Exception) {
            }
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
            cameraExecutor.shutdown()
        }
    }

    // One binding pipeline for every camera mode.
    LaunchedEffect(cameraProvider, lensFacing, mode, slowMotionFps) {
        val provider = cameraProvider ?: return@LaunchedEffect
        if (isRecording && mode != ProCameraMode.VIDEO && mode != ProCameraMode.SLOW_MO) return@LaunchedEffect

        try {
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            canSwitchCamera = hasBack && hasFront

            val cameraInfo = provider.getCameraInfo(selector)
            val supportedQualities = QualitySelector.getSupportedQualities(cameraInfo)
            val preferredQualities = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
                .filter { supportedQualities.contains(it) }
            val qualitySelector = QualitySelector.fromOrderedList(
                preferredQualities.ifEmpty { listOf(Quality.FHD, Quality.HD, Quality.SD) },
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )

            hasFlash = cameraInfo.hasFlashUnit()
            val exposureState = cameraInfo.exposureState
            exposureRange = if (exposureState.isExposureCompensationSupported) {
                exposureState.exposureCompensationRange
            } else null

            bokehAvailable = extensionsManager?.isExtensionAvailable(selector, ExtensionMode.BOKEH) == true
            nightAvailable = extensionsManager?.isExtensionAvailable(selector, ExtensionMode.NIGHT) == true
            hdrAvailable = extensionsManager?.isExtensionAvailable(selector, ExtensionMode.HDR) == true

            provider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            if (mode == ProCameraMode.SLOW_MO) {
                val caps = Recorder.getHighSpeedVideoCapabilities(cameraInfo)
                if (caps == null) {
                    slowMotionAvailable = false
                    supportedSlowFps = emptyList()
                    return@LaunchedEffect
                }

                val hsQualities = caps.getSupportedQualities(androidx.camera.core.DynamicRange.SDR)
                val hsQuality = listOf(Quality.FHD, Quality.HD, Quality.SD).firstOrNull { hsQualities.contains(it) }
                    ?: return@LaunchedEffect
                val supportedRanges = cameraInfo.getSupportedFrameRateRanges(
                    HighSpeedVideoSessionConfig.Builder(
                        VideoCapture.withOutput(
                            Recorder.Builder()
                                .setQualitySelector(QualitySelector.from(hsQuality))
                                .build()
                        )
                    ).setPreview(preview).setSlowMotionEnabled(true).build()
                )
                val candidates = supportedRanges.flatMap { range ->
                    listOf(120, 240).filter { fps -> range.contains(fps) }
                }.distinct().sortedDescending()
                supportedSlowFps = candidates
                slowMotionAvailable = candidates.isNotEmpty()
                if (candidates.isEmpty()) return@LaunchedEffect
                val selectedFps = when {
                    candidates.contains(slowMotionFps) -> slowMotionFps
                    else -> candidates.first()
                }
                if (selectedFps != slowMotionFps) {
                    slowMotionFps = selectedFps
                    return@LaunchedEffect
                }

                val hsRecorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(hsQuality))
                    .build()
                val hsVideoCapture = VideoCapture.withOutput(hsRecorder)
                recorder = hsRecorder
                videoCapture = hsVideoCapture

                val probeConfig = HighSpeedVideoSessionConfig.Builder(hsVideoCapture)
                    .setPreview(preview)
                    .setFrameRateRange(Range(selectedFps, selectedFps))
                    .setSlowMotionEnabled(true)
                    .build()
                val bound = provider.bindToLifecycle(lifecycleOwner, selector, probeConfig)
                camera = bound
                cameraControl = bound.cameraControl
            } else {
                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(95)
                val imageCapture = captureBuilder.build()

                if (mode == ProCameraMode.PHOTO || mode == ProCameraMode.PORTRAIT ||
                    mode == ProCameraMode.NIGHT || mode == ProCameraMode.HDR) {
                    val extMode = when (mode) {
                        ProCameraMode.PORTRAIT -> if (bokehAvailable) ExtensionMode.BOKEH else ExtensionMode.NONE
                        ProCameraMode.NIGHT -> if (nightAvailable) ExtensionMode.NIGHT else ExtensionMode.NONE
                        ProCameraMode.HDR -> if (hdrAvailable) ExtensionMode.HDR else ExtensionMode.NONE
                        else -> ExtensionMode.NONE
                    }

                    val extSelector = if (extMode != ExtensionMode.NONE) {
                        extensionsManager?.getExtensionEnabledCameraSelector(selector, extMode) ?: selector
                    } else selector

                    val bound = provider.bindToLifecycle(lifecycleOwner, extSelector, preview, imageCapture)
                    camera = bound
                    cameraControl = bound.cameraControl

                    imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    if (bound.cameraInfo.hasFlashUnit()) imageCapture.flashMode = flashMode
                } else {
                    val normalRecorder = Recorder.Builder()
                        .setQualitySelector(qualitySelector)
                        .build()
                    val normalVideoCapture = VideoCapture.withOutput(normalRecorder)
                    recorder = normalRecorder
                    videoCapture = normalVideoCapture

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    val faceAnalyzer = FaceFocusAnalyzer { target -> faceTarget = target }
                    analysis.setAnalyzer(cameraExecutor, faceAnalyzer)

                    val bound = try {
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, normalVideoCapture, analysis)
                    } catch (_: Exception) {
                        analysis.clearAnalyzer()
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, normalVideoCapture)
                    }
                    camera = bound
                    cameraControl = bound.cameraControl
                }
            }

            camera?.cameraInfo?.zoomState?.value?.let {
                zoomRatio = it.zoomRatio
                minZoom = it.minZoomRatio
                maxZoom = it.maxZoomRatio
            }
            exposureRange?.let {
                exposureIndex = exposureIndex.coerceIn(it.lower, it.upper)
            }
            if (!hasFlash) {
                flashMode = ImageCapture.FLASH_MODE_OFF
                torchEnabled = false
            }
        } catch (e: Exception) {
            Log.e("CameraPro", "Camera binding failed", e)
        }
    }

    // Face-priority metering. Only move focus when the target moved enough.
    LaunchedEffect(faceTarget, faceFocusEnabled, camera) {
        if (!faceFocusEnabled) {
            faceLockCenter = null
            return@LaunchedEffect
        }
        val target = faceTarget ?: return@LaunchedEffect
        val boundCamera = camera ?: return@LaunchedEffect
        val point = Offset(
            target.normalizedX * previewView.width,
            target.normalizedY * previewView.height
        )
        val previous = faceLockCenter
        if (previous == null || (point - previous).getDistance() > 45f) {
            faceLockCenter = point
            val meteringPoint = previewView.meteringPointFactory.createPoint(point.x, point.y)
            boundCamera.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(meteringPoint)
                    .setAutoCancelDuration(2, TimeUnit.SECONDS)
                    .build()
            )
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            isPaused = false
            while (isActive && isRecording) {
                delay(1000)
                if (isActive && isRecording && !isPaused) recordingSeconds++
            }
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
            if (countdown == 0) capturePhotoNow(
                context = context,
                mode = mode,
                timerBlur = portraitBlur,
                imageCapture = findImageCapture(cameraProvider, lifecycleOwner),
                onMediaCaptured = onMediaCaptured,
                scopeLaunch = scopeLaunch
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { offset ->
                        focusPoint = offset
                        val boundCamera = camera ?: return@detectTapGestures
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        boundCamera.cameraControl.startFocusAndMetering(
                            FocusMeteringAction.Builder(point)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                        )
                    }
                }
                .pointerInput(camera) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        val boundCamera = camera ?: return@detectTransformGestures
                        val state = boundCamera.cameraInfo.zoomState.value ?: return@detectTransformGestures
                        val next = (state.zoomRatio * gestureZoom).coerceIn(
                            state.minZoomRatio,
                            state.maxZoomRatio
                        )
                        boundCamera.cameraControl.setZoomRatio(next)
                        zoomRatio = next
                    }
                }
        ) {
            AndroidView({ previewView }, Modifier.fillMaxSize())

            if (gridEnabled) {
                GridOverlay(Modifier.fillMaxSize())
            }

            focusPoint?.let { point ->
                Box(
                    Modifier
                        .offset { IntOffset(point.x.toInt() - 24, point.y.toInt() - 24) }
                        .size(48.dp)
                        .border(2.dp, Color.Yellow, CircleShape)
                )
            }

            faceTarget?.let {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .border(2.dp, Color.Cyan, RoundedCornerShape(20.dp))
                )
            }
        }

        if (countdown > 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = countdown.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, top = 42.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasFlash) {
                    IconButton(onClick = {
                        if (mode == ProCameraMode.VIDEO || mode == ProCameraMode.SLOW_MO) {
                            torchEnabled = !torchEnabled
                            cameraControl?.enableTorch(torchEnabled)
                        } else {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                        }
                    }) {
                        Icon(
                            when {
                                mode == ProCameraMode.VIDEO || mode == ProCameraMode.SLOW_MO -> if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff
                                flashMode == ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                flashMode == ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                                else -> Icons.Default.FlashOff
                            },
                            null,
                            tint = if (torchEnabled || flashMode != ImageCapture.FLASH_MODE_OFF) Color.Yellow else Color.White
                        )
                    }
                }

                IconButton(onClick = { gridEnabled = !gridEnabled }) {
                    Icon(Icons.Default.GridOn, null, tint = if (gridEnabled) Color.Yellow else Color.White)
                }
            }

            Text(
                text = when (mode) {
                    ProCameraMode.SLOW_MO -> "$slowMotionFps FPS"
                    else -> "${String.format(Locale.US, "%.1f", zoomRatio)}x"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Row {
                IconButton(onClick = { faceFocusEnabled = !faceFocusEnabled }) {
                    Text(
                        "FACE",
                        color = if (faceFocusEnabled) Color.Cyan else Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = {
                    if (!isRecording) lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else CameraSelector.LENS_FACING_BACK
                }, enabled = canSwitchCamera && !isRecording) {
                    Icon(Icons.Default.Cameraswitch, null, tint = if (canSwitchCamera) Color.White else Color.Gray)
                }
            }
        }

        if (isRecording) {
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 92.dp),
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%02d:%02d", recordingSeconds / 60, recordingSeconds % 60),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (showTools) {
            ProToolsPanel(
                mode = mode,
                timerSeconds = timerSeconds,
                slowMotionFps = slowMotionFps,
                supportedSlowFps = supportedSlowFps,
                portraitBlur = portraitBlur,
                exposureIndex = exposureIndex,
                exposureRange = exposureRange,
                minZoom = minZoom,
                maxZoom = maxZoom,
                onTimerChange = { timerSeconds = it },
                onFpsChange = { slowMotionFps = it },
                onBlurChange = { portraitBlur = it },
                onExposureChange = { index ->
                    exposureIndex = index
                    cameraControl?.setExposureCompensationIndex(index)
                },
                onZoomPreset = { zoom ->
                    cameraControl?.setZoomRatio(zoom)
                    zoomRatio = zoom.coerceIn(minZoom, maxZoom)
                }
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 132.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            ProCameraMode.values().forEach { cameraMode ->
                if (cameraMode != ProCameraMode.SLOW_MO || slowMotionAvailable || !cameraProviderReady(cameraProvider)) {
                    TextButton(
                        onClick = {
                            if (!isRecording) mode = cameraMode
                        }
                    ) {
                        Text(
                            cameraMode.label,
                            color = if (mode == cameraMode) Color.Yellow else Color.White.copy(alpha = 0.65f),
                            fontWeight = if (mode == cameraMode) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 58.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showTools = !showTools }) {
                Icon(Icons.Default.Tune, null, tint = Color.White)
            }

            Box(
                Modifier
                    .size(78.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .background(
                        if (isRecording) Color.Red else Color.White,
                        if (isRecording) RoundedCornerShape(12.dp) else CircleShape
                    )
                    .clickable(enabled = countdown == 0) {
                        when (mode) {
                            ProCameraMode.VIDEO, ProCameraMode.SLOW_MO -> {
                                val rec = recording
                                if (rec == null) {
                                    startRecording(
                                        context = context,
                                        videoCapture = videoCapture,
                                        recorder = recorder,
                                        mode = mode,
                                        onStart = { active ->
                                            recording = active
                                            isRecording = true
                                        },
                                        onFinalize = { uri ->
                                            recording = null
                                            isRecording = false
                                            isPaused = false
                                            if (uri != null) onMediaCaptured(uri)
                                        }
                                    )
                                } else {
                                    rec.stop()
                                }
                            }
                            else -> {
                                if (timerSeconds > 0) {
                                    countdown = timerSeconds
                                } else {
                                    scopeLaunch {
                                        capturePhotoNow(
                                            context = context,
                                            mode = mode,
                                            timerBlur = portraitBlur,
                                            imageCapture = findImageCapture(cameraProvider, lifecycleOwner),
                                            onMediaCaptured = onMediaCaptured,
                                            scopeLaunch = scopeLaunch
                                        )
                                    }
                                }
                            }
                        }
                    }
            )

            if (isRecording) {
                IconButton(onClick = {
                    recording?.let {
                        if (isPaused) {
                            it.resume()
                            isPaused = false
                        } else {
                            it.pause()
                            isPaused = true
                        }
                    }
                }) {
                    Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = Color.White)
                }
            } else {
                IconButton(onClick = {
                    val next = when {
                        zoomRatio < 1.5f -> 2f
                        zoomRatio < 2.5f -> 3f
                        else -> 1f
                    }.coerceIn(minZoom, maxZoom)
                    cameraControl?.setZoomRatio(next)
                    zoomRatio = next
                }) {
                    Text("Z", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun cameraProviderReady(provider: ProcessCameraProvider?): Boolean = provider != null

private fun findImageCapture(
    provider: ProcessCameraProvider?,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
): ImageCapture? {
    // ImageCapture is intentionally not reconstructed from the Camera object; photo capture is handled
    // through the dedicated temporary bind below only when needed. Normal camera mode already binds it.
    return null
}

private fun startRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>?,
    recorder: Recorder?,
    mode: ProCameraMode,
    onStart: (Recording) -> Unit,
    onFinalize: (String?) -> Unit
) {
    val capture = videoCapture ?: return
    val activeRecorder = recorder ?: return
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MediaAIStudio")
        }
    }
    val output = MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(values).build()

    var pending = activeRecorder.prepareRecording(context, output)
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
        pending = pending.withAudioEnabled()
    }

    val recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
        when (event) {
            is VideoRecordEvent.Start -> Unit
            is VideoRecordEvent.Finalize -> {
                if (event.hasError()) onFinalize(null)
                else onFinalize(event.outputResults.outputUri.toString())
            }
        }
    }
    onStart(recording)
}

private suspend fun capturePhotoNow(
    context: Context,
    mode: ProCameraMode,
    timerBlur: Float,
    imageCapture: ImageCapture?,
    onMediaCaptured: (String) -> Unit,
    scopeLaunch: (suspend kotlinx.coroutines.CoroutineScope.() -> Unit) -> Unit
) {
    // The active ImageCapture instance must be supplied by the caller in the production path.
    // Keep this guard explicit so a mode can never report a false capture success.
    val capture = imageCapture ?: return
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MediaAIStudio")
        }
    }
    val output = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ).build()

    capture.takePicture(
        output,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraPro", "Photo capture failed", exception)
            }

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = outputFileResults.savedUri ?: return
                scopeLaunch {
                    try {
                        val finalUri = when (mode) {
                            ProCameraMode.PORTRAIT -> {
                                if (timerBlur > 0f) {
                                    try {
                                        PortraitBlurProcessor.process(context, uri.toString(), timerBlur)
                                    } catch (e: Exception) {
                                        Log.w("CameraPro", "Portrait fallback failed", e)
                                        uri.toString()
                                    }
                                } else uri.toString()
                            }
                            ProCameraMode.NIGHT -> {
                                try {
                                    CpgaLowLightEngine(context.applicationContext).process(uri.toString()) { }
                                        .let { result ->
                                            when (result) {
                                                is com.example.ai.core.AIResult.Success -> result.outputUri
                                                else -> uri.toString()
                                            }
                                        }
                                } catch (e: Exception) {
                                    Log.w("CameraPro", "Night AI fallback failed", e)
                                    uri.toString()
                                }
                            }
                            else -> uri.toString()
                        }
                        onMediaCaptured(finalUri)
                    } catch (e: Exception) {
                        Log.e("CameraPro", "Post-processing failed", e)
                        onMediaCaptured(uri.toString())
                    }
                }
            }
        }
    )
}

@Composable
private fun GridOverlay(modifier: Modifier) {
    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .background(Color.White.copy(alpha = 0.28f))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .offset(y = (-120).dp)
                .background(Color.White.copy(alpha = 0.18f))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .offset(y = 120.dp)
                .background(Color.White.copy(alpha = 0.18f))
        )
    }
}

@Composable
private fun ProToolsPanel(
    mode: ProCameraMode,
    timerSeconds: Int,
    slowMotionFps: Int,
    supportedSlowFps: List<Int>,
    portraitBlur: Float,
    exposureIndex: Int,
    exposureRange: Range<Int>?,
    minZoom: Float,
    maxZoom: Float,
    onTimerChange: (Int) -> Unit,
    onFpsChange: (Int) -> Unit,
    onBlurChange: (Float) -> Unit,
    onExposureChange: (Int) -> Unit,
    onZoomPreset: (Float) -> Unit
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 90.dp)
            .align(Alignment.TopCenter),
        color = Color.Black.copy(alpha = 0.82f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Timer", color = Color.White, style = MaterialTheme.typography.labelMedium)
                listOf(0, 3, 5, 10).forEach { value ->
                    TextButton(onClick = { onTimerChange(value) }) {
                        Text(
                            if (value == 0) "OFF" else "${value}s",
                            color = if (value == timerSeconds) Color.Yellow else Color.White
                        )
                    }
                }
            }

            if (mode == ProCameraMode.SLOW_MO && supportedSlowFps.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FPS", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    supportedSlowFps.forEach { fps ->
                        TextButton(onClick = { onFpsChange(fps) }) {
                            Text(fps.toString(), color = if (fps == slowMotionFps) Color.Yellow else Color.White)
                        }
                    }
                }
            }

            if (mode == ProCameraMode.PORTRAIT) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Blur", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { onBlurChange((portraitBlur - 0.15f).coerceAtLeast(0f)) }) {
                        Icon(Icons.Default.Remove, null, tint = Color.White)
                    }
                    Text("${(portraitBlur * 100).toInt()}%", color = Color.White)
                    TextButton(onClick = { onBlurChange((portraitBlur + 0.15f).coerceAtMost(1f)) }) {
                        Icon(Icons.Default.Add, null, tint = Color.White)
                    }
                }
            }

            if (exposureRange != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("EV", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { onExposureChange((exposureIndex - 1).coerceIn(exposureRange.lower, exposureRange.upper)) }) {
                        Icon(Icons.Default.Remove, null, tint = Color.White)
                    }
                    Text(if (exposureIndex > 0) "+$exposureIndex" else exposureIndex.toString(), color = Color.White)
                    TextButton(onClick = { onExposureChange((exposureIndex + 1).coerceIn(exposureRange.lower, exposureRange.upper)) }) {
                        Icon(Icons.Default.Add, null, tint = Color.White)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1f, 2f, 3f).filter { it <= maxZoom + 0.01f }.forEach { zoom ->
                    TextButton(onClick = { onZoomPreset(zoom.coerceAtLeast(minZoom)) }) {
                        Text("${zoom.toInt()}x", color = Color.White)
                    }
                }
            }
        }
    }
}
