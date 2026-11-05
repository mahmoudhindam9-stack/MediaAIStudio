param(
  [string]$InstallDirectory = $PSScriptRoot
)

$ErrorActionPreference = 'Stop'
$InstallDirectory = (Resolve-Path -LiteralPath $InstallDirectory).Path
$launcher = Join-Path $InstallDirectory 'Launch.vbs'
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
  throw "Launch.vbs was not found in $InstallDirectory"
}

$wsh = New-Object -ComObject WScript.Shell
$icon = Join-Path $env:SystemRoot 'System32\imageres.dll'
$desktopCandidates = @(
  [Environment]::GetFolderPath([Environment+SpecialFolder]::DesktopDirectory),
  [Environment]::GetFolderPath([Environment+SpecialFolder]::Desktop),
  (Join-Path $env:PUBLIC 'Desktop')
) | Where-Object { $_ } | Select-Object -Unique

$created = $false
foreach ($desktop in $desktopCandidates) {
  try {
    if (-not (Test-Path -LiteralPath $desktop)) {
      New-Item -ItemType Directory -Path $desktop -Force | Out-Null
    }
    $shortcutPath = Join-Path $desktop 'MediaAIStudio.lnk'
    $shortcut = $wsh.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = Join-Path $env:SystemRoot 'System32\wscript.exe'
    $shortcut.Arguments = '"' + $launcher + '"'
    $shortcut.WorkingDirectory = $InstallDirectory
    $shortcut.Description = 'Start MediaAIStudio PC workspace'
    $shortcut.IconLocation = "$icon,109"
    $shortcut.Save()
    if (Test-Path -LiteralPath $shortcutPath -PathType Leaf) {
      Write-Output "Desktop shortcut created: $shortcutPath"
      $created = $true
      break
    }
  } catch {
    Write-Warning "Could not create shortcut in $desktop : $($_.Exception.Message)"
  }
}

$startMenu = Join-Path ([Environment]::GetFolderPath([Environment+SpecialFolder]::Programs)) 'MediaAIStudio'
New-Item -ItemType Directory -Path $startMenu -Force | Out-Null
$menuShortcutPath = Join-Path $startMenu 'MediaAIStudio.lnk'
$menuShortcut = $wsh.CreateShortcut($menuShortcutPath)
$menuShortcut.TargetPath = Join-Path $env:SystemRoot 'System32\wscript.exe'
$menuShortcut.Arguments = '"' + $launcher + '"'
$menuShortcut.WorkingDirectory = $InstallDirectory
$menuShortcut.Description = 'Start MediaAIStudio PC workspace'
$menuShortcut.IconLocation = "$icon,109"
$menuShortcut.Save()
Write-Output "Start Menu shortcut created: $menuShortcutPath"

if (-not $created) {
  throw 'Windows did not allow a Desktop shortcut to be created. A Start Menu shortcut was created instead.'
}
