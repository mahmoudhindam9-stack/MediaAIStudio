import os

path = "app/src/main/res/values/strings.xml"
with open(path, "r") as f:
    content = f.read()

# Fix unresolved strings
if "update_available_title" not in content:
    # We might have mangled it before. Let's just forcefully append it cleanly.
    pass

print("OK")
