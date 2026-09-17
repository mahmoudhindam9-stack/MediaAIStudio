package com.example.videoeditor.export

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Transformer.Listener
import com.example.videoeditor.VideoEditorState
import com.example.videoeditor.VideoClip

/**
 * Immutable description of one video segment to be rendered.
 * Timeline time is deliberately kept separate from source trim time.
 */
data class VideoRenderItem(
    val uri: Uri,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val startTimeMs: Long,
    val rotation: Float,
    val volume: Float,
    val muted: Boolean
)

fun VideoEditorState.toVideoRenderPlan(): List<VideoRenderItem> =
    videoClips.map { clip ->
        VideoRenderItem(
            uri = Uri.parse(clip.uri),
            trimStartMs = clip.startTrimMs,
            trimEndMs = clip.startTrimMs + clip.durationMs,
            startTimeMs = clip.startTimeMs,
            rotation = clip.rotation,
            volume = clip.volume,
            muted = clip.isMuted
        )
    }

@OptIn(UnstableApi::class)
class VideoExport(private val context: Context) {

    fun export(
        state: VideoEditorState,
        outputFilePath: String,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val renderPlan = state.toVideoRenderPlan()
            validateRenderPlan(renderPlan)

            if (state.audioTracks.isNotEmpty()) {
                LogWarning.audioTracksIgnored(state.audioTracks.size)
            }

            val videoEditedMediaItems = renderPlan.map { item ->
                val mediaItem = MediaItem.Builder()
                    .setUri(item.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(item.trimStartMs)
                            .setEndPositionMs(item.trimEndMs)
                            .build()
                    )
                    .build()

                // Rotation/volume are retained in the render plan. Current Transformer wiring
                // intentionally remains video-only until the unified audio compositor is added.
                EditedMediaItem.Builder(mediaItem).build()
            }

            val videoSequence = EditedMediaItemSequence(videoEditedMediaItems)
            val composition = Composition.Builder(listOf(videoSequence)).build()

            val transformer = Transformer.Builder(context)
                .addListener(object : Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        onProgress(100)
                        onSuccess()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        onError(exportException)
                    }
                })
                .build()

            transformer.start(composition, outputFilePath)
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun validateRenderPlan(plan: List<VideoRenderItem>) {
        require(plan.isNotEmpty()) { "Cannot export an empty video timeline" }
        var expectedStart = 0L
        plan.forEachIndexed { index, item ->
            require(item.trimStartMs >= 0L) { "Clip $index has a negative trim start" }
            require(item.trimEndMs > item.trimStartMs) { "Clip $index has an invalid trim range" }
            require(item.startTimeMs == expectedStart) {
                "Clip $index is not contiguous: expected $expectedStart, got ${item.startTimeMs}"
            }
            require(item.volume in 0f..1f) { "Clip $index has invalid volume" }
            expectedStart += item.trimEndMs - item.trimStartMs
        }
    }
}

private object LogWarning {
    fun audioTracksIgnored(count: Int) {
        android.util.Log.w(
            "VideoExport",
            "$count audio track(s) are present in the editor but are not yet included in video-only export"
        )
    }
}
