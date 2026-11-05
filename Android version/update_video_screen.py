import os

path = "app/src/main/java/com/example/videoeditor/ui/VideoEditorScreen.kt"
with open(path, "r") as f:
    content = f.read()

if "AI Tools" not in content:
    # We need to add a bottom sheet or a dialog or just a row for AI Tools.
    # Let's add an AI button to the EditorToolbar and a dialog for AI operations.
    
    imports = """
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest
"""
    content = content.replace("import androidx.compose.runtime.*", "import androidx.compose.runtime.*\n" + imports)
    
    # Add AI Dialog state and Snackbar to VideoEditorScreen
    screen_top = """
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
"""
    new_screen_top = """
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
"""
    
    body = """
    val state by viewModel.state.collectAsState()
"""
    new_body = """
    val state by viewModel.state.collectAsState()
    val showAiDialog = remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(Unit) {
        viewModel.aiMessages.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    
    if (showAiDialog.value) {
        AlertDialog(
            onDismissRequest = { showAiDialog.value = false },
            title = { Text("AI Video Tools") },
            text = {
                Column {
                    TextButton(onClick = { viewModel.runSmartCut(); showAiDialog.value = false }) { Text("Smart Cut") }
                    TextButton(onClick = { viewModel.runAutoCaptions(); showAiDialog.value = false }) { Text("Auto Captions") }
                    TextButton(onClick = { viewModel.runObjectTracking(); showAiDialog.value = false }) { Text("Object Tracking") }
                    TextButton(onClick = { viewModel.runSmartReframe(); showAiDialog.value = false }) { Text("Smart Reframe") }
                    TextButton(onClick = { viewModel.runEnhancement(); showAiDialog.value = false }) { Text("Enhance") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiDialog.value = false }) { Text("Close") }
            }
        )
    }
    
    if (state.aiSuggestedCuts != null) {
        AlertDialog(
            onDismissRequest = { viewModel.rejectSmartCuts() },
            title = { Text("Review Smart Cuts") },
            text = { Text("AI has suggested ${state.aiSuggestedCuts!!.size} cuts based on structural analysis. Apply these changes?") },
            confirmButton = { TextButton(onClick = { viewModel.applySmartCuts() }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { viewModel.rejectSmartCuts() }) { Text("Reject") } }
        )
    }
"""
    content = content.replace(body, new_body)
    
    # Add SnackbarHost to Scaffold
    content = content.replace(
        "containerColor = Color.Black,",
        "containerColor = Color.Black,\n        snackbarHost = { SnackbarHost(snackbarHostState) },"
    )
    
    # Add AI button to EditorToolbar
    toolbar_func = """fun EditorToolbar(viewModel: VideoEditorViewModel, state: VideoEditorState, onRecordVoiceOver: () -> Unit)"""
    new_toolbar_func = """fun EditorToolbar(viewModel: VideoEditorViewModel, state: VideoEditorState, onRecordVoiceOver: () -> Unit, onAiClick: () -> Unit)"""
    content = content.replace(toolbar_func, new_toolbar_func)
    
    # Actually finding the call site in VideoEditorScreen:
    content = content.replace(
        "onRecordVoiceOver = {",
        "onAiClick = { showAiDialog.value = true },\n                 onRecordVoiceOver = {"
    )
    
    # Add the Icon to EditorToolbar layout
    toolbar_icons = """
        IconButton(onClick = { viewModel.deleteSelectedClip() }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = if (state.selectedItemId != null) Color.White else Color.Gray)
        }
"""
    new_toolbar_icons = toolbar_icons + """
        IconButton(onClick = onAiClick) {
            Icon(Icons.Default.AutoAwesome, contentDescription = "AI Tools", tint = Color.Cyan)
        }
"""
    content = content.replace(toolbar_icons, new_toolbar_icons)
    
    with open(path, "w") as f:
        f.write(content)
