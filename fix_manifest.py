import os

path = "app/src/main/AndroidManifest.xml"
with open(path, "r") as f:
    content = f.read()

if "android.permission.INTERNET" not in content:
    content = content.replace('<uses-permission android:name="android.permission.CAMERA" />',
                              '<uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n    <uses-permission android:name="android.permission.CAMERA" />')

if "android.support.v4.content.FileProvider" not in content and "androidx.core.content.FileProvider" not in content:
    provider = """
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
"""
    content = content.replace("</application>", provider + "    </application>")

with open(path, "w") as f:
    f.write(content)

os.makedirs("app/src/main/res/xml", exist_ok=True)
with open("app/src/main/res/xml/file_paths.xml", "w") as f:
    f.write("""<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
</paths>
""")
