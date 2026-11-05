@echo off
setlocal EnableExtensions
title MediaAIStudio - Setup
cd /d "%~dp0"
echo.
echo  ============================================
echo             MediaAIStudio PC Setup
echo  ============================================
echo.

where node >nul 2>nul
if errorlevel 1 (
  echo [1/5] Node.js LTS is required. Installing it now...
  where winget >nul 2>nul
  if errorlevel 1 (
    echo.
    echo Windows Package Manager (winget) was not found.
    echo Install Node.js LTS from https://nodejs.org/en/download
    echo Then run Install.bat again.
    pause
    exit /b 1
  )
  winget install --id OpenJS.NodeJS.LTS --exact --silent --accept-package-agreements --accept-source-agreements
  if errorlevel 1 (
    echo Node.js installation failed. Install the LTS version from https://nodejs.org/en/download
    pause
    exit /b 1
  )
  set "PATH=%ProgramFiles%\nodejs;%ProgramFiles(x86)%\nodejs;%PATH%"
)

where npm >nul 2>nul
if errorlevel 1 (
  set "PATH=%ProgramFiles%\nodejs;%ProgramFiles(x86)%\nodejs;%PATH%"
)
node --version
if errorlevel 1 (
  echo Node.js is not available in this window. Close it and run Install.bat again.
  pause
  exit /b 1
)

echo.
echo [2/5] Installing backend dependencies...
call npm install --prefix "backend"
if errorlevel 1 goto :failed

echo.
echo [3/5] Installing web dependencies...
call npm install --prefix "web"
if errorlevel 1 goto :failed

echo.
echo [4/5] Building the browser application...
call npm run build --prefix "web"
if errorlevel 1 goto :failed

echo.
echo [5/6] Checking optional FFmpeg support for IP cameras...
where ffmpeg >nul 2>nul
if errorlevel 1 (
  echo FFmpeg enables RTSP-to-HLS conversion and Windows camera discovery.
  where winget >nul 2>nul
  if errorlevel 1 (
    echo winget was not found. You can install FFmpeg later from https://ffmpeg.org/download.html
  ) else (
    set /p INSTALL_FFMPEG=Install FFmpeg now using winget? [Y/N]: 
    if /I "%INSTALL_FFMPEG%"=="Y" (
      winget install --id Gyan.FFmpeg.Shared --exact --silent --accept-package-agreements --accept-source-agreements
      if errorlevel 1 echo FFmpeg installation did not complete. USB cameras will still work; RTSP support can be installed later.
    )
  )
) else (
  ffmpeg -version
)

echo.
echo [6/6] Configuring the local backend...
if not exist "backend\.env" (
  set /p GEMINI_KEY=Paste your Gemini API key (press Enter to skip): 
  set /p FAL_KEY=Paste your FAL API key (optional, press Enter to skip): 
  for /f %%T in ('node -e "process.stdout.write(require('crypto').randomBytes(32).toString('hex'))"') do set "BACKEND_TOKEN=%%T"
  >"backend\.env" echo PORT=3100
  >>"backend\.env" echo MEDIA_AI_BACKEND_API_KEY=%BACKEND_TOKEN%
  >>"backend\.env" echo GEMINI_API_KEY=%GEMINI_KEY%
  >>"backend\.env" echo FAL_KEY=%FAL_KEY%
  >>"backend\.env" echo IMAGE_MODEL=gemini-nano-banana-2.1
  >>"backend\.env" echo VIDEO_MODEL=veo-3.1-fast-generate-preview
  >>"backend\.env" echo TRANSCRIPTION_MODEL=gemini-3.8-flash
)

cscript //nologo "CreateShortcut.vbs"
if errorlevel 1 (
  echo Could not create the desktop shortcut automatically.
  echo You can still open Start.bat from this folder.
)

echo.
echo Setup completed successfully.
echo A MediaAIStudio shortcut has been added to your Desktop.
echo Double-click it to open the app in your browser.
echo.
pause
exit /b 0

:failed
echo.
echo Setup failed. Check your internet connection and try Install.bat again.
pause
exit /b 1
