#!/bin/bash
sed -i 's/subject-segmentation/play-services-mlkit-subject-segmentation/g' gradle/libs.versions.toml
sed -i 's/com.google.mlkit/com.google.android.gms/g' gradle/libs.versions.toml
sed -i 's/16.0.0-beta01/16.0.0-beta1/g' gradle/libs.versions.toml
