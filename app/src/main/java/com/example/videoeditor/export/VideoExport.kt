package com.example.videoeditor.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.videoeditor.AudioClip
import com.example.videoeditor.VideoEditorState
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min


data class VideoRenderItem(
    val uri: Uri,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val startTimeMs: Long,
    val rotation: Float,
    val volume: Float,
    val muted: Boolean,
    val isImage: Boolean
)

data class AudioRenderPlan(
    val startAtMs: Long,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val durationMs: Long
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
            muted = clip.isMuted,
            isImage = clip.isImage
        )
    }

/** Resolves audio timeline semantics without allowing a track to exceed project duration. */
internal fun AudioClip.toAudioRenderPlan(projectDurationMs: Long): AudioRenderPlan? {
    val projectDuration = projectDurationMs.coerceAtLeast(0L)
    if (isMuted || durationMs <= 0L || originalDurationMs <= 0L || projectDuration == 0L) return null

    val startAt = startTimeMs.coerceAtLeast(0L)
    if (startAt >= projectDuration) return null

    val sourceStart = startTrimMs.coerceIn(0L, originalDurationMs)
    val timelineRemaining = projectDuration - startAt
    val requestedDuration = durationMs.coerceAtLeast(0L)
    val sourceRemaining = (originalDurationMs - sourceStart).coerceAtLeast(0L)
    val actualDuration = min(min(requestedDuration, timelineRemaining), sourceRemaining)
    if (actualDuration <= 0L) return null

    return AudioRenderPlan(
        startAtMs = startAt,
        sourceStartMs = sourceStart,
        sourceEndMs = sourceStart + actualDuration,
        durationMs = actualDuration
    )
}

internal fun validateVideoRenderPlan(plan: List<VideoRenderItem>, projectDurationMs: Long) {
    require(plan.isNotEmpty()) { "Cannot export an empty video timeline" }
    require(projectDurationMs > 0L) { "Project duration must be positive" }

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
    require(expectedStart == projectDurationMs) {
        "Video timeline duration mismatch: expected $projectDurationMs, got $expectedStart"
    }
}

internal fun silenceFrameCount(durationMs: Long, sampleRate: Int = 48_000): Long {
    require(sampleRate > 0) { "Sample rate must be positive" }
    return durationMs.coerceAtLeast(0L) * sampleRate / 1_000L
}

@OptIn(UnstableApi::class)
class VideoExport(private val context: Context) {
    fun export(
        state: VideoEditorState,
        onProgress: (Int) -> Unit,
        onSuccess: (Uri) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val temporaryFiles = mutableListOf<File>()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var progressRunnable: Runnable? = null
        var transformer: Transformer? = null
        var isTerminal = false

        fun reportProgress(value: Int) {
            onProgress(value.coerceIn(0, 100))
        }

        fun terminateAndCleanup(action: () -> Unit) {
            if (isTerminal) return
            isTerminal = true
            progressRunnable?.let(handler::removeCallbacks)
            progressRunnable = null
            runCatching { transformer?.removeAllListeners() }
            cleanup(temporaryFiles)
            action()
        }

        try {
            require(state.durationMs > 0L) { "Cannot export an empty timeline" }
            val renderPlan = state.toVideoRenderPlan()
            validateVideoRenderPlan(renderPlan, state.durationMs)

            val outputFile = File(context.cacheDir, "mediaai_export_${System.currentTimeMillis()}.mp4")
            temporaryFiles += outputFile
            reportProgress(0)

            val videoItems = renderPlan.map { item ->
                val mediaBuilder = MediaItem.Builder().setUri(item.uri)
                if (item.isImage) {
                    mediaBuilder.setImageDurationMs(item.trimEndMs - item.trimStartMs)
                } else {
                    mediaBuilder.setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(item.trimStartMs)
                            .setEndPositionMs(item.trimEndMs)
                            .build()
                    )
                }

                val editedBuilder = EditedMediaItem.Builder(mediaBuilder.build())
                if (item.isImage) {
                    editedBuilder.setFrameRate(30)
                } else if (item.muted) {
                    editedBuilder.setRemoveAudio(true)
                } else {
                    editedBuilder.setEffects(
                        audioEffects(
                            volume = item.volume,
                            durationMs = item.trimEndMs - item.trimStartMs,
                            fadeInDurationMs = 0L,
                            fadeOutDurationMs = 0L
                        )
                    )
                }
                editedBuilder.build()
            }

            val sequences = mutableListOf(EditedMediaItemSequence(videoItems))
            state.audioTracks.forEach { track ->
                val plan = track.toAudioRenderPlan(state.durationMs) ?: return@forEach
                val items = mutableListOf<EditedMediaItem>()

                if (plan.startAtMs > 0L) {
                    val silenceFile = createSilenceFile(context.cacheDir, plan.startAtMs)
                    temporaryFiles += silenceFile
                    items += EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(silenceFile))).build()
                }

                val audioItem = MediaItem.Builder()
                    .setUri(Uri.parse(track.uri))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(plan.sourceStartMs)
                            .setEndPositionMs(plan.sourceEndMs)
                            .build()
                    )
                    .build()

                val editedAudio = EditedMediaItem.Builder(audioItem)
                    .setEffects(
                        audioEffects(
                            volume = track.volume,
                            durationMs = plan.durationMs,
                            fadeInDurationMs = track.fadeInDurationMs.coerceAtLeast(0L),
                            fadeOutDurationMs = track.fadeOutDurationMs.coerceAtLeast(0L)
                        )
                    )
                    .build()
                items += editedAudio
                sequences += EditedMediaItemSequence(items)
            }

            val composition = Composition.Builder(sequences).build()
            transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        try {
                            val publishedUri = publishToMediaStore(outputFile)
                            terminateAndCleanup {
                                reportProgress(100)
                                onSuccess(publishedUri)
                            }
                        } catch (e: Exception) {
                            terminateAndCleanup { onError(e) }
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        terminateAndCleanup { onError(exportException) }
                    }
                })
                .build()

            transformer!!.start(composition, outputFile.absolutePath)

            val holder = ProgressHolder()
            progressRunnable = object : Runnable {
                override fun run() {
                    if (isTerminal) return
                    val stateCode = runCatching { transformer!!.getProgress(holder) }.getOrNull()
                    if (stateCode == Transformer.PROGRESS_STATE_AVAILABLE) {
                        reportProgress(holder.progress)
                    }
                    if (!isTerminal && stateCode != null &&
                        stateCode != Transformer.PROGRESS_STATE_NOT_STARTED &&
                        stateCode != Transformer.PROGRESS_STATE_UNAVAILABLE
                    ) {
                        handler.postDelayed(this, 250L)
                    }
                }
            }
            handler.post(progressRunnable!!)
        } catch (e: Exception) {
            terminateAndCleanup { onError(e) }
        }
    }

    @OptIn(UnstableApi::class)
    private fun audioEffects(
        volume: Float,
        durationMs: Long,
        fadeInDurationMs: Long,
        fadeOutDurationMs: Long
    ): Effects {
        val channelMixer = ChannelMixingAudioProcessor()
        for (inputChannelCount in 1..6) {
            val matrix = when (inputChannelCount) {
                1 -> ChannelMixingMatrix(
                    1, 2,
                    floatArrayOf(0.7071f, 0.7071f)
                )
                2 -> ChannelMixingMatrix(
                    2, 2,
                    floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f)
                )
                3 -> ChannelMixingMatrix(
                    3, 2,
                    floatArrayOf(
                        1.0f, 0.0f, 0.7071f,
                        0.0f, 1.0f, 0.7071f
                    )
                )
                4 -> ChannelMixingMatrix(
                    4, 2,
                    floatArrayOf(
                        1.0f, 0.0f, 0.7071f, 0.0f,
                        0.0f, 1.0f, 0.0f, 0.7071f
                    )
                )
                5 -> ChannelMixingMatrix(
                    5, 2,
                    floatArrayOf(
                        1.0f, 0.0f, 0.7071f, 0.7071f, 0.0f,
                        0.0f, 1.0f, 0.7071f, 0.0f, 0.7071f
                    )
                )
                6 -> ChannelMixingMatrix(
                    6, 2,
                    floatArrayOf(
                        1.0f, 0.0f, 0.7071f, 0.5f, 0.7071f, 0.0f,
                        0.0f, 1.0f, 0.7071f, 0.5f, 0.0f, 0.7071f
                    )
                )
                else -> error("Unsupported input channel count: $inputChannelCount")
            }
            channelMixer.putChannelMixingMatrix(matrix)
        }

        val processors = mutableListOf<AudioProcessor>(ToInt16PcmAudioProcessor(), channelMixer)
        val normalizedVolume = volume.coerceIn(0f, 1f)
        val normalizedFadeIn = fadeInDurationMs.coerceAtLeast(0L)
        val normalizedFadeOut = fadeOutDurationMs.coerceAtLeast(0L)
        if (normalizedVolume != 1f || normalizedFadeIn > 0L || normalizedFadeOut > 0L) {
            processors += FadeGainAudioProcessor(
                volume = normalizedVolume,
                durationMs = durationMs.coerceAtLeast(0L),
                fadeInDurationMs = normalizedFadeIn.coerceAtMost(durationMs.coerceAtLeast(0L)),
                fadeOutDurationMs = normalizedFadeOut.coerceAtMost(durationMs.coerceAtLeast(0L))
            )
        }
        return Effects(processors, emptyList())
    }

    private fun publishToMediaStore(source: File): Uri {
        require(source.isFile && source.length() > 0L) { "Export produced no output file" }
        val resolver = context.contentResolver
        val displayName = "MediaAIStudio_${System.currentTimeMillis()}.mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MediaAIStudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore refused the export destination")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IOException("Could not open MediaStore output stream")

                val ready = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                if (resolver.update(uri, ready, null, null) != 1) {
                    throw IOException("Could not finalize MediaStore export")
                }
                return uri
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        }

        @Suppress("DEPRECATION")
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "MediaAIStudio"
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create Movies/MediaAIStudio")
        }
        val destination = File(directory, displayName)
        try {
            source.inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.TITLE, displayName.removeSuffix(".mp4"))
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, destination.absolutePath)
            }
            return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore refused the legacy export destination")
        } catch (e: Exception) {
            runCatching { destination.delete() }
            throw e
        }
    }

    private fun createSilenceFile(directory: File, durationMs: Long): File {
        val file = File.createTempFile("mediaai_silence_", ".wav", directory)
        writeSilenceWav(file, durationMs)
        return file
    }

    private fun writeSilenceWav(file: File, durationMs: Long) {
        val sampleRate = 48_000
        val channels = 2
        val bytesPerSample = 2
        val sampleCount = silenceFrameCount(durationMs, sampleRate)
        val dataSize = sampleCount * channels * bytesPerSample
        require(dataSize <= Int.MAX_VALUE) { "Silence segment is too large" }

        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeBytes("RIFF")
            writeLeInt(out, (36L + dataSize).toInt())
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            writeLeInt(out, 16)
            writeLeShort(out, 1)
            writeLeShort(out, channels)
            writeLeInt(out, sampleRate)
            writeLeInt(out, sampleRate * channels * bytesPerSample)
            writeLeShort(out, channels * bytesPerSample)
            writeLeShort(out, bytesPerSample * 8)
            out.writeBytes("data")
            writeLeInt(out, dataSize.toInt())

            val buffer = ByteArray(16 * 1024)
            var remaining = dataSize
            while (remaining > 0) {
                val count = min(remaining, buffer.size.toLong()).toInt()
                out.write(buffer, 0, count)
                remaining -= count
            }
        }
    }

    private fun writeLeInt(out: DataOutputStream, value: Int) {
        out.writeByte(value and 0xFF)
        out.writeByte((value ushr 8) and 0xFF)
        out.writeByte((value ushr 16) and 0xFF)
        out.writeByte((value ushr 24) and 0xFF)
    }

    private fun writeLeShort(out: DataOutputStream, value: Int) {
        out.writeByte(value and 0xFF)
        out.writeByte((value ushr 8) and 0xFF)
    }

    private fun cleanup(files: List<File>) {
        files.forEach { runCatching { it.delete() } }
    }
}
