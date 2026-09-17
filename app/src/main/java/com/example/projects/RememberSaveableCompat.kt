package com.example.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable as runtimeRememberSaveable

/**
 * Local bridge for ProjectsScreen's unqualified rememberSaveable calls.
 * Delegates directly to Compose's real saveable implementation.
 */
@Composable
fun <T : Any?> rememberSaveable(init: () -> T): T =
    runtimeRememberSaveable { init() }
