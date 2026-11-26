package com.snapfetch.app.data.compressor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.snapfetch.app.core.datastore.CompressionPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

sealed class CompressionOutcome {
    data class Success(val outputFile: File, val outputBytes: Long) : CompressionOutcome()
    data class Failure(val message: String) : CompressionOutcome()
}

class Media3VideoCompressor(private val context: Context) {

    fun estimateOutputBytes(sourceDurationSec: Int, preset: CompressionPreset): Long {
        val sec = sourceDurationSec.coerceAtLeast(10).toLong()
        return (sec * preset.bitrateKbps.toLong() * 1024L) / 8L
    }

    fun probeDurationSeconds(sourceUri: Uri): Int {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, sourceUri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            (ms / 1000L).toInt()
        }.getOrDefault(0)
    }

    suspend fun compress(
        sourceUri: Uri,
        outputFile: File,
        preset: CompressionPreset,
        onProgress: (Int) -> Unit
    ): CompressionOutcome = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val mediaItem = MediaItem.fromUri(sourceUri)
        val editedBuilder = EditedMediaItem.Builder(mediaItem)

        if (preset.audioOnly) {
            editedBuilder.setRemoveVideo(true)
        } else {
            // Align output height/width cleanly to mod-16 for budget MediaTek/Spreadtrum encoders
            val alignedHeight = (preset.heightPx / 16) * 16
            val effects = listOf<Effect>(Presentation.createForHeight(alignedHeight))
            editedBuilder.setEffects(
                androidx.media3.transformer.Effects(
                    emptyList(),
                    effects
                )
            )
        }

        val transformerBuilder = Transformer.Builder(context)
        if (!preset.audioOnly) {
            transformerBuilder.setVideoMimeType(MimeTypes.VIDEO_H264)
        }
        transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)

        suspendCancellableCoroutine { cont ->
            var settled = false
            val transformer = transformerBuilder
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (!settled) {
                            settled = true
                            val valid = outputFile.exists() && outputFile.length() > 1024L
                            if (valid) {
                                cont.resume(CompressionOutcome.Success(outputFile, outputFile.length()))
                            } else {
                                cont.resume(CompressionOutcome.Failure("Compressed output was empty"))
                            }
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (!settled) {
                            settled = true
                            outputFile.delete()
                            cont.resume(CompressionOutcome.Failure(exportException.message ?: "Hardware encoder failed"))
                        }
                    }
                })
                .build()

            transformer.start(editedBuilder.build(), outputFile.absolutePath)

            val handler = Handler(Looper.getMainLooper())
            val holder = ProgressHolder()
            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!settled && cont.isActive) {
                        val state = transformer.getProgress(holder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress.coerceIn(0, 100))
                        }
                        handler.postDelayed(this, 400L)
                    }
                }
            }
            handler.postDelayed(pollRunnable, 400L)

            cont.invokeOnCancellation {
                handler.removeCallbacks(pollRunnable)
                transformer.cancel()
                outputFile.delete()
            }
        }
    }
}
