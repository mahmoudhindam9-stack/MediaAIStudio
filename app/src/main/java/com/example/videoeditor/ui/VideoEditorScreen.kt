package com.example.videoeditor.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ai.generative.GenerativeType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.example.videoeditor.VideoEditorState
import com.example.videoeditor.VideoEditorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    uriString: String,
    onBack: () -> Unit,
    onExported: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: VideoEditorViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val showAiDialog = remember { mutableStateOf(false) }
    
    val showConsentDialog = remember { mutableStateOf<GenerativeType?>(null) }
    val showPromptDialog = remember { mutableStateOf<GenerativeType?>(null) }
    val promptText = remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                viewModel.addMedia(uris.map { it.toString() }, context.contentResolver)
            }
        }
    )

    LaunchedEffect(Unit) {
        viewModel.aiMessages.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    
    if (showAiDialog.value) {
        AlertDialog(
            onDismissRequest = { showAiDialog.value = false },
            title = { Text(stringResource(com.example.R.string.ai_video_tools)) },
            text = {
                Column {
                    TextButton(onClick = { viewModel.runSmartCut(); showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_smart_cut)) }
                    TextButton(onClick = { viewModel.runAutoCaptions(); showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_auto_captions)) }
                    TextButton(onClick = { viewModel.runObjectTracking(); showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_object_tracking)) }
                    TextButton(onClick = { viewModel.runSmartReframe(); showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_smart_reframe)) }
                    TextButton(onClick = { viewModel.runEnhancement(); showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_video_enhance)) }

                    androidx.compose.material3.HorizontalDivider()
                    Text(stringResource(com.example.R.string.ai_advanced_tools), color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp, modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = { showConsentDialog.value = GenerativeType.IMAGE_TO_VIDEO; showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_img_to_vid)) }
                    TextButton(onClick = { showConsentDialog.value = GenerativeType.VIDEO_TO_VIDEO; showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_vid_to_vid)) }
                    TextButton(onClick = { showConsentDialog.value = GenerativeType.VIDEO_EXTENSION; showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_vid_ext)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiDialog.value = false }) { Text(stringResource(com.example.R.string.ai_close)) }
            }
        )
    }
    
    
    
    if (showConsentDialog.value != null) {
        AlertDialog(
            onDismissRequest = { showConsentDialog.value = null },
            title = { Text(stringResource(com.example.R.string.ai_advanced_tools)) },
            text = { Text(stringResource(com.example.R.string.ai_cloud_privacy_consent)) },
            confirmButton = {
                TextButton(onClick = { 
                    val type = showConsentDialog.value
                    showConsentDialog.value = null
                    showPromptDialog.value = type
                }) { Text("Agree") }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog.value = null }) { Text(stringResource(com.example.R.string.ai_close)) }
            }
        )
    }

    if (showPromptDialog.value != null) {
        AlertDialog(
            onDismissRequest = { showPromptDialog.value = null },
            title = { Text(showPromptDialog.value!!.name) },
            text = {
                OutlinedTextField(
                    value = promptText.value,
                    onValueChange = { promptText.value = it },
                    label = { Text(stringResource(com.example.R.string.ai_prompt_hint)) }
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.runGenerativeVideo(showPromptDialog.value!!, promptText.value)
                    showPromptDialog.value = null
                    promptText.value = ""
                }) { Text(stringResource(com.example.R.string.ai_generate)) }
            },
            dismissButton = {
                TextButton(onClick = { showPromptDialog.value = null }) { Text(stringResource(com.example.R.string.ai_close)) }
            }
        )
    }

    if (state.aiSuggestedCuts != null) {
        AlertDialog(
            onDismissRequest = { viewModel.rejectSmartCuts() },
            title = { Text(stringResource(com.example.R.string.ai_review_cuts)) },
            text = { Text(stringResource(com.example.R.string.ai_cuts_suggested_msg, state.aiSuggestedCuts!!.size)) },
            confirmButton = { TextButton(onClick = { viewModel.applySmartCuts() }) { Text(stringResource(com.example.R.string.ai_apply)) } },
            dismissButton = { TextButton(onClick = { viewModel.rejectSmartCuts() }) { Text(stringResource(com.example.R.string.ai_reject)) } }
        )
    }
    
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.toggleVoiceOverRecording()
            }
        }
    )
    
    LaunchedEffect(uriString) {
        viewModel.loadInitialMedia(uriString)
    }
    
    Scaffold(
        topBar = {
            com.example.ui.components.AppTopBar(
                title = "Video Editor",
                onBack = { 
                    viewModel.exoPlayer.release()
                    onBack()
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = MaterialTheme.colorScheme.onBackground) }
                    IconButton(onClick = { viewModel.redo() }) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = MaterialTheme.colorScheme.onBackground) }
                    TextButton(onClick = { 
                        val exporter = com.example.videoeditor.export.VideoExport(context)
                        val outPath = java.io.File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4").absolutePath
                        exporter.export(
                            state = state,
                            outputFilePath = outPath,
                            onProgress = {},
                            onSuccess = { onExported(outPath) },
                            onError = { it.printStackTrace() }
                        )
                    }) {
                        Text("Export", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            EditorToolbar(
                viewModel = viewModel, 
                state = state, 
                onAddMediaClick = {
                    mediaPickerLauncher.launch(arrayOf("video/*", "image/*"))
                },
                onAiClick = { showAiDialog.value = true },
                 onRecordVoiceOver = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.toggleVoiceOverRecording()
                    } else {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = viewModel.exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            VideoPlaybackControls(viewModel, state)
            
            TimelineView(
                state = state,
                onItemSelect = { viewModel.selectItem(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
}

@Composable
fun VideoPlaybackControls(viewModel: VideoEditorViewModel, state: VideoEditorState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Playback buttons etc
        IconButton(onClick = {
            if (viewModel.exoPlayer.isPlaying) {
                viewModel.exoPlayer.pause()
            } else {
                viewModel.exoPlayer.play()
            }
        }) {
            val isPlaying = state.playheadMs > 0 && viewModel.exoPlayer.isPlaying
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                contentDescription = "Play/Pause", 
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = formatTime(state.playheadMs) + " / " + formatTime(state.durationMs),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp
        )
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun EditorToolbar(viewModel: VideoEditorViewModel, state: VideoEditorState, onAddMediaClick: () -> Unit, onRecordVoiceOver: () -> Unit, onAiClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(onClick = onAddMediaClick) {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onBackground)
        }
        IconButton(onClick = { viewModel.splitSelectedClip() }) {
            Icon(Icons.Default.ContentCut, contentDescription = "Split", tint = if (state.selectedItemId != null) Color.White else Color.Gray)
        }
        IconButton(onClick = { viewModel.deleteSelectedClip() }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = if (state.selectedItemId != null) Color.White else Color.Gray)
        }

        IconButton(onClick = onAiClick) {
            Icon(Icons.Default.AutoAwesome, contentDescription = "AI Tools", tint = MaterialTheme.colorScheme.secondary)
        }
        IconButton(onClick = { onRecordVoiceOver() }) {
            Icon(
                if (viewModel.isRecordingVoiceOver) Icons.Default.Stop else Icons.Default.Mic, 
                contentDescription = "Voice Over", 
                tint = if (viewModel.isRecordingVoiceOver) MaterialTheme.colorScheme.error else Color.White
            )
        }
    }
}

@Composable
fun TimelineView(
    state: VideoEditorState,
    onItemSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    
    // Scale: 1 dp = 100 ms
    val scale = 1f / 100f
    
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
                .padding(vertical = 16.dp)
        ) {
            // Video Track
            Row(modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(bottom = 4.dp)) {
                state.videoClips.forEach { clip ->
                    val widthDp = (clip.durationMs * scale).dp
                    Box(
                        modifier = Modifier
                            .width(widthDp)
                            .fillMaxHeight()
                            .background(if (state.selectedItemId == clip.id) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                            .clickable { onItemSelect(clip.id) }
                            .padding(2.dp)
                    ) {
                        Text("Video", color = MaterialTheme.colorScheme.onBackground, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            
            // Audio Tracks
            state.audioTracks.forEach { audio ->
                val widthDp = (audio.durationMs * scale).dp
                val startDp = (audio.startTimeMs * scale).dp
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = startDp)
                            .width(widthDp)
                            .fillMaxHeight()
                            .background(if (state.selectedItemId == audio.id) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary)
                            .clickable { onItemSelect(audio.id) }
                            .padding(2.dp)
                    ) {
                        Text(audio.type.name, color = MaterialTheme.colorScheme.onTertiary, fontSize = 10.sp)
                    }
                }
            }
        }
        
        // Playhead
        val playheadOffset = (state.playheadMs * scale).dp
        Box(
            modifier = Modifier
                .offset(x = playheadOffset - scrollState.value.dp / density.density)
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.error)
        )
    }
}
