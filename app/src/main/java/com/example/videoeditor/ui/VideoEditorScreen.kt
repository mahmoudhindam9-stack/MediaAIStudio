package com.example.videoeditor.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.example.ai.generative.GenerativeType
import com.example.videoeditor.VideoEditorState
import com.example.videoeditor.VideoEditorViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max

@Composable
fun VideoEditorScreen(
    uriString: String,
    onBack: () -> Unit,
    onExported: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: VideoEditorViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    var showAiDialog by rememberSaveable { mutableStateOf(false) }
    var showTrimDialog by rememberSaveable { mutableStateOf(false) }
    var showVolumeDialog by rememberSaveable { mutableStateOf(false) }
    var showConsentType by rememberSaveable { mutableStateOf<String?>(null) }
    var showPromptType by rememberSaveable { mutableStateOf<String?>(null) }
    var promptText by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            }
            viewModel.addMedia(uris.map { it.toString() }, context.contentResolver)
        }
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            }
            viewModel.addAudio(uris.map { it.toString() }, context.contentResolver)
        }
    )

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) viewModel.toggleVoiceOverRecording()
        }
    )

    LaunchedEffect(Unit) {
        viewModel.aiMessages.collectLatest { msg -> snackbarHostState.showSnackbar(msg) }
    }
    LaunchedEffect(uriString) {
        viewModel.loadInitialMedia(uriString)
    }

    val selectedVideo = state.videoClips.firstOrNull { it.id == state.selectedItemId }
    val selectedAudio = state.audioTracks.firstOrNull { it.id == state.selectedItemId }

    if (showTrimDialog && selectedVideo != null) {
        var start by rememberSaveable(selectedVideo.id) { mutableFloatStateOf(selectedVideo.startTrimMs.toFloat()) }
        var end by rememberSaveable(selectedVideo.id) { mutableFloatStateOf((selectedVideo.startTrimMs + selectedVideo.durationMs).toFloat()) }
        val maxDuration = max(100f, selectedVideo.originalDurationMs.toFloat())
        val valid = end > start && end - start >= 100f

        AlertDialog(
            onDismissRequest = { showTrimDialog = false },
            title = { Text("Trim clip") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Start: ${formatTime(start.toLong())}")
                    Slider(
                        value = start.coerceIn(0f, maxDuration),
                        onValueChange = { newValue -> start = newValue.coerceAtMost(end - 100f) },
                        valueRange = 0f..maxDuration
                    )
                    Text("End: ${formatTime(end.toLong())}")
                    Slider(
                        value = end.coerceIn(100f, maxDuration),
                        onValueChange = { newValue -> end = newValue.coerceAtLeast(start + 100f) },
                        valueRange = 0f..maxDuration
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        viewModel.trimSelectedClip(start.toLong(), end.toLong())
                        showTrimDialog = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showTrimDialog = false }) { Text("Cancel") } }
        )
    }

    if (showVolumeDialog && (selectedVideo != null || selectedAudio != null)) {
        val selectedVolume = selectedVideo?.volume ?: selectedAudio?.volume ?: 1f
        var volume by rememberSaveable(state.selectedItemId) { mutableFloatStateOf(selectedVolume) }
        AlertDialog(
            onDismissRequest = { showVolumeDialog = false },
            title = { Text("Volume") },
            text = {
                Column {
                    Text("${(volume * 100).toInt()}%")
                    Slider(value = volume, onValueChange = { volume = it }, valueRange = 0f..1f)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSelectedVolume(volume)
                    showVolumeDialog = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showVolumeDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text("AI Video Tools") },
            text = {
                Column {
                    TextButton(onClick = { viewModel.runSmartCut(); showAiDialog = false }) { Text("Smart Cut") }
                    TextButton(onClick = { viewModel.runAutoCaptions(); showAiDialog = false }) { Text("Auto Captions") }
                    TextButton(onClick = { viewModel.runObjectTracking(); showAiDialog = false }) { Text("Object Tracking") }
                    TextButton(onClick = { viewModel.runSmartReframe(); showAiDialog = false }) { Text("Smart Reframe") }
                    TextButton(onClick = { viewModel.runEnhancement(); showAiDialog = false }) { Text("Video Enhancement") }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Advanced", color = Color.Gray, fontSize = 12.sp)
                    TextButton(onClick = { showConsentType = GenerativeType.IMAGE_TO_VIDEO.name; showAiDialog = false }) { Text("Image → Video") }
                    TextButton(onClick = { showConsentType = GenerativeType.VIDEO_TO_VIDEO.name; showAiDialog = false }) { Text("Video → Video") }
                    TextButton(onClick = { showConsentType = GenerativeType.VIDEO_EXTENSION.name; showAiDialog = false }) { Text("Video Extension") }
                }
            },
            confirmButton = { TextButton(onClick = { showAiDialog = false }) { Text("Close") } }
        )
    }

    if (showConsentType != null) {
        AlertDialog(
            onDismissRequest = { showConsentType = null },
            title = { Text("Cloud AI") },
            text = { Text("This operation may send media or prompts to a remote AI backend. Continue only after reviewing the configured backend and privacy policy.") },
            confirmButton = {
                TextButton(onClick = {
                    showPromptType = showConsentType
                    showConsentType = null
                }) { Text("Agree") }
            },
            dismissButton = { TextButton(onClick = { showConsentType = null }) { Text("Cancel") } }
        )
    }

    if (showPromptType != null) {
        val type = runCatching { GenerativeType.valueOf(showPromptType!!) }.getOrNull()
        if (type != null) {
            AlertDialog(
                onDismissRequest = { showPromptType = null },
                title = { Text(type.name) },
                text = {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Prompt") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.runGenerativeVideo(type, promptText)
                        promptText = ""
                        showPromptType = null
                    }) { Text("Generate") }
                },
                dismissButton = { TextButton(onClick = { showPromptType = null }) { Text("Cancel") } }
            )
        }
    }

    if (state.aiSuggestedCuts != null) {
        AlertDialog(
            onDismissRequest = { viewModel.rejectSmartCuts() },
            title = { Text("Review Smart Cut") },
            text = { Text("${state.aiSuggestedCuts!!.size} suggested cut interval(s) were generated.") },
            confirmButton = { TextButton(onClick = { viewModel.applySmartCuts() }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { viewModel.rejectSmartCuts() }) { Text("Reject") } }
        )
    }

    Scaffold(
        topBar = {
            com.example.ui.components.AppTopBar(
                title = "Video Editor",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.undo() }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { viewModel.redo() }) {
                        Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    TextButton(onClick = {
                        val exporter = com.example.videoeditor.export.VideoExport(context)
                        val outPath = java.io.File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4").absolutePath
                        exporter.export(
                            state = state,
                            outputFilePath = outPath,
                            onProgress = {},
                            onSuccess = { onExported(outPath) },
                            onError = { snackbarHostState.currentSnackbarData?.dismiss(); viewModel.run { } }
                        )
                    }) { Text("Export", color = MaterialTheme.colorScheme.secondary) }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            EditorToolbar(
                viewModel = viewModel,
                state = state,
                onAddMediaClick = { mediaPickerLauncher.launch(arrayOf("video/*", "image/*")) },
                onAddAudioClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                onTrimClick = { showTrimDialog = true },
                onVolumeClick = { showVolumeDialog = true },
                onAiClick = { showAiDialog = true },
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
                onSeek = { viewModel.seekTo(it) },
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
        IconButton(onClick = { viewModel.togglePlayback() }) {
            Icon(
                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
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

@Composable
fun EditorToolbar(
    viewModel: VideoEditorViewModel,
    state: VideoEditorState,
    onAddMediaClick: () -> Unit,
    onAddAudioClick: () -> Unit,
    onTrimClick: () -> Unit,
    onVolumeClick: () -> Unit,
    onRecordVoiceOver: () -> Unit,
    onAiClick: () -> Unit
) {
    val selectedIsVideo = state.videoClips.any { it.id == state.selectedItemId }
    val selectedTimelineItem = state.videoClips.firstOrNull { it.id == state.selectedItemId }
        ?: state.audioTracks.firstOrNull { it.id == state.selectedItemId }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAddMediaClick) {
            Icon(Icons.Default.Add, contentDescription = "Add media", tint = MaterialTheme.colorScheme.onBackground)
        }
        IconButton(onClick = onAddAudioClick) {
            Icon(Icons.Default.MusicNote, contentDescription = "Add audio", tint = MaterialTheme.colorScheme.onBackground)
        }
        IconButton(enabled = selectedIsVideo, onClick = onTrimClick) {
            Icon(Icons.Default.Crop, contentDescription = "Trim", tint = if (selectedIsVideo) MaterialTheme.colorScheme.onBackground else Color.Gray)
        }
        IconButton(enabled = selectedTimelineItem != null, onClick = onVolumeClick) {
            Icon(
                if (selectedTimelineItem?.isMuted == true) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "Volume",
                tint = if (selectedTimelineItem != null) MaterialTheme.colorScheme.onBackground else Color.Gray
            )
        }
        IconButton(enabled = selectedTimelineItem != null, onClick = { viewModel.toggleSelectedMute() }) {
            Icon(
                if (selectedTimelineItem?.isMuted == true) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "Mute",
                tint = if (selectedTimelineItem != null) MaterialTheme.colorScheme.onBackground else Color.Gray
            )
        }
        IconButton(enabled = selectedIsVideo, onClick = { viewModel.moveSelectedClipLeft() }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Move left", tint = if (selectedIsVideo) Color.White else Color.Gray)
        }
        IconButton(enabled = selectedIsVideo, onClick = { viewModel.moveSelectedClipRight() }) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Move right", tint = if (selectedIsVideo) Color.White else Color.Gray)
        }
        IconButton(enabled = selectedIsVideo, onClick = { viewModel.splitSelectedClip() }) {
            Icon(Icons.Default.ContentCut, contentDescription = "Split", tint = if (selectedIsVideo) Color.White else Color.Gray)
        }
        IconButton(enabled = selectedTimelineItem != null, onClick = { viewModel.deleteSelectedClip() }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = if (selectedTimelineItem != null) Color.White else Color.Gray)
        }
        IconButton(onClick = onAiClick) {
            Icon(Icons.Default.AutoAwesome, contentDescription = "AI Tools", tint = MaterialTheme.colorScheme.secondary)
        }
        IconButton(onClick = onRecordVoiceOver) {
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
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val scale = 1f / 100f // 1dp = 100ms

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
                .padding(vertical = 16.dp)
                .pointerInput(state.durationMs) {
                    detectTapGestures { offset ->
                        val timelineDp = (offset.x + scrollState.value) / density.density
                        val positionMs = (timelineDp / scale).toLong().coerceIn(0L, state.durationMs)
                        onSeek(positionMs)
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .width(maxOf(1f, state.durationMs * scale).dp)
                    .height(60.dp)
                    .padding(bottom = 4.dp)
            ) {
                state.videoClips.forEach { clip ->
                    val widthDp = maxOf(8f, clip.durationMs * scale).dp
                    Box(
                        modifier = Modifier
                            .width(widthDp)
                            .fillMaxHeight()
                            .background(
                                if (state.selectedItemId == clip.id) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary
                            )
                            .clickable {
                                onItemSelect(clip.id)
                            }
                            .padding(2.dp)
                    ) {
                        Text(
                            text = "Video",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            state.audioTracks.forEach { audio ->
                val widthDp = maxOf(8f, audio.durationMs * scale).dp
                val startDp = (audio.startTimeMs * scale).dp
                Box(
                    modifier = Modifier
                        .width(maxOf(1f, state.durationMs * scale).dp)
                        .height(40.dp)
                        .padding(bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = startDp)
                            .width(widthDp)
                            .fillMaxHeight()
                            .background(
                                if (state.selectedItemId == audio.id) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.tertiary
                            )
                            .clickable { onItemSelect(audio.id) }
                            .padding(2.dp)
                    ) {
                        Text(
                            audio.type.name,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        val playheadOffset = (state.playheadMs * scale).dp - with(density) { scrollState.value.toDp() }
        Box(
            modifier = Modifier
                .offset(x = playheadOffset.coerceAtLeast(0.dp))
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.error)
        )
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
