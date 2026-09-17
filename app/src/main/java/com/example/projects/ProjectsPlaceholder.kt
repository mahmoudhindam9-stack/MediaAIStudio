package com.example.projects

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.core.navigation.Screen
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassSurface
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class ProjectSort {
    RECENT,
    NAME,
    TYPE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { ProjectRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var projects by remember { mutableStateOf(repository.getProjects()) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProjectSort.RECENT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameProject by remember { mutableStateOf<MediaProject?>(null) }
    var deleteProject by remember { mutableStateOf<MediaProject?>(null) }
    var projectWaitingForMedia by remember { mutableStateOf<String?>(null) }
    var pickerMime by remember { mutableStateOf("*/*") }

    fun refresh() {
        projects = repository.getProjects()
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val projectId = projectWaitingForMedia
        projectWaitingForMedia = null
        if (uri != null && projectId != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val type = if (pickerMime.startsWith("video/")) MediaProjectType.VIDEO else MediaProjectType.PHOTO
            repository.updateSource(projectId, uri.toString(), type)
            refresh()
            when (type) {
                MediaProjectType.PHOTO -> navController.navigate(Screen.PhotoEditor(uri.toString()))
                MediaProjectType.VIDEO -> navController.navigate(Screen.VideoEditor(uri.toString()))
            }
        }
    }

    fun pickMediaFor(project: MediaProject) {
        projectWaitingForMedia = project.id
        pickerMime = if (project.mediaType == MediaProjectType.VIDEO) "video/*" else "image/*"
        mediaPicker.launch(arrayOf(pickerMime))
    }

    fun openProject(project: MediaProject) {
        val sourceUri = project.sourceUri
        if (sourceUri.isNullOrBlank()) {
            pickMediaFor(project)
            return
        }
        when (project.mediaType) {
            MediaProjectType.PHOTO -> navController.navigate(Screen.PhotoEditor(sourceUri))
            MediaProjectType.VIDEO -> navController.navigate(Screen.VideoEditor(sourceUri))
        }
    }

    val visibleProjects = remember(projects, query, sort) {
        val filtered = projects.filter { it.name.contains(query.trim(), ignoreCase = true) }
        when (sort) {
            ProjectSort.RECENT -> filtered.sortedByDescending { it.updatedAt }
            ProjectSort.NAME -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            ProjectSort.TYPE -> filtered.sortedWith(
                compareBy<MediaProject> { it.mediaType.name }.thenByDescending { it.updatedAt }
            )
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Projects") },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New project")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
        ) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text("Search projects") },
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort projects")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Recent") },
                                    onClick = { sort = ProjectSort.RECENT; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name") },
                                    onClick = { sort = ProjectSort.NAME; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Type") },
                                    onClick = { sort = ProjectSort.TYPE; showSortMenu = false }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Your projects", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${visibleProjects.size} project${if (visibleProjects.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Project")
                    }
                }
            }

            if (visibleProjects.isEmpty()) {
                item {
                    EmptyState(
                        title = if (query.isBlank()) "No projects yet" else "No matching projects",
                        description = if (query.isBlank()) {
                            "Create a project, choose your photo or video, and continue editing from here."
                        } else {
                            "Try another project name."
                        },
                        actionText = if (query.isBlank()) "Create Project" else null,
                        onAction = if (query.isBlank()) ({ showCreateDialog = true }) else null
                    )
                }
            } else {
                items(visibleProjects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { openProject(project) },
                        onAddMedia = { pickMediaFor(project) },
                        onRename = { renameProject = project },
                        onDuplicate = {
                            repository.duplicateProject(project.id)
                            refresh()
                            scope.launch { snackbarHostState.showSnackbar("Project duplicated") }
                        },
                        onDelete = { deleteProject = project }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type ->
                val created = repository.createProject(name, type)
                refresh()
                showCreateDialog = false
                pickMediaFor(created)
            }
        )
    }

    renameProject?.let { project ->
        RenameProjectDialog(
            project = project,
            onDismiss = { renameProject = null },
            onRename = { newName ->
                repository.renameProject(project.id, newName)
                refresh()
                renameProject = null
            }
        )
    }

    deleteProject?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteProject = null },
            title = { Text("Delete project?") },
            text = { Text("\"${project.name}\" will be removed from Projects. Your original photo or video will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.deleteProject(project.id)
                        refresh()
                        deleteProject = null
                        scope.launch { snackbarHostState.showSnackbar("Project deleted") }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteProject = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProjectCard(
    project: MediaProject,
    onOpen: () -> Unit,
    onAddMedia: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isVideo = project.mediaType == MediaProjectType.VIDEO
    val sourceUri = project.sourceUri?.let(Uri::parse)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (sourceUri != null) {
                    AsyncImage(
                        model = sourceUri,
                        contentDescription = project.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isVideo) "VIDEO" else "PHOTO",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Project options", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = { menuExpanded = false; onDuplicate() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Edited ${formatDate(project.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (sourceUri == null) "Add Media" else "Open")
                    }
                    if (sourceUri != null) {
                        OutlinedButton(
                            onClick = onAddMedia,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Replace media", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, MediaProjectType) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by remember { mutableStateOf(MediaProjectType.VIDEO) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Project name") },
                    placeholder = { Text("My new project") },
                    singleLine = true
                )
                Text("Project type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == MediaProjectType.VIDEO,
                        onClick = { type = MediaProjectType.VIDEO },
                        label = { Text("Video") },
                        leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = type == MediaProjectType.PHOTO,
                        onClick = { type = MediaProjectType.PHOTO },
                        label = { Text("Photo") },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, type) }) {
                Text("Create & Choose Media")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RenameProjectDialog(
    project: MediaProject,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember(project.id) { mutableStateOf(project.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename project") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Project name") }
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onRename(name) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown date"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
}
