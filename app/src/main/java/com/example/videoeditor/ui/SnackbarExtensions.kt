package com.example.videoeditor.ui

import androidx.compose.material3.SnackbarHostState

suspend fun SnackbarHostState.showSnackbar(error: Exception) {
    showSnackbar(error.message ?: error.javaClass.simpleName)
}
