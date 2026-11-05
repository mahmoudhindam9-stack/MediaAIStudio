import os

def append_strings(path, is_ar=False):
    with open(path, "r") as f:
        content = f.read()
    
    if "ai_cloud_privacy_consent" not in content:
        if is_ar:
            new_strings = """
    <string name="ai_cloud_privacy_consent">تتطلب هذه العملية السحابة. سيتم تحميل الوسائط بأمان، وسيتم استخدام حصة المزود. هل توافق؟</string>
    <string name="ai_offline_warning">المعالجة السحابية غير متوفرة دون اتصال بالإنترنت.</string>
</resources>"""
        else:
            new_strings = """
    <string name="ai_cloud_privacy_consent">This operation requires Cloud processing. Media will be securely uploaded and provider quota may be consumed. Do you consent?</string>
    <string name="ai_offline_warning">Cloud processing unavailable offline.</string>
</resources>"""
        content = content.replace("</resources>", new_strings)
        with open(path, "w") as f:
            f.write(content)

append_strings("app/src/main/res/values/strings.xml", False)
append_strings("app/src/main/res/values-ar/strings.xml", True)
