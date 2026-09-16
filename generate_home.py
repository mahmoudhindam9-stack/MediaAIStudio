import os

home_screen_kt = """package com.example.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.R
import com.example.core.navigation.Screen
import com.example.ui.components.ActionCard
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassSurface
import com.example.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings) }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Settings", modifier = Modifier.size(32.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = 32.dp)
        ) {
            
            // Hero / Recent Project
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                GlassSurface {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Summer Vacation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Edited 2 hours ago", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { navController.navigate(Screen.Projects) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Continue Editing")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Quick Create")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ActionCard(
                    title = "Camera",
                    icon = Icons.Default.CameraAlt,
                    onClick = { navController.navigate(Screen.Camera) },
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    title = "Photo",
                    icon = Icons.Default.Image,
                    onClick = { navController.navigate(Screen.Library) },
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    title = "Video",
                    icon = Icons.Default.Movie,
                    onClick = { navController.navigate(Screen.Library) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "AI Studio")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ActionCard(
                    title = "Remove BG",
                    icon = Icons.Default.PersonRemove,
                    onClick = { navController.navigate(Screen.AITools) },
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.secondary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
                ActionCard(
                    title = "Enhance",
                    icon = Icons.Default.AutoFixHigh,
                    onClick = { navController.navigate(Screen.AITools) },
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.secondary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
                ActionCard(
                    title = "Transform",
                    icon = Icons.Default.AutoAwesome,
                    onClick = { navController.navigate(Screen.AITools) },
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.secondary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = stringResource(id = R.string.recent_media))
            EmptyState(
                title = "No recent media",
                description = "Start by importing a video or photo.",
                actionText = "Open Library",
                onAction = { navController.navigate(Screen.Library) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(title = stringResource(id = R.string.recent_projects))
            EmptyState(
                title = "No projects yet",
                description = "Create your first project to get started.",
                actionText = "Create Project",
                onAction = { navController.navigate(Screen.Projects) }
            )
        }
    }
}
"""

with open("app/src/main/java/com/example/home/HomeScreen.kt", "w") as f:
    f.write(home_screen_kt)
