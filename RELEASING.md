# Release Process

This document describes how to create and distribute a new release of MediaAIStudio via GitHub Releases.

## 1. CI/CD Preparation (GitHub Actions)

The repository is structured to support a future GitHub Actions workflow to automate the build, signing, and release process. The CI will:
- Check out the repository.
- Build the `assembleRelease` APK.
- Sign the APK using production credentials supplied securely via GitHub Secrets.
- Generate a SHA-256 digest of the APK.
- Create a GitHub Release and attach the APK and `.sha256` file.

## 2. Production Signing Config

The app is configured to use environment variables for production signing during the `release` build:
- `RELEASE_KEYSTORE_PATH`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

If these are not provided in the CI or local environment, the release build will fall back securely without exposing keys or committing a `debug.keystore`.

## 3. How to Create a Release

1. **Update Versions:** Increment `versionCode` and `versionName` in `app/build.gradle.kts`.
2. **Commit & Push:** Push changes to the `main` branch.
3. **Tag the Release:** Create a tag on GitHub in the format `v{versionName}-{versionCode}` (e.g., `v1.0.1-2`).
4. **Build APK:** Build the signed release APK (`app-release.apk`).
5. **Publish on GitHub:** Create a new Release associated with the tag. Upload the `app-release.apk` as a binary asset.

## 4. How Users Receive Updates

The application includes an in-app Update Checker:
- **Automatic Checks:** If enabled in Settings, a background `WorkManager` job periodically queries the GitHub Releases API.
- **Manual Checks:** Users can manually trigger a check from Settings > Updates.
- **Verification:** The app securely validates the update's `versionCode` (to prevent downgrades), optionally checks the SHA-256 digest, and **mandates** a cryptographic verification of the APK's signing certificate against the currently installed trusted identity before allowing the Android PackageInstaller to execute.
