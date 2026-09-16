import os

provider_path = "app/src/main/java/com/example/ai/provider/OnDeviceAIProvider.kt"
with open(provider_path, "r") as f:
    code = f.read()

code = code.replace("SubjectSegmentationOptions", "SubjectSegmenterOptions")
with open(provider_path, "w") as f:
    f.write(code)

def append_strings(path, is_ar=False):
    with open(path, "r") as f:
        content = f.read()
    
    if "settings_ai_mode" not in content:
        if is_ar:
            new_strings = """
    <string name="settings_ai_mode">وضع معالجة الذكاء الاصطناعي</string>
    <string name="settings_ai_mode_desc">تلقائي / على الجهاز / سحابي</string>
    <string name="settings_ai_clear_cache">مسح ذاكرة التخزين المؤقت للذكاء الاصطناعي</string>
    <string name="settings_ai_clear_cache_desc">تفريغ مساحة من نتائج التوليد</string>
</resources>"""
        else:
            new_strings = """
    <string name="settings_ai_mode">AI Processing Mode</string>
    <string name="settings_ai_mode_desc">Auto / On-device / Cloud</string>
    <string name="settings_ai_clear_cache">Clear AI Cache</string>
    <string name="settings_ai_clear_cache_desc">Free up space from AI generation results</string>
</resources>"""
        content = content.replace("</resources>", new_strings)
        with open(path, "w") as f:
            f.write(content)

append_strings("app/src/main/res/values/strings.xml", False)
append_strings("app/src/main/res/values-ar/strings.xml", True)
