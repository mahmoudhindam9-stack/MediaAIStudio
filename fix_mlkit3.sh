#!/bin/bash
TOML="gradle/libs.versions.toml"

sed -i '/mlkit-play-services-mlkit-subject-segmentation = /d' $TOML
sed -i '/mlkit-objectdetection = /d' $TOML

sed -i '/\[libraries\]/a \
mlkit-subjectsegmentation = { group = "com.google.android.gms", name = "play-services-mlkit-subject-segmentation", version.ref = "mlkitSubjectSegmentation" }\
mlkit-objectdetection = { group = "com.google.mlkit", name = "object-detection", version.ref = "mlkitObjectDetection" }\
' $TOML

sed -i 's/implementation(libs.mlkit.subject.segmentation)/implementation(libs.mlkit.subjectsegmentation)/g' app/build.gradle.kts
sed -i 's/implementation(libs.mlkit.play.services.mlkit.subject.segmentation)/implementation(libs.mlkit.subjectsegmentation)/g' app/build.gradle.kts
