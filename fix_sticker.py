import os

path = "app/src/main/java/com/example/photoeditor/PhotoEditorScreen.kt"
with open(path, "r") as f:
    content = f.read()

duplicate_sticker = """                "STICKER" -> {
                    LazyRow {
                        items(listOf("😀", "❤️", "🔥", "🌟", "🎉", "✨", "😎", "🐱")) { emoji ->
                            Text(text = emoji, fontSize = 32.sp, modifier = Modifier
                                .padding(8.dp)
                                .clickable { viewModel.addSticker(emoji) })
                        }
                    }
                }"""

# Since there are two, replace the LAST one by splitting on the duplicate and joining
parts = content.rsplit(duplicate_sticker, 1)
if len(parts) > 1:
    content = "".join(parts)

with open(path, "w") as f:
    f.write(content)
