import os

path = "app/src/main/java/com/example/videoeditor/VideoEditorState.kt"
with open(path, "r") as f:
    content = f.read()

if "com.example.ai.video.SubtitleTrack" not in content:
    content = content.replace(
        "import java.util.UUID",
        "import java.util.UUID\nimport com.example.ai.video.SubtitleTrack\nimport com.example.ai.video.TrackingKeyframe\nimport com.example.ai.video.SuggestedCut"
    )
    content = content.replace(
        "val selectedItemId: String? = null",
        "val selectedItemId: String? = null,\n    val aiSubtitleTrack: SubtitleTrack? = null,\n    val aiTrackingData: List<TrackingKeyframe>? = null,\n    val aiSuggestedCuts: List<SuggestedCut>? = null"
    )
    with open(path, "w") as f:
        f.write(content)
