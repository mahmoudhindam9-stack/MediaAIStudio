#!/bin/bash
VM_FILE="./app/src/main/java/com/example/photoeditor/PhotoEditorViewModel.kt"

# Add previewBitmap StateFlow
sed -i '/val previewAiResultUri = MutableStateFlow<String?>/a \
    val previewBitmap = MutableStateFlow<Bitmap?>(null)\
' $VM_FILE

# Find where previewAiResultUri is set to result.outputUri, and decode the bitmap there
sed -i 's/previewAiResultUri.value = result.outputUri/previewAiResultUri.value = result.outputUri\n                    val previewUri = Uri.parse(result.outputUri)\n                    val context = getApplication<Application>()\n                    try {\n                        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {\n                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, previewUri)) { decoder, _, _ -> decoder.isMutableRequired = true }\n                        } else {\n                            MediaStore.Images.Media.getBitmap(context.contentResolver, previewUri)\n                        }\n                        previewBitmap.value = b\n                    } catch (e: Exception) {\n                        e.printStackTrace()\n                    }/g' $VM_FILE

# Clear previewBitmap on discard/accept
sed -i 's/previewAiResultUri.value = null/previewAiResultUri.value = null\n            previewBitmap.value = null/g' $VM_FILE

