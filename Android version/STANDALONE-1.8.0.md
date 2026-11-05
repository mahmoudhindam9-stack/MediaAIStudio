# MediaAIStudio Android 1.8.0 — Standalone edition

This Android build intentionally uses the distinct application ID `com.mediaaistudio.standalone` and version code 14. It installs alongside the preserved original `com.example.mediaaistudio` / MediaAIStudio 1.7.0 app and does not overwrite its data or icon.

The 1.8.0 CI APKs are debug-signed test builds, not production/store-signed releases. Keep the original 1.7.0 installed. Do not distribute these debug APKs as a production release. A production release requires a dedicated, securely stored signing keystore and certificate fingerprint; the repository release workflow enforces this.

The PC edition has its own independent version line and release tag `pc-v1.8.0`.
