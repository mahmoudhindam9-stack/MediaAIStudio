import os

def insert_string(path, is_ar=False):
    with open(path, "r") as f:
        content = f.read()
    
    if "ai_enhance" not in content:
        if is_ar:
            content = content.replace("</resources>", '    <string name="ai_enhance">تحسين الصورة</string>\n</resources>')
        else:
            content = content.replace("</resources>", '    <string name="ai_enhance">Enhance</string>\n</resources>')
            
    with open(path, "w") as f:
        f.write(content)

insert_string("app/src/main/res/values/strings.xml", False)
insert_string("app/src/main/res/values-ar/strings.xml", True)
