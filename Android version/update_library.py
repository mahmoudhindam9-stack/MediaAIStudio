import re

with open("app/src/main/java/com/example/media/LibraryScreen.kt", "r") as f:
    text = f.read()

# Add imports for AppTopBar and EmptyState
text = text.replace('import com.example.core.permission.PermissionManagerImpl', 'import com.example.core.permission.PermissionManagerImpl\nimport com.example.ui.components.AppTopBar\nimport com.example.ui.components.EmptyState')

# Replace TopAppBar with AppTopBar
text = re.sub(
    r'TopAppBar\(\s*title = \{ Text\(stringResource\(id = R\.string\.nav_library\)\) \}\s*\)',
    r'AppTopBar(title = stringResource(id = R.string.nav_library), onBack = onBack)',
    text
)

# Replace Empty State
text = re.sub(
    r'Text\(\s*text = stringResource\(id = R\.string\.state_empty\),\s*modifier = Modifier\.align\(Alignment\.Center\)\s*\)',
    r'EmptyState(title = stringResource(id = R.string.state_empty), description = "Import photos or videos to begin editing.", modifier = Modifier.align(Alignment.Center))',
    text
)

with open("app/src/main/java/com/example/media/LibraryScreen.kt", "w") as f:
    f.write(text)
