#!/bin/bash
sed -i 's/play-services-mlkit-subject-segmentation/subject-segmentation/g' gradle/libs.versions.toml
sed -i 's/com.google.android.gms/com.google.mlkit/g' gradle/libs.versions.toml
