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
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
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
        outputFilePath: String,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val temporaryFiles = mutableListOf<File>()
        try {
            val renderPlan = state.toVideoRenderPlan()
            validateRenderPlan(renderPlan)
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
                if (!item.isImage && item.muted) editedBuilder.setRemoveAudio(true)
                editedBuilder.build()
            }

            // Media3 mixes concurrent audio sequences in a Composition. The first sequence is the
            // ordered video timeline; every additional sequence is one independent audio track.
            val sequences = mutableListOf(EditedMediaItemSequence(videoItems))
            val silenceLibrary = if (state.audioTracks.any { it.startTimeMs > 0L && !it.isMuted }) {
                createSilenceLibrary().also { temporaryFiles += it.allFiles }
            } else {
                null
            }

            state.audioTracks.forEach { track ->
                if (track.isMuted || track.durationMs <= 0L) return@forEach
                val startAt = track.startTimeMs.coerceAtLeast(0L)
                if (startAt >= state.durationMs) return@forEach
                val availableDuration = min(track.durationMs, state.durationMs - startAt)
                if (availableDuration <= 0L) return@forEach

                val items = mutableListOf<EditedMediaItem>()
                if (startAt > 0L) {
                    requireNotNull(silenceLibrary) { "Silence library was not created" }
                    appendSilence(items, startAt, silenceLibrary)
                }

                val sourceStart = track.startTrimMs.coerceIn(0L, track.originalDurationMs)
                val sourceEnd = (sourceStart + availableDuration).coerceAtMost(track.originalDurationMs)
                if (sourceEnd <= sourceStart) return@forEach

                val audioItem = MediaItem.Builder()
                    .setUri(Uri.parse(track.uri))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(sourceStart)
                            .setEndPositionMs(sourceEnd)
                            .build()
                    )
                    .build()
                items += EditedMediaItem.Builder(audioItem).build()
                sequences += EditedMediaItemSequence(items)
            }

            val composition = Composition.Builder(sequences).build()
            val transformer = Transformer.Builder(context)
                .addListener(object : Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        cleanup(temporaryFiles)
                        onProgress(100)
                        onSuccess()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        cleanup(temporaryFiles)
                        onError(exportException)
                    }
                })
                .build()

            transformer.start(composition, outputFilePath)
        } catch (e: Exception) {
            cleanup(temporaryFiles)
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

    private data class SilenceLibrary(
        val tenSecond: File,
        val oneSecond: File,
        val hundredMs: File,
        val tenMs: File
    ) {
        val allFiles: List<File> get() = listOf(tenSecond, oneSecond, hundredMs, tenMs)
    }

    private fun createSilenceLibrary(): SilenceLibrary {
        val dir = context.cacheDir
        return SilenceLibrary(
            tenSecond = createSilenceFile(dir, 10_000L),
            oneSecond = createSilenceFile(dir, 1_000L),
            hundredMs = createSilenceFile(dir, 100L),
            tenMs = createSilenceFile(dir, 10L)
        )
    }

    private fun createSilenceFile(directory: File, durationMs: Long): File {
        val file = File.createTempFile("mediaai_silence_", ".wav", directory)
        writeSilenceWav(file, durationMs)
        return file
    }

    private fun appendSilence(items: MutableList<EditedMediaItem>, durationMs: Long, library: SilenceLibrary) {
        var remaining = durationMs
        fun add(file: File, duration: Long) {
            while (remaining >= duration) {
                items += EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file))).build()
                remaining -= duration
            }
        }
        add(library.tenSecond, 10_000L)
        add(library.oneSecond, 1_000L)
        add(library.hundredMs, 100L)
        add(library.tenMs, 10L)
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
