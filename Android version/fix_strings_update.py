import os

def append_strings(path, is_ar=False):
    with open(path, "r") as f:
        content = f.read()
    
    if "settings_updates" not in content:
        if is_ar:
            new_strings = """
    <string name="settings_about">حول</string>
    <string name="version_info">الإصدار: %1$s</string>
    <string name="github_link">https://github.com/placeholder_owner/placeholder_repo</string>
    <string name="settings_updates">التحديثات</string>
    <string name="auto_update_checks">فحوصات التحديث التلقائية</string>
    <string name="last_check">آخر فحص: %1$s</string>
    <string name="check_now">تحقق الآن</string>
    <string name="checking_updates">جاري التحقق من التحديثات...</string>
    <string name="up_to_date">أنت تستخدم أحدث إصدار.</string>
    <string name="update_available">تحديث متاح!</string>
    <string name="error_checking">خطأ: %1$s</string>
    <string name="size_mb">الحجم: %1$d MB</string>
    <string name="download_update">تحميل التحديث</string>
    <string name="verifying_update">جاري التحقق من التحديث...</string>
    <string name="install_update">تثبيت</string>
    <string name="update_available_title">تحديث متاح</string>
    <string name="update_available_desc">الإصدار %1$s متاح للتحميل.</string>
    <string name="back">رجوع</string>
</resources>"""
        else:
            new_strings = """
    <string name="settings_about">About</string>
    <string name="version_info">Version: %1$s</string>
    <string name="github_link">https://github.com/placeholder_owner/placeholder_repo</string>
    <string name="settings_updates">Updates</string>
    <string name="auto_update_checks">Automatic Update Checks</string>
    <string name="last_check">Last check: %1$s</string>
    <string name="check_now">Check Now</string>
    <string name="checking_updates">Checking for updates...</string>
    <string name="up_to_date">You\'re up to date.</string>
    <string name="update_available">Update Available!</string>
    <string name="error_checking">Error: %1$s</string>
    <string name="size_mb">Size: %1$d MB</string>
    <string name="download_update">Download Update</string>
    <string name="verifying_update">Verifying update...</string>
    <string name="install_update">Install</string>
    <string name="update_available_title">Update Available</string>
    <string name="update_available_desc">Version %1$s is available to download.</string>
    <string name="back">Back</string>
</resources>"""
        content = content.replace("</resources>", new_strings)
        with open(path, "w") as f:
            f.write(content)

append_strings("app/src/main/res/values/strings.xml", False)
append_strings("app/src/main/res/values-ar/strings.xml", True)
