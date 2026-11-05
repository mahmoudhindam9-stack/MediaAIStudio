package com.example.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.core.AIProviderType
import com.example.ui.components.AppTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.GlassSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(
    onNavigateToUpdate: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsPreferences = remember { SettingsPreferences(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val themeMode by settingsPreferences.themeMode.collectAsState(initial = AppTheme.DARK)
    val aiMode by settingsPreferences.aiMode.collectAsState(initial = AIProviderType.AUTO)

    var showThemeDialog by remember { mutableStateOf(false) }
    var showAiModeDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    var storageUsedStr by remember { mutableStateOf("Calculating...") }
    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "Unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

    LaunchedEffect(Unit) {
        val size = withContext(Dispatchers.IO) {
            getDirectorySize(context.filesDir) + getDirectorySize(context.cacheDir) + getDirectorySize(context.noBackupFilesDir)
        }
        storageUsedStr = "${formatSize(size)} used"
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(id = R.string.nav_settings),
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                SectionHeader(title = "General")
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(id = R.string.settings_theme),
                        icon = Icons.Default.Palette,
                        subtitle = when (themeMode) {
                            AppTheme.SYSTEM -> "System default"
                            AppTheme.LIGHT -> "Light"
                            AppTheme.DARK -> "Dark"
                        },
                        onClick = { showThemeDialog = true }
                    )
                    SettingsRow(
                        title = stringResource(id = R.string.settings_storage),
                        icon = Icons.Default.Storage,
                        subtitle = storageUsedStr,
                        onClick = null
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "AI & Processing")
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(id = R.string.settings_ai),
                        icon = Icons.Default.AutoAwesome,
                        subtitle = when (aiMode) {
                            AIProviderType.AUTO -> "Auto (Cloud & On-device)"
                            AIProviderType.ON_DEVICE -> "On-device only"
                            AIProviderType.CLOUD -> "Cloud only"
                        },
                        onClick = { showAiModeDialog = true }
                    )
                    SettingsRow(
                        title = "Clear AI Cache",
                        icon = Icons.Default.DeleteOutline,
                        subtitle = "Free up space used by AI models",
                        onClick = { showClearCacheDialog = true }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "Audio")
                SettingsGroup {
                    SettingsRow(
                        title = "Audio Settings",
                        icon = Icons.Default.Audiotrack,
                        subtitle = "Optimized automatically"
                    )
                    SettingsRow(
                        title = "Voice-over Settings",
                        icon = Icons.Default.Mic,
                        subtitle = "Managed by audio engine"
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "App")
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(id = R.string.settings_updates),
                        icon = Icons.Default.SystemUpdate,
                        onClick = onNavigateToUpdate,
                        showChevron = true
                    )
                    SettingsRow(
                        title = stringResource(id = R.string.settings_about),
                        icon = Icons.Default.Info,
                        subtitle = "MediaAIStudio Version $appVersion"
                    )
                }
            }
        }

        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentTheme = themeMode,
                onDismiss = { showThemeDialog = false },
                onSelect = { selectedTheme ->
                    coroutineScope.launch {
                        settingsPreferences.setThemeMode(selectedTheme)
                    }
                    showThemeDialog = false
                }
            )
        }

        if (showAiModeDialog) {
            AiModeSelectionDialog(
                currentMode = aiMode,
                onDismiss = { showAiModeDialog = false },
                onSelect = { selectedMode ->
                    coroutineScope.launch {
                        settingsPreferences.setAiMode(selectedMode)
                    }
                    showAiModeDialog = false
                }
            )
        }

        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text("Clear AI Cache") },
                text = { Text("This will delete temporary AI files. Your projects, photos, and videos will not be deleted.") },
                confirmButton = {
                    Button(onClick = {
                        coroutineScope.launch {
                            val bytes = withContext(Dispatchers.IO) {
                                clearAiCache(context)
                            }
                            snackbarHostState.showSnackbar("AI cache cleared — ${formatSize(bytes)} freed")
                        }
                        showClearCacheDialog = false
                    }) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onDismiss: () -> Unit,
    onSelect: (AppTheme) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            Column {
                AppTheme.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(theme) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = { onSelect(theme) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(when (theme) {
                            AppTheme.SYSTEM -> "System default"
                            AppTheme.LIGHT -> "Light"
                            AppTheme.DARK -> "Dark"
                        })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AiModeSelectionDialog(
    currentMode: AIProviderType,
    onDismiss: () -> Unit,
    onSelect: (AIProviderType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Processing Mode") },
        text = {
            Column {
                AIProviderType.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = { onSelect(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(when (mode) {
                            AIProviderType.AUTO -> "Auto (Cloud & On-device)"
                            AIProviderType.ON_DEVICE -> "On-device only"
                            AIProviderType.CLOUD -> "Cloud only"
                        })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsRow(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showChevron || onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getDirectorySize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0
    var size = 0L
    val files = dir.listFiles()
    if (files != null) {
        for (f in files) {
            if (f.isDirectory) {
                size += getDirectorySize(f)
            } else {
                size += f.length()
            }
        }
    }
    return size
}

private fun clearAiCache(context: Context): Long {
    var clearedBytes = 0L
    val files = context.cacheDir.listFiles()
    if (files != null) {
        for (f in files) {
            if (f.name.startsWith("ai_")) {
                clearedBytes += f.length()
                f.delete()
            }
        }
    }
    return clearedBytes
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
