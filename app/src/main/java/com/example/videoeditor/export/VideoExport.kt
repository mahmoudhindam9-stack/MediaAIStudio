package com.example.videoeditor.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
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
        var isTerminal = false

        fun terminateAndCleanup(action: () -> Unit) {
            if (isTerminal) return
            isTerminal = true
            progressRunnable?.let { handler.removeCallbacks(it) }
            progressRunnable = null
            cleanup(temporaryFiles)
            action()
        }

        try {
            require(state.durationMs > 0L) { "Cannot export an empty timeline" }
            val renderPlan = state.toVideoRenderPlan()
            validateRenderPlan(renderPlan, state.durationMs)

            val outputFile = File(context.cacheDir, "mediaai_export_${System.currentTimeMillis()}.mp4")
            temporaryFiles += outputFile
            onProgress(0)

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
                if (item.isImage) editedBuilder.setFrameRate(30)
                if (item.muted || item.volume < 1f) {
                    if (item.muted) {
                        editedBuilder.setRemoveAudio(true)
                    } else if (!item.isImage) {
                        editedBuilder.setEffects(
                            Effects(
                                listOf(FadeGainAudioProcessor(item.volume, item.trimEndMs - item.trimStartMs, 0L, 0L)),
                                emptyList()
                            )
                        )
                    }
                }
                editedBuilder.build()
            }

            val sequences = mutableListOf(EditedMediaItemSequence(videoItems))
            state.audioTracks.forEach { track ->
                if (track.isMuted || track.durationMs <= 0L) return@forEach
                val startAt = track.startTimeMs.coerceAtLeast(0L)
                if (startAt >= state.durationMs) return@forEach
                val availableDuration = min(track.durationMs, state.durationMs - startAt)
                if (availableDuration <= 0L) return@forEach

                val items = mutableListOf<EditedMediaItem>()
                if (startAt > 0L) {
                    val silenceFile = createSilenceFile(context.cacheDir, startAt)
                    temporaryFiles += silenceFile
                    items += EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(silenceFile))).build()
                }

                val sourceStart = track.startTrimMs.coerceIn(0L, track.originalDurationMs)
                val sourceEnd = (sourceStart + availableDuration).coerceAtMost(track.originalDurationMs)
                if (sourceEnd <= sourceStart) return@forEach
                val actualDuration = sourceEnd - sourceStart

                val audioItem = MediaItem.Builder()
                    .setUri(Uri.parse(track.uri))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(sourceStart)
                            .setEndPositionMs(sourceEnd)
                            .build()
                    )
                    .build()
                val processors = if (
                    track.volume != 1f || track.fadeInDurationMs > 0L || track.fadeOutDurationMs > 0L
                ) {
                    listOf(
                        FadeGainAudioProcessor(
                            volume = track.volume,
                            durationMs = actualDuration,
                            fadeInDurationMs = track.fadeInDurationMs.coerceAtMost(actualDuration),
                            fadeOutDurationMs = track.fadeOutDurationMs.coerceAtMost(actualDuration)
                        )
                    )
                } else {
                    emptyList()
                }
                items += EditedMediaItem.Builder(audioItem)
                    .apply {
                        if (processors.isNotEmpty()) {
                            setEffects(Effects(processors, emptyList()))
                        }
                    }
                    .build()
                sequences += EditedMediaItemSequence(items)
            }

            val composition = Composition.Builder(sequences).build()
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        try {
                            val publishedUri = publishToMediaStore(outputFile)
                            terminateAndCleanup {
                                onProgress(100)
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

            transformer.start(composition, outputFile.absolutePath)
            
            val holder = ProgressHolder()
            progressRunnable = object : Runnable {
                override fun run() {
                    if (isTerminal) return
                    val stateCode = runCatching { transformer.getProgress(holder) }.getOrNull()
                    if (stateCode == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress)
                    }
                    if (stateCode != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        handler.postDelayed(this, 250L)
                    }
                }
            }
            handler.post(progressRunnable!!)
        } catch (e: Exception) {
            terminateAndCleanup { onError(e) }
        }
    }

    private fun validateRenderPlan(plan: List<VideoRenderItem>, projectDurationMs: Long) {
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
        require(expectedStart == projectDurationMs) {
            "Video timeline duration mismatch: expected $projectDurationMs, got $expectedStart"
        }
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
                resolver.update(uri, ready, null, null)
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
        source.inputStream().use { input -> destination.outputStream().use { input.copyTo(it) } }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.TITLE, displayName.removeSuffix(".mp4"))
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            @Suppress("DEPRECATION")
            put(MediaStore.Video.Media.DATA, destination.absolutePath)
        }
        return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: run {
                destination.delete()
                throw IOException("MediaStore refused the legacy export destination")
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
        val sampleCount = (durationMs.coerceAtLeast(0L) * sampleRate) / 1_000L
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
