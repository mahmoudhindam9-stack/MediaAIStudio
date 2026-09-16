# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# TensorFlow Lite
-dontwarn org.tensorflow.lite.**
-keep class org.tensorflow.lite.** { *; }

# ONNX Runtime
-dontwarn ai.onnxruntime.**
-keep class ai.onnxruntime.** { *; }

# Protobuf & DataStore
-dontwarn com.google.protobuf.**
-dontwarn androidx.datastore.**

# Apache Commons & BouncyCastle (from pdfbox-android)
-dontwarn org.apache.commons.logging.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.**

