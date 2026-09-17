package com.example.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable as runtimeRememberSaveable

/**
 * Local bridge for ProjectsScreen's unqualified rememberSaveable calls.
 * Delegates to the real Compose saveable implementation so state remains
 * available across configuration changes and process recreation when supported.
 */
@Composable
fun <T> rememberSaveable(init: () -> T): MutableState<T> =
    runtimeRememberSaveable { mutableStateOf(init()) }
