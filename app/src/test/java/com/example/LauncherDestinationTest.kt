package com.example

import com.example.core.navigation.Screen
import com.example.core.navigation.resolveLauncherDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherDestinationTest {
    @Test
    fun cameraLauncherAliasOpensCamera() {
        assertEquals(
            Screen.Camera,
            resolveLauncherDestination("com.example.CameraLauncherAlias", showUpdates = false)
        )
    }

    @Test
    fun galleryLauncherAliasOpensLibrary() {
        assertEquals(
            Screen.Library,
            resolveLauncherDestination("com.example.GalleryLauncherAlias", showUpdates = false)
        )
    }

    @Test
    fun updateIntentOpensUpdateScreen() {
        assertEquals(
            Screen.Update,
            resolveLauncherDestination("com.example.MainActivity", showUpdates = true)
        )
    }

    @Test
    fun ordinaryLaunchOpensHome() {
        assertEquals(
            Screen.Home,
            resolveLauncherDestination("com.example.MainActivity", showUpdates = false)
        )
    }
}
