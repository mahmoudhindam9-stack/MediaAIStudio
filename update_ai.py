import os

ai_screen_kt = """package com.example.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.core.AIProviderType
import com.example.ai.model.AICapability
import com.example.ai.model.AICapabilityStatus
import com.example.ai.model.AppModelManager
import com.example.ai.provider.AIProviderManager
import com.example.ui.components.AppTopBar
import com.example.ui.components.GlassSurface
import com.example.ui.components.SectionHeader

@Composable
fun AIToolsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val providerManager = remember { AIProviderManager(context) }
    val modelManager = remember { AppModelManager() }
    
    val isLocalAvailable = remember { providerManager.getProvider(AIProviderType.ON_DEVICE).isAvailable }
    val isCloudAvailable = remember { providerManager.getProvider(AIProviderType.CLOUD).isAvailable }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.ai_tools),
                onBack = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(title = "AI Providers")
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProviderBadge(name = "On-Device", isAvailable = isLocalAvailable)
                        ProviderBadge(name = "Cloud", isAvailable = isCloudAvailable)
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "AI Capabilities")
            }

            val tools = listOf(
                Triple(R.string.ai_background_removal, AICapability.BACKGROUND_REMOVAL, Icons.Default.PersonRemove),
                Triple(R.string.ai_object_detection, AICapability.OBJECT_DETECTION, Icons.Default.Search),
                Triple(R.string.ai_object_removal, AICapability.OBJECT_REMOVAL, Icons.Default.DeleteSweep),
                Triple(R.string.ai_upscale, AICapability.UPSCALE, Icons.Default.ZoomIn),
                Triple(R.string.ai_enhance, AICapability.ENHANCEMENT, Icons.Default.AutoFixHigh),
                Triple(R.string.ai_restyle, AICapability.RESTYLE, Icons.Default.Brush),
                Triple(R.string.ai_gen_fill, AICapability.GENERATIVE_FILL, Icons.Default.FormatPaint),
                Triple(R.string.ai_img_to_img, AICapability.IMAGE_TO_IMAGE, Icons.Default.Transform)
            )
            
            items(tools.size) { index ->
                val (titleRes, capability, icon) = tools[index]
                val status = modelManager.getCapabilityStatus(capability)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(stringResource(titleRes), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        }
                        
                        val (statusText, statusColor) = when (status) {
                            AICapabilityStatus.AVAILABLE -> "Ready" to MaterialTheme.colorScheme.tertiary
                            AICapabilityStatus.UNAVAILABLE -> "Unavailable" to MaterialTheme.colorScheme.error
                            AICapabilityStatus.REQUIRES_MODEL -> "Download Required" to MaterialTheme.colorScheme.secondary
                            AICapabilityStatus.REQUIRES_CLOUD -> "Cloud Only" to MaterialTheme.colorScheme.primary
                            AICapabilityStatus.DEVICE_NOT_SUPPORTED -> "Not Supported" to MaterialTheme.colorScheme.error
                            AICapabilityStatus.REQUIRES_PROVIDER -> "Setup Required" to MaterialTheme.colorScheme.error
                            AICapabilityStatus.PROCESSING -> "Processing..." to MaterialTheme.colorScheme.primary
                            AICapabilityStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
                            AICapabilityStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        
                        Surface(
                            color = statusColor.copy(alpha = 0.2f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = statusText,
                                color = statusColor,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderBadge(name: String, isAvailable: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (isAvailable) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (isAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}
"""

with open("app/src/main/java/com/example/ai/ui/AIToolsScreen.kt", "w") as f:
    f.write(ai_screen_kt)
