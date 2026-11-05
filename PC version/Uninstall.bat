@echo off
setlocal
set "SHORTCUT=%USERPROFILE%\Desktop\MediaAIStudio.lnk"
if exist "%SHORTCUT%" del "%SHORTCUT%"
echo The MediaAIStudio desktop shortcut was removed.
echo Node.js is left installed because other applications may use it.
echo Your project files, local backend configuration, and generated media were not deleted.
pause
