import os

path = "app/src/main/java/com/example/MainActivity.kt"
with open(path, "r") as f:
    content = f.read()

replacement = """
        val startDestination: com.example.core.navigation.Screen = if (intent.getBooleanExtra("SHOW_UPDATES", false)) {
            com.example.core.navigation.Screen.Update
        } else {
            com.example.core.navigation.Screen.Home
        }

        setContent {
            MediaAIStudioTheme {
                // We'd need to pass startDestination to AppNavGraph, but for simplicity, 
                // we can just stick to default and let users navigate. 
                // To do it properly, let's update AppNavGraph to accept startDestination
                com.example.core.navigation.AppNavGraph(startDestination)
            }
        }
"""
content = content.replace("setContent {", replacement.split("setContent {")[0] + "setContent {")

with open(path, "w") as f:
    f.write(content)

path_nav = "app/src/main/java/com/example/core/navigation/NavGraph.kt"
with open(path_nav, "r") as f:
    content = f.read()

content = content.replace("fun AppNavGraph() {", "fun AppNavGraph(startDestination: Screen = Screen.Home) {")
content = content.replace("startDestination = Screen.Home,", "startDestination = startDestination,")

with open(path_nav, "w") as f:
    f.write(content)

