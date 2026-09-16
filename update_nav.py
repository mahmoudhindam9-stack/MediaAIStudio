import os

path_screen = "app/src/main/java/com/example/core/navigation/Screen.kt"
with open(path_screen, "r") as f:
    content = f.read()
if "data object Update : Screen()" not in content:
    content = content.replace("data object Settings : Screen()", "data object Settings : Screen()\n\n    @Serializable\n    data object Update : Screen()")
    with open(path_screen, "w") as f:
        f.write(content)

path_nav = "app/src/main/java/com/example/core/navigation/NavGraph.kt"
with open(path_nav, "r") as f:
    content = f.read()
if "composable<Screen.Update>" not in content:
    content = content.replace("import com.example.settings.SettingsScreen", "import com.example.settings.SettingsScreen\nimport com.example.update.ui.UpdateScreen")
    
    update_composable = """
            composable<Screen.Update> {
                UpdateScreen(
                    onBack = { navController.navigateUp() }
                )
            }
"""
    content = content.replace("composable<Screen.Settings> {", update_composable + "            composable<Screen.Settings> {")
    with open(path_nav, "w") as f:
        f.write(content)

path_settings = "app/src/main/java/com/example/settings/SettingsScreen.kt"
with open(path_settings, "r") as f:
    content = f.read()
if "onNavigateToUpdate: () -> Unit" not in content:
    content = content.replace("fun SettingsScreen(", "fun SettingsScreen(\n    onNavigateToUpdate: () -> Unit,")
    content = content.replace('SettingsItem(\n                    title = stringResource(id = R.string.settings_about),\n                    icon = Icons.Default.Info,\n                    subtitle = stringResource(id = R.string.app_name)\n                ) {}', 
                              'SettingsItem(\n                    title = stringResource(id = R.string.settings_about),\n                    icon = Icons.Default.Info,\n                    subtitle = stringResource(id = R.string.app_name),\n                    onClick = onNavigateToUpdate\n                )')
    with open(path_settings, "w") as f:
        f.write(content)

