import os

path_nav = "app/src/main/java/com/example/core/navigation/NavGraph.kt"
with open(path_nav, "r") as f:
    content = f.read()

content = content.replace(
"""                SettingsScreen(
                    onBack = { navController.navigateUp() }
                )""",
"""                SettingsScreen(
                    onNavigateToUpdate = { navController.navigate(Screen.Update) },
                    onBack = { navController.navigateUp() }
                )""")
with open(path_nav, "w") as f:
    f.write(content)
