package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.core.navigation.AppNavGraph
import com.example.core.theme.MediaAIStudioTheme
import com.example.settings.SettingsPreferences
import com.example.settings.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val startDestination: com.example.core.navigation.Screen = if (intent.getBooleanExtra("SHOW_UPDATES", false)) {
            com.example.core.navigation.Screen.Update
        } else {
            com.example.core.navigation.Screen.Home
        }

        setContent {
            val context = LocalContext.current
            val settingsPreferences = remember { SettingsPreferences(context) }
            val themeMode by settingsPreferences.themeMode.collectAsState(initial = AppTheme.DARK)

            MediaAIStudioTheme(theme = themeMode) {
                AppNavGraph(startDestination)
            }
        }
    }
}
