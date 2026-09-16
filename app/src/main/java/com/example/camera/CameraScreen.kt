package com.example.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.core.permission.PermissionManagerImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun CameraScreen(
    onMediaCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManagerImpl(context) }

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
        if (!hasCameraPermission) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    if (hasCameraPermission) {
        CameraContent(
            onMediaCaptured = onMediaCaptured
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(id = R.string.permission_camera_mic))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                    Text(stringResource(id = R.string.grant_permissions))
                }
            }
        }
    }
}

private fun bindCameraUseCases(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    lensFacing: Int,
    isVideoMode: Boolean,
    imageCapture: ImageCapture,
    videoCapture: VideoCapture<Recorder>
): Camera {
    cameraProvider.unbindAll()

    val preview = Preview.Builder()
        .build()
        .also {
            it.surfaceProvider = previewView.surfaceProvider
        }

    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()

    val useCases = if (isVideoMode) {
        arrayOf(preview, videoCapture)
    } else {
        arrayOf(preview, imageCapture)
    }

    return cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        *useCases
    )
}

@Composable
private fun CameraContent(
    onMediaCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isVideoMode by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTimeSeconds by remember { mutableIntStateOf(0) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var showZoomIndicator by remember { mutableStateOf(false) }

    var camera: Camera? by remember { mutableStateOf(null) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }

    var hasFlashUnit by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var canSwitchCamera by remember { mutableStateOf(false) }

    var isExposureSupported by remember { mutableStateOf(false) }
    var exposureRange by remember { mutableStateOf<Range<Int>?>(null) }
    var exposureIndex by remember { mutableIntStateOf(0) }

    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    val previewView = remember { PreviewView(context) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(95)
            .build()
    }

    var videoCapture by remember {
        mutableStateOf(
            VideoCapture.withOutput(
                Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()
            )
        )
    }

    var recording: Recording? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recording?.stop()
                recording?.close()
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error stopping recording on dispose", e)
            }
            recording = null
        }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(context))
    }

    // Unified camera binding flow reacting to lensFacing and isVideoMode
    LaunchedEffect(cameraProvider, lensFacing, isVideoMode) {
        val provider = cameraProvider ?: return@LaunchedEffect

        val hasBackCamera = try {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        } catch (e: Exception) {
            false
        }
        val hasFrontCamera = try {
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        } catch (e: Exception) {
            false
        }
        canSwitchCamera = hasBackCamera && hasFrontCamera

        val targetLens = when {
            lensFacing == CameraSelector.LENS_FACING_FRONT && hasFrontCamera -> CameraSelector.LENS_FACING_FRONT
            lensFacing == CameraSelector.LENS_FACING_BACK && hasBackCamera -> CameraSelector.LENS_FACING_BACK
            hasBackCamera -> CameraSelector.LENS_FACING_BACK
            hasFrontCamera -> CameraSelector.LENS_FACING_FRONT
            else -> lensFacing
        }

        if (targetLens != lensFacing) {
            lensFacing = targetLens
            return@LaunchedEffect
        }

        val cameraSelector = CameraSelector.Builder().requireLensFacing(targetLens).build()
        val cameraInfo = if (provider.hasCamera(cameraSelector)) {
            provider.getCameraInfo(cameraSelector)
        } else null

        // Device-capability-aware video quality selection for the selected camera
        val supportedQualities = cameraInfo?.let { QualitySelector.getSupportedQualities(it) } ?: emptyList()
        val preferredOrder = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
        val supportedPreferred = preferredOrder.filter { supportedQualities.contains(it) }

        val qualitySelector = if (supportedPreferred.isNotEmpty()) {
            QualitySelector.fromOrderedList(
                supportedPreferred,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
        } else {
            QualitySelector.fromOrderedList(
                preferredOrder,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()

        val newVideoCapture = VideoCapture.withOutput(recorder)
        videoCapture = newVideoCapture

        // Keep photo rotation synchronized with PreviewView rotation
        imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0

        try {
            val boundCamera = bindCameraUseCases(
                cameraProvider = provider,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                lensFacing = targetLens,
                isVideoMode = isVideoMode,
                imageCapture = imageCapture,
                videoCapture = newVideoCapture
            )

            camera = boundCamera
            cameraControl = boundCamera.cameraControl

            // Flash & Torch capabilities
            hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()
            if (!hasFlashUnit) {
                flashMode = ImageCapture.FLASH_MODE_OFF
                imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
                isTorchOn = false
            } else {
                if (!isVideoMode) {
                    imageCapture.flashMode = flashMode
                } else {
                    boundCamera.cameraControl.enableTorch(isTorchOn)
                }
            }

            // Exposure compensation capabilities
            val expState = boundCamera.cameraInfo.exposureState
            isExposureSupported = expState.isExposureCompensationSupported
            if (isExposureSupported) {
                exposureRange = expState.exposureCompensationRange
                exposureIndex = expState.exposureCompensationIndex.coerceIn(
                    expState.exposureCompensationRange.lower,
                    expState.exposureCompensationRange.upper
                )
            } else {
                exposureRange = null
                exposureIndex = 0
            }

            // Zoom ratio initialization
            boundCamera.cameraInfo.zoomState.value?.let { zs ->
                zoomRatio = zs.zoomRatio
            }
        } catch (exc: Exception) {
            Log.e("CameraScreen", "Use case binding failed", exc)
        }
    }

    // Observe real zoom state updates
    LaunchedEffect(camera) {
        val boundCamera = camera ?: return@LaunchedEffect
        boundCamera.cameraInfo.zoomState.observe(lifecycleOwner) { zs ->
            zoomRatio = zs.zoomRatio
        }
    }

    // Cancel focus indicator after duration
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(2000)
            focusPoint = null
        }
    }

    // Temporarily display real zoom ratio indicator
    LaunchedEffect(zoomRatio) {
        showZoomIndicator = true
        delay(1500)
        showZoomIndicator = false
    }

    // Fixed cancellable recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTimeSeconds = 0
            while (isActive && isRecording) {
                delay(1000)
                if (isActive && isRecording) {
                    recordingTimeSeconds++
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Preview interaction layer handling pinch zoom and tap focus
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { offset ->
                        val boundCamera = camera ?: return@detectTapGestures
                        focusPoint = offset
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        boundCamera.cameraControl.startFocusAndMetering(action)
                    }
                }
                .pointerInput(camera) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        val boundCamera = camera ?: return@detectTransformGestures
                        val zs = boundCamera.cameraInfo.zoomState.value
                        val minZoom = zs?.minZoomRatio ?: 1f
                        val maxZoom = zs?.maxZoomRatio ?: 1f
                        val currentZoom = zs?.zoomRatio ?: zoomRatio
                        val newZoom = (currentZoom * gestureZoom).coerceIn(minZoom, maxZoom)
                        boundCamera.cameraControl.setZoomRatio(newZoom)
                        zoomRatio = newZoom
                    }
                }
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Real Focus Indicator Ring
            focusPoint?.let { point ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (point.x - 24.dp.toPx()).toInt(),
                                (point.y - 24.dp.toPx()).toInt()
                            )
                        }
                        .size(48.dp)
                        .border(2.dp, Color.Yellow, CircleShape)
                )
            }
        }

        // Dark gradient overlays for cinematic feel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        // Real Zoom Ratio Display
        if (showZoomIndicator) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1fx", zoomRatio),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Recording Timer
        if (isRecording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).background(Color.Red, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.US, "%02d:%02d", recordingTimeSeconds / 60, recordingTimeSeconds % 60),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Top Controls: Flash/Torch, Exposure Control, Camera Switching
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasFlashUnit) {
                if (!isVideoMode) {
                    IconButton(
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                            imageCapture.flashMode = flashMode
                        }
                    ) {
                        val icon = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                            else -> Icons.Default.FlashOff
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Toggle Flash",
                            tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) Color.Yellow else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            isTorchOn = !isTorchOn
                            cameraControl?.enableTorch(isTorchOn)
                        }
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (isTorchOn) "Torch On" else "Torch Off",
                            tint = if (isTorchOn) Color.Yellow else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Exposure Control
            if (isExposureSupported && exposureRange != null && exposureRange!!.lower < exposureRange!!.upper) {
                val range = exposureRange!!
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val newIndex = (exposureIndex - 1).coerceIn(range.lower, range.upper)
                            cameraControl?.setExposureCompensationIndex(newIndex)
                            exposureIndex = newIndex
                        },
                        enabled = exposureIndex > range.lower,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease Exposure",
                            tint = if (exposureIndex > range.lower) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (exposureIndex > 0) "+$exposureIndex" else "$exposureIndex",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = {
                            val newIndex = (exposureIndex + 1).coerceIn(range.lower, range.upper)
                            cameraControl?.setExposureCompensationIndex(newIndex)
                            exposureIndex = newIndex
                        },
                        enabled = exposureIndex < range.upper,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase Exposure",
                            tint = if (exposureIndex < range.upper) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Camera Switch Button
            IconButton(
                onClick = {
                    if (isRecording) return@IconButton
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                enabled = canSwitchCamera && !isRecording
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = if (canSwitchCamera && !isRecording) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Mode Switch (Photo / Video)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
                .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(
                onClick = { if (!isRecording) isVideoMode = false },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (!isVideoMode) Color.Yellow else Color.White.copy(alpha = 0.6f)
                )
            ) {
                Text("PHOTO", fontWeight = if (!isVideoMode) FontWeight.Bold else FontWeight.Normal)
            }
            TextButton(
                onClick = { if (!isRecording) isVideoMode = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isVideoMode) Color.Yellow else Color.White.copy(alpha = 0.6f)
                )
            ) {
                Text("VIDEO", fontWeight = if (isVideoMode) FontWeight.Bold else FontWeight.Normal)
            }
        }

        // Capture Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(72.dp)
                .border(
                    width = 4.dp,
                    color = Color.White,
                    shape = CircleShape
                )
                .padding(8.dp)
                .background(
                    color = if (isRecording) Color.Red else Color.White,
                    shape = if (isRecording) RoundedCornerShape(8.dp) else CircleShape
                )
                .clickable {
                    if (isVideoMode) {
                        if (isRecording) {
                            recording?.stop()
                            recording = null
                            isRecording = false
                        } else {
                            val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MediaAIStudio")
                                }
                            }
                            val mediaStoreOutputOptions = MediaStoreOutputOptions
                                .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                                .setContentValues(contentValues)
                                .build()

                            val pendingRecording = videoCapture.output
                                .prepareRecording(context, mediaStoreOutputOptions)
                                .apply {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        withAudioEnabled()
                                    }
                                }

                            val activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                                when (recordEvent) {
                                    is VideoRecordEvent.Start -> {
                                        isRecording = true
                                    }
                                    is VideoRecordEvent.Finalize -> {
                                        if (!recordEvent.hasError()) {
                                            onMediaCaptured(recordEvent.outputResults.outputUri.toString())
                                        } else {
                                            Log.e("CameraScreen", "Video capture ends with error: ${recordEvent.error}")
                                        }
                                        recording?.close()
                                        recording = null
                                        isRecording = false
                                    }
                                }
                            }
                            recording = activeRecording
                        }
                    } else {
                        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MediaAIStudio")
                            }
                        }

                        // Ensure latest rotation before capture
                        imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0

                        val outputOptions = ImageCapture.OutputFileOptions
                            .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            .build()

                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onError(exc: ImageCaptureException) {
                                    Log.e("CameraScreen", "Photo capture failed: ${exc.message}", exc)
                                }
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    val savedUri = output.savedUri
                                    if (savedUri != null) {
                                        onMediaCaptured(savedUri.toString())
                                    }
                                }
                            }
                        )
                    }
                }
        )
    }
}

