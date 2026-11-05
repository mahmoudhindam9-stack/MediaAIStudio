package com.example.media

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaDetailScreen(
    uriString: String,
    onBack: () -> Unit,
    onEditPhoto: (String) -> Unit,
    onEditVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaStoreRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        mediaRepository.getAllMedia().collect { mediaItems = it }
    }

    val requestedUri = Uri.parse(uriString)
    val requestedMimeType = context.contentResolver.getType(requestedUri) ?: "image/jpeg"
    val requestedIsVideo = requestedMimeType.startsWith("video/")
    val navigableItems = remember(mediaItems, requestedIsVideo) {
        mediaItems.filter { it.isVideo == requestedIsVideo }
    }
    val initialIndex = navigableItems.indexOfFirst { it.uri.toString() == uriString }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) {
        navigableItems.size.coerceAtLeast(1)
    }

    LaunchedEffect(uriString, navigableItems) {
        val matchingIndex = navigableItems.indexOfFirst { it.uri.toString() == uriString }
        if (matchingIndex >= 0 && pagerState.currentPage != matchingIndex) {
            pagerState.scrollToPage(matchingIndex)
        }
    }

    val currentMedia = navigableItems.getOrNull(pagerState.currentPage)
    val currentUriString = currentMedia?.uri?.toString() ?: uriString
    val currentIsVideo = currentMedia?.isVideo ?: requestedIsVideo
    val canGoPrevious = pagerState.currentPage > 0
    val canGoNext = pagerState.currentPage < navigableItems.lastIndex

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (navigableItems.isNotEmpty()) {
                        Text("${pagerState.currentPage + 1} / ${navigableItems.size}")
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
                    IconButton(onClick = { /* Favorite action can be connected to the media repository. */ }) {
                        Icon(Icons.Default.Favorite, contentDescription = "Favorite")
                    }
                    IconButton(onClick = { /* Share action can be connected to the Android share sheet. */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = {
                        if (currentIsVideo) onEditVideo(currentUriString) else onEditPhoto(currentUriString)
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { /* Delete action can be connected to the media repository. */ }) {
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
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (navigableItems.isEmpty()) {
                Text("Media is no longer available", color = Color.White)
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0,
                    key = { page -> navigableItems[page].uri.toString() }
                ) { page ->
                    val item = navigableItems[page]
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (item.isVideo) {
                            VideoPlayer(uri = item.uri)
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            if (canGoPrevious) {
                IconButton(
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
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
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
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
