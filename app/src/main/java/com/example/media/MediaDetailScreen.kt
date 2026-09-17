package com.example.media

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    uriString: String,
    onBack: () -> Unit,
    onEditPhoto: (String) -> Unit,
    onEditVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaStoreRepository(context) }
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var currentUriString by remember(uriString) { mutableStateOf(uriString) }

    LaunchedEffect(Unit) {
        mediaRepository.getAllMedia().collect { mediaItems = it }
    }

    val currentUri = Uri.parse(currentUriString)
    val currentMimeType = context.contentResolver.getType(currentUri) ?: "image/jpeg"
    val currentIsVideo = currentMimeType.startsWith("video/")
    val navigableItems = remember(mediaItems, currentIsVideo) {
        mediaItems.filter { it.isVideo == currentIsVideo }
    }
    val currentIndex = navigableItems.indexOfFirst { it.uri.toString() == currentUriString }

    fun move(delta: Int) {
        if (navigableItems.isEmpty()) return
        val index = if (currentIndex >= 0) currentIndex else 0
        val target = (index + delta).coerceIn(0, navigableItems.lastIndex)
        currentUriString = navigableItems[target].uri.toString()
    }

    val canGoPrevious = currentIndex > 0
    val canGoNext = if (currentIndex >= 0) currentIndex < navigableItems.lastIndex else navigableItems.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (navigableItems.isNotEmpty()) {
                        val position = if (currentIndex >= 0) currentIndex + 1 else 1
                        Text("$position / ${navigableItems.size}")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { /* Future: Favorite */ }) {
                        Icon(Icons.Default.Favorite, contentDescription = "Favorite")
                    }
                    IconButton(onClick = { /* Future: Share */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = {
                        if (currentIsVideo) onEditVideo(currentUriString) else onEditPhoto(currentUriString)
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { /* Future: Delete */ }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(navigableItems, currentUriString) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        },
                        onDragEnd = {
                            when {
                                totalDrag < -80f && canGoNext -> move(+1)
                                totalDrag > 80f && canGoPrevious -> move(-1)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (currentIsVideo) {
                VideoPlayer(uri = currentUri)
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (canGoPrevious) {
                IconButton(
                    onClick = { move(-1) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = Color.White)
                }
            }
            if (canGoNext) {
                IconButton(
                    onClick = { move(+1) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
