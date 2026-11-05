#!/bin/bash
TOML="gradle/libs.versions.toml"
BUILD="app/build.gradle.kts"

sed -i 's/mlkit-object-detection =/mlkit-objectdetection =/g' $TOML
sed -i 's/implementation(libs.mlkit.object.detection)/implementation(libs.mlkit.objectdetection)/g' $BUILD

