import os

def fix_file(path):
    with open(path, "r") as f:
        content = f.read()
    
    if "update_available_title" not in content:
        content = content.replace("</resources>", """
    <string name="settings_about">About</string>
    <string name="version_info">Version: %1$s</string>
    <string name="github_link">https://github.com/placeholder_owner/placeholder_repo</string>
    <string name="settings_updates">Updates</string>
    <string name="auto_update_checks">Automatic Update Checks</string>
    <string name="last_check">Last check: %1$s</string>
    <string name="check_now">Check Now</string>
    <string name="checking_updates">Checking for updates...</string>
    <string name="up_to_date">You\\'re up to date.</string>
    <string name="update_available">Update Available!</string>
    <string name="error_checking">Error: %1$s</string>
    <string name="size_mb">Size: %1$d MB</string>
    <string name="download_update">Download Update</string>
    <string name="verifying_update">Verifying update...</string>
    <string name="install_update">Install</string>
    <string name="update_available_title">Update Available</string>
    <string name="update_available_desc">Version %1$s is available to download.</string>
    <string name="back">Back</string>
</resources>""")
        with open(path, "w") as f:
            f.write(content)

fix_file("app/src/main/res/values/strings.xml")
