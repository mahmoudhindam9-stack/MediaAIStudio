import os
import re

path = "app/src/main/java/com/example/update/ui/UpdateScreen.kt"
with open(path, "r") as f:
    content = f.read()

# Replace any lingering instance of arrayOf(updateState.versionName) or just updateState.versionName for version_info
content = re.sub(r'stringResource\(\s*id\s*=\s*R\.string\.version_info\s*,\s*(formatArgs\s*=\s*)?arrayOf\([^)]+\)\s*\)', 'stringResource(id = R.string.version_info, updateState.versionName ?: "")', content)
content = re.sub(r'stringResource\(\s*R\.string\.version_info\s*,\s*updateState\.versionName\s*\)', 'stringResource(R.string.version_info, updateState.versionName ?: "")', content)

with open(path, "w") as f:
    f.write(content)
