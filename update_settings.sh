#!/bin/bash
STRINGS="app/src/main/res/values/strings.xml"
STRINGS_AR="app/src/main/res/values-ar/strings.xml"

sed -i '/<string name="settings_ai">AI Engine/a \
    <string name="settings_ai_mode">AI Processing Mode</string>\
    <string name="settings_ai_mode_desc">Auto / On-device / Cloud</string>\
    <string name="settings_ai_clear_cache">Clear AI Cache</string>\
    <string name="settings_ai_clear_cache_desc">Free up space from AI generation results</string>\
' $STRINGS

sed -i '/<string name="settings_ai">محرك الذكاء/a \
    <string name="settings_ai_mode">وضع معالجة الذكاء الاصطناعي</string>\
    <string name="settings_ai_mode_desc">تلقائي / على الجهاز / سحابي</string>\
    <string name="settings_ai_clear_cache">مسح ذاكرة التخزين المؤقت للذكاء الاصطناعي</string>\
    <string name="settings_ai_clear_cache_desc">تفريغ مساحة من نتائج التوليد</string>\
' $STRINGS_AR

