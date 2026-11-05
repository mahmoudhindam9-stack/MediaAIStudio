package com.example.core.navigation

/**
 * Resolves the app's initial destination for launcher shortcuts and update intents.
 * Kept free of Android dependencies so shortcut routing can be regression-tested.
 */
fun resolveLauncherDestination(componentName: String, showUpdates: Boolean): Screen = when {
    componentName.endsWith(".CameraLauncherAlias") -> Screen.Camera
    componentName.endsWith(".GalleryLauncherAlias") -> Screen.Library
    showUpdates -> Screen.Update
    else -> Screen.Home
}
