# Release Process

This document describes how to create and distribute a production-signed MediaAIStudio APK via GitHub Releases.

## 1. Required GitHub Actions secrets

Before pushing to `main` or running the release workflow, configure these repository secrets under **Settings > Secrets and variables > Actions**:

- `RELEASE_KEYSTORE_BASE64`: the original production keystore encoded as one base64 string.
- `RELEASE_STORE_PASSWORD`: keystore password.
- `RELEASE_KEY_ALIAS`: the original production key alias.
- `RELEASE_KEY_PASSWORD`: password for that key.
- `RELEASE_CERT_SHA256`: SHA-256 fingerprint of the certificate in that same keystore (64 hexadecimal characters; colons are optional).

The workflow validates that all five secrets exist and that the certificate fingerprint is correctly formatted before attempting the release build. It also checks that the keystore can be opened and contains the configured alias. The fingerprint secret is passed into the Android build as `PERMANENT_RELEASE_CERT_SHA256`, so the app's in-app update verifier trusts the exact certificate used to sign the APK.

Do not commit a keystore, passwords, API tokens, or base64-encoded signing material to the repository. Do not paste them into chat. If the original production keystore is lost, an APK cannot be used to recover its private signing key; do not generate a replacement without first planning an app-signing migration.

## 2. Release verification

The workflow:
- Validates backend JavaScript syntax.
- Checks required signing secrets and keystore alias.
- Builds the release APK.
- Verifies package name, version, and APK signing certificate fingerprint.
- Generates a SHA-256 checksum.
- Publishes the APK and checksum as GitHub Release assets.

A release is not considered successful until the APK signature matches `RELEASE_CERT_SHA256`.

## 3. How users receive updates

The app includes an in-app Update Checker. It validates version information and the APK signing certificate against the trusted permanent release identity before allowing Android PackageInstaller to install an update. Keep the original production keystore and certificate fingerprint stable for future releases.
