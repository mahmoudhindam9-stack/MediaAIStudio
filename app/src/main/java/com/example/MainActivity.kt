package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.core.navigation.AppNavGraph
import com.example.core.theme.MediaAIStudioTheme

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
            MediaAIStudioTheme {
                AppNavGraph(startDestination)
            }
        }
    }
}
