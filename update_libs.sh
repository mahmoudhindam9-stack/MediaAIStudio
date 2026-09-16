#!/bin/bash
TOML="gradle/libs.versions.toml"

sed -i '/\[versions\]/a \
mlkitSubjectSegmentation = "16.0.0-beta01"\
mlkitObjectDetection = "17.0.2"\
' $TOML

sed -i '/\[libraries\]/a \
mlkit-subject-segmentation = { group = "com.google.android.gms", name = "play-services-mlkit-subject-segmentation", version.ref = "mlkitSubjectSegmentation" }\
mlkit-object-detection = { group = "com.google.mlkit", name = "object-detection", version.ref = "mlkitObjectDetection" }\
' $TOML

sed -i '/implementation(libs.retrofit)/a \
  implementation(libs.mlkit.subject.segmentation)\
  implementation(libs.mlkit.object.detection)\
' app/build.gradle.kts
