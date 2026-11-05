# MediaAIStudio

The repository is separated into two independent application folders:

- **Android version/** — the native Android Studio / Gradle project. Open this folder directly in Android Studio.
- **PC version/** — the browser-based PC edition, its backend, and the Windows installer.

## PC version installation
Open `PC version/Install.bat` on Windows. The setup installs Node.js LTS when needed, installs dependencies, builds the web interface, asks for provider API keys, and creates a MediaAIStudio shortcut on the Desktop. The shortcut opens MediaAIStudio in the default browser.

## Android version
Open `Android version/` in Android Studio. GitHub Actions workflows remain in the repository's required root `.github/workflows` location and build the Android project from its new folder.

The two editions are kept separate; the PC installer does not install or modify the Android app.
