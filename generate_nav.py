import os

app_nav_graph = """package com.example.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.home.HomeScreen
import com.example.settings.SettingsScreen
import com.example.update.ui.UpdateScreen
import com.example.camera.CameraScreen
import com.example.media.LibraryScreen
import com.example.media.MediaDetailScreen
import com.example.photoeditor.PhotoEditorScreen
import com.example.ai.ui.AIToolsScreen
import com.example.projects.ProjectsPlaceholder
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun AppNavGraph(startDestination: Screen = Screen.Home) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            AppBottomNavigation(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Screen.Home> {
                HomeScreen(navController)
            }
            composable<Screen.Camera> {
                CameraScreen(
                    onMediaCaptured = { uriString ->
                        navController.navigate(Screen.MediaDetail(uriString))
                    }
                )
            }
            composable<Screen.Library> {
                LibraryScreen(
                    onNavigateToMediaDetail = { uriString ->
                        navController.navigate(Screen.MediaDetail(uriString))
                    },
                    onBack = { navController.navigateUp() }
                )
            }
            composable<Screen.MediaDetail> { backStackEntry ->
                val mediaDetail = backStackEntry.toRoute<Screen.MediaDetail>()
                MediaDetailScreen(
                    uriString = mediaDetail.uriString,
                    onBack = { navController.navigateUp() },
                    onEditPhoto = { uriString ->
                        navController.navigate(Screen.PhotoEditor(uriString))
                    },
                    onEditVideo = { uriString ->
                        navController.navigate(Screen.VideoEditor(uriString))
                    }
                )
            }
            composable<Screen.PhotoEditor> { backStackEntry ->
                val photoEditor = backStackEntry.toRoute<Screen.PhotoEditor>()
                PhotoEditorScreen(
                    uriString = photoEditor.uriString,
                    onBack = { navController.navigateUp() },
                    onExported = { uriString ->
                        navController.navigateUp()
                    }
                )
            }
            composable<Screen.VideoEditor> { backStackEntry ->
                val videoEditor = backStackEntry.toRoute<Screen.VideoEditor>()
                com.example.videoeditor.ui.VideoEditorScreen(
                    uriString = videoEditor.uriString,
                    onBack = { navController.navigateUp() },
                    onExported = { uriString ->
                        navController.navigateUp()
                    }
                )
            }
            composable<Screen.AITools> {
                AIToolsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Screen.Projects> {
                ProjectsPlaceholder()
            }
            
            composable<Screen.Update> {
                UpdateScreen(
                    onBack = { navController.navigateUp() }
                )
            }
            composable<Screen.Settings> {
                SettingsScreen(
                    onNavigateToUpdate = { navController.navigate(Screen.Update) },
                    onBack = { navController.navigateUp() }
                )
            }
        }
    }
}

@Composable
fun AppBottomNavigation(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Define top-level routes
    val items = listOf(
        Triple(Screen.Home, stringResource(R.string.nav_home), Icons.Default.Home),
        Triple(Screen.Library, stringResource(R.string.nav_library), Icons.Default.PhotoLibrary),
        Triple(Screen.AITools, stringResource(R.string.nav_ai_tools), Icons.Default.AutoAwesome),
        Triple(Screen.Projects, stringResource(R.string.nav_projects), Icons.Default.Folder)
    )

    val currentRoute = currentDestination?.route
    val isTopLevel = currentRoute?.contains("Home") == true ||
            currentRoute?.contains("Library") == true ||
            currentRoute?.contains("AITools") == true ||
            currentRoute?.contains("Projects") == true

    if (isTopLevel) {
        NavigationBar {
            items.forEach { (screen, title, icon) ->
                val selected = currentRoute?.contains(screen::class.simpleName ?: "") == true
                NavigationBarItem(
                    icon = { Icon(icon, contentDescription = title) },
                    label = { Text(title) },
                    selected = selected,
                    onClick = {
                        navController.navigate(screen) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}
"""

with open("app/src/main/java/com/example/core/navigation/NavGraph.kt", "w") as f:
    f.write(app_nav_graph)
