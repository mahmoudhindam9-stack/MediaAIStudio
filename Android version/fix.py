import re
with open("app/src/main/java/com/example/update/ui/UpdateScreen.kt", "r") as f:
    text = f.read()

# Fix the exact two lines found
text = text.replace('stringResource(com.example.R.string.version_info, currentVersion)', 'stringResource(com.example.R.string.version_info, currentVersion ?: "Unknown")')
text = text.replace('stringResource(com.example.R.string.version_info, release.versionName)', 'stringResource(com.example.R.string.version_info, release.versionName ?: "Unknown")')

with open("app/src/main/java/com/example/update/ui/UpdateScreen.kt", "w") as f:
    f.write(text)
