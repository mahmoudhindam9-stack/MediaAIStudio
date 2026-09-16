#!/bin/bash
STRINGS="app/src/main/res/values/strings.xml"
STRINGS_AR="app/src/main/res/values-ar/strings.xml"

sed -i '/<string name="ai_background_removal">/a \
    <string name="ai_object_detection">Detect Objects</string>\
' $STRINGS

sed -i '/<string name="ai_background_removal">/a \
    <string name="ai_object_detection">اكتشاف الكائنات</string>\
' $STRINGS_AR

