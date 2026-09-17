package com.example.core.navigation

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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.ai.ui.AIToolsScreen
import com.example.camera.CameraProScreen2
import com.example.home.HomeScreen
import com.example.media.LibraryScreen
import com.example.media.MediaDetailScreen
import com.example.photoeditor.PhotoEditorScreen
import com.example.projects.ProjectsPlaceholder
import com.example.settings.SettingsScreen
import com.example.update.ui.UpdateScreen
import androidx.compose.ui.res.stringResource

@Composable
fun AppNavGraph(startDestination: Screen = Screen.Home) {
    val navController = rememberNavController()
    Scaffold(bottomBar = { AppBottomNavigation(navController) }) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Screen.Home> { HomeScreen(navController) }
            composable<Screen.Camera> {
                CameraProScreen2 { _ ->
                    // Captures remain inside the camera screen. The saved media is available in the Library.
                }
            }
            composable<Screen.Library> {
                LibraryScreen(
                    onNavigateToMediaDetail = { navController.navigate(Screen.MediaDetail(it)) },
                    onBack = { navController.navigateUp() }
                )
            }
            composable<Screen.MediaDetail> { backStackEntry ->
                val detail = backStackEntry.toRoute<Screen.MediaDetail>()
                MediaDetailScreen(
                    uriString = detail.uriString,
                    onBack = { navController.navigateUp() },
                    onEditPhoto = { navController.navigate(Screen.PhotoEditor(it)) },
                    onEditVideo = { navController.navigate(Screen.VideoEditor(it)) }
                )
            }
            composable<Screen.PhotoEditor> { backStackEntry ->
                val editor = backStackEntry.toRoute<Screen.PhotoEditor>()
                PhotoEditorScreen(
                    uriString = editor.uriString,
                    onBack = { navController.navigateUp() },
                    onExported = { navController.navigateUp() }
                )
            }
            composable<Screen.VideoEditor> { backStackEntry ->
                val editor = backStackEntry.toRoute<Screen.VideoEditor>()
                com.example.videoeditor.ui.VideoEditorScreen(
                    uriString = editor.uriString,
                    onBack = { navController.navigateUp() },
                    onExported = { navController.navigateUp() }
                )
            }
            composable<Screen.AITools> { AIToolsScreen { navController.popBackStack() } }
            composable<Screen.Projects> { ProjectsPlaceholder() }
            composable<Screen.Update> { UpdateScreen { navController.navigateUp() } }
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
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val items = listOf(
        Triple(Screen.Home, stringResource(R.string.nav_home), Icons.Default.Home),
        Triple(Screen.Library, stringResource(R.string.nav_library), Icons.Default.PhotoLibrary),
        Triple(Screen.AITools, stringResource(R.string.nav_ai_tools), Icons.Default.AutoAwesome),
        Triple(Screen.Projects, stringResource(R.string.nav_projects), Icons.Default.Folder)
    )
    val topLevel = route?.contains("Home") == true || route?.contains("Library") == true ||
        route?.contains("AITools") == true || route?.contains("Projects") == true
    if (topLevel) {
        NavigationBar {
            items.forEach { (screen, title, icon) ->
                NavigationBarItem(
                    icon = { Icon(icon, contentDescription = title) },
                    label = { Text(title) },
                    selected = route?.contains(screen::class.simpleName ?: "") == true,
                    onClick = {
                        navController.navigate(screen) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}
