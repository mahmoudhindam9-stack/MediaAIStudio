Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
base = fso.GetParentFolderName(WScript.ScriptFullName)
desktop = shell.SpecialFolders("Desktop")
Set shortcut = shell.CreateShortcut(desktop & "\MediaAIStudio.lnk")
shortcut.TargetPath = shell.ExpandEnvironmentStrings("%SystemRoot%") & "\System32\wscript.exe"
shortcut.Arguments = """" & base & "\Launch.vbs"""
shortcut.WorkingDirectory = base
shortcut.Description = "Open MediaAIStudio in your browser"
shortcut.IconLocation = shell.ExpandEnvironmentStrings("%SystemRoot%") & "\System32\imageres.dll, 109"
shortcut.Save
