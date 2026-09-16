import re

with open("app/src/main/java/com/example/photoeditor/PhotoEditorScreen.kt", "r") as f:
    text = f.read()

text = text.replace('containerColor = Color.Black', 'containerColor = MaterialTheme.colorScheme.background')
text = text.replace('navigationIconContentColor = Color.White', 'navigationIconContentColor = MaterialTheme.colorScheme.onBackground')
text = text.replace('actionIconContentColor = Color.White', 'actionIconContentColor = MaterialTheme.colorScheme.onBackground')
text = text.replace('.background(Color.Black)', '.background(MaterialTheme.colorScheme.background)')
text = text.replace('contentColor = Color.White', 'contentColor = MaterialTheme.colorScheme.onBackground')
# We'll leave Color.White for drawing colors or text tools, only replace structural colors if needed.

with open("app/src/main/java/com/example/photoeditor/PhotoEditorScreen.kt", "w") as f:
    f.write(text)
