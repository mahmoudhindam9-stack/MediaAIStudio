#!/bin/bash
RES_DIR="./app/src/main/res"

# Extract existing strings (just to be safe we don't wipe them).
# Wait, let's just append to strings.xml safely using sed or just append.
cat << 'INNER' >> $RES_DIR/values/strings.xml

    <!-- AI Tools -->
    <string name="ai_tools">AI Tools</string>
    <string name="ai_background_removal">Background Removal</string>
    <string name="ai_object_removal">Object Removal</string>
    <string name="ai_upscale">Upscale (2x)</string>
    <string name="ai_enhance">Enhance</string>
    <string name="ai_restyle">Restyle</string>
    <string name="ai_assistant">AI Assistant</string>
    <string name="ai_processing">Processing AI Request…</string>
    <string name="ai_error_provider_unavailable">AI Provider Unavailable</string>
    <string name="ai_error_model_unavailable">AI Model Unavailable</string>
    <string name="ai_error_generic">AI Processing Failed</string>
    <string name="ai_prompt_hint">E.g., Make it cinematic</string>
    <string name="ai_apply">Apply AI</string>
    <string name="ai_cancel">Cancel AI</string>
INNER

mkdir -p $RES_DIR/values-ar
# Copy current strings.xml or just create it if it doesn't exist
if [ ! -f $RES_DIR/values-ar/strings.xml ]; then
cat << 'INNER' > $RES_DIR/values-ar/strings.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Media AI Studio</string>
    <string name="ai_tools">أدوات الذكاء الاصطناعي</string>
    <string name="ai_background_removal">إزالة الخلفية</string>
    <string name="ai_object_removal">إزالة كائن</string>
    <string name="ai_upscale">تكبير (2x)</string>
    <string name="ai_enhance">تحسين الصورة</string>
    <string name="ai_restyle">إعادة تصميم</string>
    <string name="ai_assistant">مساعد الذكاء الاصطناعي</string>
    <string name="ai_processing">جاري المعالجة…</string>
    <string name="ai_error_provider_unavailable">مزود الذكاء الاصطناعي غير متاح</string>
    <string name="ai_error_model_unavailable">النموذج غير متاح</string>
    <string name="ai_error_generic">فشلت معالجة الذكاء الاصطناعي</string>
    <string name="ai_prompt_hint">مثال: اجعلها سينمائية</string>
    <string name="ai_apply">تطبيق الذكاء الاصطناعي</string>
    <string name="ai_cancel">إلغاء</string>
</resources>
INNER
else
cat << 'INNER' >> $RES_DIR/values-ar/strings.xml

    <!-- AI Tools -->
    <string name="ai_tools">أدوات الذكاء الاصطناعي</string>
    <string name="ai_background_removal">إزالة الخلفية</string>
    <string name="ai_object_removal">إزالة كائن</string>
    <string name="ai_upscale">تكبير (2x)</string>
    <string name="ai_enhance">تحسين الصورة</string>
    <string name="ai_restyle">إعادة تصميم</string>
    <string name="ai_assistant">مساعد الذكاء الاصطناعي</string>
    <string name="ai_processing">جاري المعالجة…</string>
    <string name="ai_error_provider_unavailable">مزود الذكاء الاصطناعي غير متاح</string>
    <string name="ai_error_model_unavailable">النموذج غير متاح</string>
    <string name="ai_error_generic">فشلت معالجة الذكاء الاصطناعي</string>
    <string name="ai_prompt_hint">مثال: اجعلها سينمائية</string>
    <string name="ai_apply">تطبيق الذكاء الاصطناعي</string>
    <string name="ai_cancel">إلغاء</string>
INNER
fi

# IMPORTANT: Fix the strings file since appending puts it AFTER the </resources> tag.
sed -i 's/<\/resources>//g' $RES_DIR/values/strings.xml
echo '</resources>' >> $RES_DIR/values/strings.xml

sed -i 's/<\/resources>//g' $RES_DIR/values-ar/strings.xml
echo '</resources>' >> $RES_DIR/values-ar/strings.xml

