import os

def append_strings(path, is_ar=False):
    with open(path, "r") as f:
        content = f.read()
    
    if "ai_video_tools" not in content:
        if is_ar:
            new_strings = """
    <string name="ai_video_tools">أدوات الذكاء الاصطناعي للفيديو</string>
    <string name="ai_smart_cut">قص ذكي</string>
    <string name="ai_auto_captions">تسميات توضيحية تلقائية</string>
    <string name="ai_object_tracking">تتبع الكائنات</string>
    <string name="ai_smart_reframe">إعادة تأطير ذكية</string>
    <string name="ai_enhance">تحسين</string>
    <string name="ai_review_cuts">مراجعة القص الذكي</string>
    <string name="ai_cuts_suggested_msg">اقترح الذكاء الاصطناعي %d عملية قص بناءً على التحليل الهيكلي. هل تريد تطبيق هذه التغييرات؟</string>
    <string name="ai_apply">تطبيق</string>
    <string name="ai_reject">رفض</string>
    <string name="ai_close">إغلاق</string>
    <string name="ai_msg_running_tracking">جاري تشغيل تتبع الكائنات...</string>
    <string name="ai_msg_tracking_complete">اكتمل التتبع</string>
    <string name="ai_msg_analyzing_cuts">جاري تحليل القص الذكي...</string>
    <string name="ai_msg_cuts_suggested">تم اقتراح قص ذكي</string>
    <string name="ai_msg_generating_captions">جاري إنشاء التسميات التوضيحية...</string>
    <string name="ai_msg_captions_generated">تم إنشاء التسميات التوضيحية</string>
    <string name="ai_msg_generating_reframe">جاري إنشاء مسارات إعادة التأطير...</string>
    <string name="ai_msg_reframe_generated">تم إنشاء مسارات ذكية لعدد %d</string>
    <string name="ai_msg_enhancing">جاري تحسين الفيديو...</string>
</resources>"""
        else:
            new_strings = """
    <string name="ai_video_tools">AI Video Tools</string>
    <string name="ai_smart_cut">Smart Cut</string>
    <string name="ai_auto_captions">Auto Captions</string>
    <string name="ai_object_tracking">Object Tracking</string>
    <string name="ai_smart_reframe">Smart Reframe</string>
    <string name="ai_enhance">Enhance</string>
    <string name="ai_review_cuts">Review Smart Cuts</string>
    <string name="ai_cuts_suggested_msg">AI has suggested %d cuts based on structural analysis. Apply these changes?</string>
    <string name="ai_apply">Apply</string>
    <string name="ai_reject">Reject</string>
    <string name="ai_close">Close</string>
    <string name="ai_msg_running_tracking">Running Object Tracking...</string>
    <string name="ai_msg_tracking_complete">Tracking Complete</string>
    <string name="ai_msg_analyzing_cuts">Analyzing for Smart Cuts...</string>
    <string name="ai_msg_cuts_suggested">Smart Cuts Suggested</string>
    <string name="ai_msg_generating_captions">Generating Captions...</string>
    <string name="ai_msg_captions_generated">Captions Generated</string>
    <string name="ai_msg_generating_reframe">Generating Reframe Paths...</string>
    <string name="ai_msg_reframe_generated">Smart Reframe generated %d paths.</string>
    <string name="ai_msg_enhancing">Enhancing Video...</string>
</resources>"""
        content = content.replace("</resources>", new_strings)
        with open(path, "w") as f:
            f.write(content)

append_strings("app/src/main/res/values/strings.xml", False)
append_strings("app/src/main/res/values-ar/strings.xml", True)
