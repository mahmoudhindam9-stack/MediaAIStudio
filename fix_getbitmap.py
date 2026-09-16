import os

def fix_file(path):
    with open(path, "r") as f:
        content = f.read()

    replacement = """
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
"""

    content = content.replace("MediaStore.Images.Media.getBitmap(context.contentResolver, uri)", replacement.strip())
    
    # special cases
    content = content.replace(
        "MediaStore.Images.Media.getBitmap(context.contentResolver, previewUri)",
        """if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, previewUri))
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, previewUri)
                            }"""
    )
    
    content = content.replace(
        "MediaStore.Images.Media.getBitmap(resolver, originalUri)",
        """if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(resolver, originalUri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(resolver, originalUri)
            }"""
    )

    with open(path, "w") as f:
        f.write(content)

fix_file("app/src/main/java/com/example/photoeditor/PhotoEditorViewModel.kt")
fix_file("app/src/main/java/com/example/photoeditor/PhotoExport.kt")
fix_file("app/src/main/java/com/example/ai/provider/OnDeviceAIProvider.kt")
