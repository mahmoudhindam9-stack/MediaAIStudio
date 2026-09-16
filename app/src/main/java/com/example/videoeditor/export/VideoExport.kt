package com.example.videoeditor.export

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Transformer.Listener
import androidx.media3.transformer.EditedMediaItemSequence
import com.example.videoeditor.VideoEditorState

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
            // Build the sequence of video clips
            val videoEditedMediaItems = state.videoClips.map { clip ->
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(clip.uri))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.startTrimMs)
                            .setEndPositionMs(clip.startTrimMs + clip.durationMs)
                            .build()
                    )
                    .build()
                
                // Add effects (volume, rotation)
                EditedMediaItem.Builder(mediaItem).build()
            }
            
            val videoSequence = EditedMediaItemSequence(videoEditedMediaItems)
            
            // Transformer composition requires audio sequences as well, but Media3 Transformer 
            // has limited support for complex multi-track mixing in a single Composition easily.
            // For a basic implementation, we will combine video sequences.
            // Advanced ducking might require custom AudioProcessors.
            
            val composition = Composition.Builder(listOf(videoSequence))
                // .experimentalSetForceAudioTrack(true)
                .build()

            val transformer = Transformer.Builder(context)
                .addListener(object : Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
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
}
