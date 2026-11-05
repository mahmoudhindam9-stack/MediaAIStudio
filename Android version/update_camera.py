import re

with open("app/src/main/java/com/example/camera/CameraScreen.kt", "r") as f:
    text = f.read()

# We need to replace the entire UI part of CameraScreen.
# The UI starts around:
#    Box(modifier = Modifier.fillMaxSize()) {
#        AndroidView( ...

new_ui = """    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        
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
                    text = String.format("%02d:%02d", recordingTimeSeconds / 60, recordingTimeSeconds % 60),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Top Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (!isVideoMode) {
                IconButton(
                    onClick = {
                        flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                            ImageCapture.FLASH_MODE_ON
                        } else {
                            ImageCapture.FLASH_MODE_OFF
                        }
                        imageCapture.flashMode = flashMode
                    }
                ) {
                    Icon(
                        imageVector = if (flashMode == ImageCapture.FLASH_MODE_ON) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Flash",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
            
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
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
                            recording = videoCapture.output
                                .prepareRecording(context, mediaStoreOutputOptions)
                                .apply {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        withAudioEnabled()
                                    }
                                }
                                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                                    when (recordEvent) {
                                        is VideoRecordEvent.Start -> {
                                            isRecording = true
                                        }
                                        is VideoRecordEvent.Finalize -> {
                                            if (!recordEvent.hasError()) {
                                                onMediaCaptured(recordEvent.outputResults.outputUri.toString())
                                            } else {
                                                recording?.close()
                                                recording = null
                                                Log.e("CameraScreen", "Video capture ends with error: ${recordEvent.error}")
                                            }
                                            isRecording = false
                                        }
                                    }
                                }
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
"""

start_idx = text.find('Box(modifier = Modifier.fillMaxSize()) {')
if start_idx != -1:
    text = text[:start_idx] + new_ui
    with open("app/src/main/java/com/example/camera/CameraScreen.kt", "w") as f:
        f.write(text)
    print("CameraScreen updated.")
else:
    print("Could not find start of UI in CameraScreen")
