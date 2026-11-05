#!/bin/bash
REQ="app/src/main/java/com/example/ai/core/AIRequest.kt"

sed -i '/data class BackgroundRemoval/i \
    data class DetectObjects(override val sourceUri: String) : AIRequest\
' $REQ

