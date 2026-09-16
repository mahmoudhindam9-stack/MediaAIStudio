#!/bin/bash
PROV="app/src/main/java/com/example/ai/provider/OnDeviceAIProvider.kt"

sed -i 's/"ai_bg_remove_${UUID.randomUUID()}.png"/"ai_bg_remove_${request.sourceUri.hashCode()}.png"/g' $PROV
sed -i 's/"ai_obj_detect_${UUID.randomUUID()}.jpg"/"ai_obj_detect_${request.sourceUri.hashCode()}.jpg"/g' $PROV

