package com.example.media

import android.Manifest
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.R
import com.example.core.permission.PermissionManagerImpl
import com.example.ui.components.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onNavigateToMediaDetail: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mediaRepository = remember { MediaStoreRepository(context) }
    val permissionManager = remember { PermissionManagerImpl(context) }

    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasPermissions by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showAlbumDialog by remember { mutableStateOf(false) }
    var albumDialogMode by remember { mutableStateOf(AlbumDialogMode.ADD) }
    var albumName by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun reload() {
        coroutineScope.launch {
            mediaRepository.getAllMedia().collect { mediaItems = it }
        }
    }

    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        selectedUris = emptySet()
        reload()
    }

    fun deleteSelected() {
        val selected = mediaItems.filter { it.uri.toString() in selectedUris }
        if (selected.isEmpty()) return
        coroutineScope.launch {
            isBusy = true
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intentSender: IntentSender = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        selected.map { it.uri }
                    ).intentSender
                    deleteRequestLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                } else {
                    val count = mediaRepository.deleteUris(selected.map { it.uri })
                    selectedUris = emptySet()
                    reload()
                    statusMessage = if (count > 0) "$count items deleted" else "Nothing deleted"
                }
            } catch (t: Throwable) {
                statusMessage = t.message ?: "Delete failed"
            } finally {
                isBusy = false
                showDeleteConfirmation = false
            }
        }
    }

    fun selectedItems(): List<MediaItem> = mediaItems.filter { it.uri.toString() in selectedUris }

    val existingAlbums = remember(mediaItems) {
        mediaItems.mapNotNull { item ->
            val path = item.relativePath ?: return@mapNotNull null
            val marker = path.substringAfter("MediaAIStudio/", "")
            marker.trim('/').takeIf { it.isNotBlank() && !it.contains('/') }
        }.distinct().sorted()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = requiredPermissions.all { permissions[it] == true || permissionManager.hasPermission(it) }
        hasPermissions = allGranted
        if (allGranted) {
            reload()
            isLoading = false
        } else {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = requiredPermissions.all { permissionManager.hasPermission(it) }
        hasPermissions = allGranted
        if (allGranted) {
            reload()
            isLoading = false
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Scaffold(
        topBar = {
            if (selectedUris.isEmpty()) {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.nav_library)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (mediaItems.isNotEmpty()) {
                            IconButton(onClick = { selectedUris = mediaItems.map { it.uri.toString() }.toSet() }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("${selectedUris.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedUris = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectedUris = mediaItems.map { it.uri.toString() }.toSet() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (selectedUris.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BulkActionButton(Icons.Default.Share, "Send") { MediaBulkActions.share(context, selectedItems()) }
                        BulkActionButton(Icons.Default.Delete, "Delete") { showDeleteConfirmation = true }
                        BulkActionButton(Icons.Default.Edit, "Edit", enabled = selectedUris.size == 1) {
                            selectedItems().firstOrNull()?.let { onNavigateToMediaDetail(it.uri.toString()) }
                        }
                        BulkActionButton(Icons.Default.Collections, "Merge", enabled = selectedUris.size >= 2 && selectedItems().all { !it.isVideo }) {
                            coroutineScope.launch {
                                isBusy = true
                                try {
                                    val merged = MediaBulkActions.mergeImages(context, selectedItems())
                                    if (merged != null) {
                                        selectedUris = emptySet()
                                        reload()
                                        onNavigateToMediaDetail(merged.toString())
                                    } else {
                                        statusMessage = "Merge failed"
                                    }
                                } catch (t: Throwable) {
                                    statusMessage = t.message ?: "Merge failed"
                                } finally {
                                    isBusy = false
                                }
                            }
                        }
                        BulkActionButton(Icons.Default.CreateNewFolder, "New album") {
                            albumName = ""
                            albumDialogMode = AlbumDialogMode.CREATE
                            showAlbumDialog = true
                        }
                        BulkActionButton(Icons.Default.DriveFileMove, "Add to album") {
                            albumDialogMode = AlbumDialogMode.ADD
                            showAlbumDialog = true
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                !hasPermissions -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(id = R.string.permission_denied_library), color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(requiredPermissions) }) { Text(stringResource(id = R.string.request_permissions)) }
                }
                mediaItems.isEmpty() -> EmptyState(
                    title = stringResource(id = R.string.state_empty),
                    description = "Import photos or videos to begin editing.",
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(mediaItems, key = { it.uri.toString() }) { item ->
                        val selected = item.uri.toString() in selectedUris
                        MediaItemThumbnail(
                            mediaItem = item,
                            selected = selected,
                            onClick = {
                                if (selectedUris.isNotEmpty()) {
                                    selectedUris = if (selected) selectedUris - item.uri.toString() else selectedUris + item.uri.toString()
                                } else {
                                    onNavigateToMediaDetail(item.uri.toString())
                                }
                            },
                            onLongClick = { selectedUris = selectedUris + item.uri.toString() }
                        )
                    }
                }
            }

            if (isBusy) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Working...", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            statusMessage?.let { message ->
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(2500)
                    statusMessage = null
                }
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    action = { TextButton(onClick = { statusMessage = null }) { Text("OK") } }
                ) { Text(message) }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete selected media?") },
            text = { Text("${selectedUris.size} items will be removed from the device.") },
            confirmButton = { TextButton(onClick = { deleteSelected() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } }
        )
    }

    if (showAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showAlbumDialog = false },
            title = { Text(if (albumDialogMode == AlbumDialogMode.CREATE) "Create album" else "Add to album") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = albumName,
                        onValueChange = { albumName = it },
                        singleLine = true,
                        label = { Text("Album name") },
                        placeholder = { Text("e.g. Vacation 2026") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (albumDialogMode == AlbumDialogMode.ADD && existingAlbums.isNotEmpty()) {
                        Text("Existing albums", style = MaterialTheme.typography.labelLarge)
                        existingAlbums.take(8).forEach { existing ->
                            TextButton(onClick = { albumName = existing }, modifier = Modifier.fillMaxWidth()) {
                                Text(existing, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        Text("Album folders require Android 10 or newer.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = albumName.isNotBlank() && selectedUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                    onClick = {
                        val targetName = albumName.trim()
                        coroutineScope.launch {
                            isBusy = true
                            try {
                                val moved = mediaRepository.moveToAlbum(selectedItems(), targetName)
                                selectedUris = emptySet()
                                showAlbumDialog = false
                                reload()
                                statusMessage = "$moved items added to $targetName"
                            } catch (t: Throwable) {
                                statusMessage = t.message ?: "Album operation failed"
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                ) { Text(if (albumDialogMode == AlbumDialogMode.CREATE) "Create & add" else "Add") }
            },
            dismissButton = { TextButton(onClick = { showAlbumDialog = false }) { Text("Cancel") } }
        )
    }
}

private enum class AlbumDialogMode { CREATE, ADD }

@Composable
private fun BulkActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun MediaItemThumbnail(
    mediaItem: MediaItem,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        val request = ImageRequest.Builder(context)
            .data(mediaItem.uri)
            .crossfade(true)
            .apply { if (mediaItem.isVideo) videoFrameMillis(1000) }
            .build()
        AsyncImage(
            model = request,
            contentDescription = mediaItem.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (selected) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp)
            )
        }

        if (mediaItem.isVideo) {
            Text(
                text = "▶",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.55f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}
