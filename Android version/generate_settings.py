import os

settings_screen_kt = """package com.example.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.AppTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.GlassSurface

@Composable
fun SettingsScreen(
    onNavigateToUpdate: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(id = R.string.nav_settings),
                onBack = onBack
            )
        }
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
                        subtitle = "Dark"
                    )
                    SettingsRow(
                        title = stringResource(id = R.string.settings_storage),
                        icon = Icons.Default.Storage,
                        subtitle = "1.2 GB used"
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
                        subtitle = "Cloud & On-device"
                    )
                    SettingsRow(
                        title = "Clear AI Cache",
                        icon = Icons.Default.DeleteOutline,
                        subtitle = "Free up space"
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
                        onClick = onNavigateToUpdate
                    )
                    SettingsRow(
                        title = stringResource(id = R.string.settings_about),
                        icon = Icons.Default.Info,
                        subtitle = "Version 1.0.0"
                    )
                }
            }
        }
    }
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
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 12.dp),
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
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
"""

with open("app/src/main/java/com/example/settings/SettingsScreen.kt", "w") as f:
    f.write(settings_screen_kt)
