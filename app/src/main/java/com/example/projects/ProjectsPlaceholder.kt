package com.example.projects

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyState

@Composable
fun ProjectsPlaceholder() {
    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(id = R.string.nav_projects))
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            EmptyState(
                title = "No projects found",
                description = stringResource(id = R.string.placeholder_not_implemented)
            )
        }
    }
}
