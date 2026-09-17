package com.example.ai.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.assistant.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    assistant: AIAssistant,
    contextBuilder: () -> AssistantContext,
    onExecutePlan: suspend (AssistantActionPlan) -> AssistantResult,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AssistantResult?>(null) }
    var isPlanning by remember { mutableStateOf(false) }
    var isExecuting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI Assistant", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .defaultMinSize(minHeight = 100.dp)
        ) {
            when {
                isPlanning -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                isExecuting -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Executing plan...")
                    }
                }
                result != null -> {
                    when (val res = result) {
                        is AssistantResult.Planned -> {
                            PlanView(
                                plan = res.plan,
                                onExecute = {
                                    scope.launch {
                                        isExecuting = true
                                        result = onExecutePlan(res.plan)
                                        isExecuting = false
                                    }
                                },
                                onCancel = { result = null }
                            )
                        }
                        is AssistantResult.NeedsConfirmation -> {
                            PlanView(
                                plan = res.plan,
                                onExecute = {
                                    scope.launch {
                                        isExecuting = true
                                        result = onExecutePlan(res.plan)
                                        isExecuting = false
                                    }
                                },
                                onCancel = { result = null }
                            )
                        }
                        is AssistantResult.Executed -> {
                            Column {
                                Text("Execution successful!", color = Color.Green, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = onDismiss) {
                                    Text("Done")
                                }
                            }
                        }
                        is AssistantResult.Unsupported -> {
                            Column {
                                Text("Unsupported Request", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                Text(res.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { result = null }) {
                                    Text("Try Again")
                                }
                            }
                        }
                        is AssistantResult.Failed -> {
                            Column {
                                Text("Execution Failed", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                Text(res.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { result = null }) {
                                    Text("Try Again")
                                }
                            }
                        }
                        null -> {}
                    }
                }
                else -> {
                    Text("How can I help you edit this media?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. Make it brighter and warmer") },
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (prompt.isNotBlank() && !isPlanning && !isExecuting) {
                            scope.launch {
                                isPlanning = true
                                result = assistant.plan(prompt, contextBuilder())
                                isPlanning = false
                            }
                        }
                    },
                    enabled = prompt.isNotBlank() && !isPlanning && !isExecuting
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            },
            singleLine = true
        )
    }
}

@Composable
private fun PlanView(
    plan: AssistantActionPlan,
    onExecute: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("I can help with that.", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Plan:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            items(plan.actions) { action ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(formatAction(action))
                }
            }
        }
        
        if (plan.requiresConfirmation) {
            Text("This plan includes destructive or AI actions and requires confirmation.", color = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onExecute) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Apply changes")
            }
        }
    }
}

private fun formatAction(action: AssistantAction): String {
    return when (action) {
        is AssistantAction.AdjustBrightness -> "Adjust brightness by ${action.amount}"
        is AssistantAction.AdjustContrast -> "Adjust contrast by ${action.amount}"
        is AssistantAction.AdjustSaturation -> "Adjust saturation by ${action.amount}"
        is AssistantAction.AdjustTemperature -> "Adjust temperature by ${action.amount}"
        is AssistantAction.ApplyFilter -> "Apply filter ${action.filterId}"
        AssistantAction.RemoveBackground -> "Remove background"
        AssistantAction.EnhanceImage -> "Enhance image"
        AssistantAction.UpscaleImage -> "Upscale image"
        AssistantAction.Undo -> "Undo"
        AssistantAction.Redo -> "Redo"
        AssistantAction.ExportMedia -> "Export media"
        is AssistantAction.Unknown -> "Unknown action: ${action.rawPrompt}"
    }
}
