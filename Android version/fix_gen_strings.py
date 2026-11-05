import os

def append_strings(path, is_ar=False):
    with open(path, "r") as f:
        content = f.read()
    
    if "ai_gen_fill" not in content:
        if is_ar:
            new_strings = """
    <string name="ai_gen_fill">تعبئة توليدية</string>
    <string name="ai_obj_removal">إزالة كائن</string>
    <string name="ai_img_to_img">صورة إلى صورة</string>
    <string name="ai_img_to_vid">صورة إلى فيديو</string>
    <string name="ai_vid_to_vid">فيديو إلى فيديو</string>
    <string name="ai_vid_ext">تمديد الفيديو</string>
    <string name="ai_advanced_tools">أدوات الذكاء الاصطناعي المتقدمة (السحابة)</string>
    <string name="ai_prompt_hint">أدخل المطالبة...</string>
    <string name="ai_generate">توليد</string>
</resources>"""
        else:
            new_strings = """
    <string name="ai_gen_fill">Generative Fill</string>
    <string name="ai_obj_removal">Object Removal</string>
    <string name="ai_img_to_img">Image-to-Image</string>
    <string name="ai_img_to_vid">Image-to-Video</string>
    <string name="ai_vid_to_vid">Video-to-Video</string>
    <string name="ai_vid_ext">Video Extension</string>
    <string name="ai_advanced_tools">Advanced AI Tools (Cloud)</string>
    <string name="ai_prompt_hint">Enter prompt...</string>
    <string name="ai_generate">Generate</string>
</resources>"""
        content = content.replace("</resources>", new_strings)
        with open(path, "w") as f:
            f.write(content)

append_strings("app/src/main/res/values/strings.xml", False)
append_strings("app/src/main/res/values-ar/strings.xml", True)
