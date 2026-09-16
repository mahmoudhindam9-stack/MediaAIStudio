import re

with open("app/src/main/java/com/example/videoeditor/ui/VideoEditorScreen.kt", "r") as f:
    text = f.read()

# Fix imports
text = text.replace('import com.example.videoeditor.export.VideoExport', 'import com.example.videoeditor.export.VideoExport\nimport com.example.ui.components.AppTopBar\nimport com.example.ui.components.GlassSurface')

text = text.replace('containerColor = Color.Black', 'containerColor = MaterialTheme.colorScheme.background')

topbar_pattern = r'TopAppBar\(\s*title = \{ Text\("Video Editor", color = Color\.White\) \},\s*navigationIcon = \{[^\}]+\}\s*\},(.*?)actions = \{'
# Actually we can just do string replacements for the TopAppBar.
text = re.sub(
    r'TopAppBar\([\s\S]*?actions = \{',
    r'AppTopBar(\n                title = "Video Editor",\n                onBack = { \n                    viewModel.exoPlayer.release()\n                    onBack()\n                },\n                actions = {',
    text
)
# The old TopAppBar closed with `colors = TopAppBarDefaults...`
text = re.sub(
    r'colors = TopAppBarDefaults\.topAppBarColors\(containerColor = Color\.Black\),',
    r'',
    text
)

text = text.replace('background(Color(0xFF222222))', 'background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))')
text = text.replace('background(Color.Black)', 'background(MaterialTheme.colorScheme.background)')
text = text.replace('background(Color.DarkGray)', 'background(MaterialTheme.colorScheme.surface)')
text = text.replace('tint = Color.White', 'tint = MaterialTheme.colorScheme.onBackground')
text = text.replace('color = Color.White', 'color = MaterialTheme.colorScheme.onBackground')

# Timeline styling
text = text.replace('Color.Blue', 'MaterialTheme.colorScheme.primary')
text = text.replace('Color.Yellow', 'MaterialTheme.colorScheme.secondary')
text = text.replace('Color.Green', 'MaterialTheme.colorScheme.tertiary')
text = text.replace('Color.Red', 'MaterialTheme.colorScheme.error')
text = text.replace('Color.Cyan', 'MaterialTheme.colorScheme.secondary')
text = text.replace('Color.Black', 'MaterialTheme.colorScheme.onTertiary') # For audio track text

with open("app/src/main/java/com/example/videoeditor/ui/VideoEditorScreen.kt", "w") as f:
    f.write(text)
