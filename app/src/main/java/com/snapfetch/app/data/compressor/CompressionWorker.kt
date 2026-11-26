package com.snapfetch.app.data.compressor

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.snapfetch.app.SnapFetchApp
import com.snapfetch.app.core.datastore.CompressionPreset
import com.snapfetch.app.domain.model.MediaType
import kotlinx.coroutines.flow.first
import java.io.File

class CompressionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_TITLE = "title"
        const val KEY_PRESET = "preset"
        const val KEY_DELETE_ORIGINAL = "delete_original"
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val UNIQUE_COMPRESSION_WORK = "snapfetch_serial_compression"

        fun enqueue(
            context: Context,
            sourceUri: String,
            title: String,
            preset: CompressionPreset,
            deleteOriginal: Boolean,
            downloadId: String? = null
        ) {
            val data = Data.Builder()
                .putString(KEY_SOURCE_URI, sourceUri)
                .putString(KEY_TITLE, title)
                .putString(KEY_PRESET, preset.name)
                .putBoolean(KEY_DELETE_ORIGINAL, deleteOriginal)
                .apply { if (downloadId != null) putString(KEY_DOWNLOAD_ID, downloadId) }
                .build()

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val req = OneTimeWorkRequestBuilder<CompressionWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .build()

            // Serialized compression (maxConcurrentCompressions = 1)
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_COMPRESSION_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                req
            )
        }
    }

    override suspend fun doWork(): Result {
        val sourceUriStr = inputData.getString(KEY_SOURCE_URI) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Video"
        val presetName = inputData.getString(KEY_PRESET) ?: CompressionPreset.SMALL_360P_500K.name
        val preset = runCatching { CompressionPreset.valueOf(presetName) }.getOrDefault(CompressionPreset.SMALL_360P_500K)
        val deleteOriginal = inputData.getBoolean(KEY_DELETE_ORIGINAL, false)
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID)

        val container = (applicationContext as SnapFetchApp).container
        val sourceUri = Uri.parse(sourceUriStr)
        val dur = container.videoCompressor.probeDurationSeconds(sourceUri)
        val estOut = container.videoCompressor.estimateOutputBytes(dur, preset)
        val guard = container.storageGuard.checkFreeSpace(estOut)
        if (!guard.hasEnoughSpace) {
            return Result.failure()
        }

        val notifId = (sourceUriStr.hashCode() and 0x0FFFFFFF) + 50_000
        setForeground(
            container.downloadNotificationHelper.buildProgressForegroundInfo(
                notificationId = notifId,
                title = "Reducing size: $title",
                subtitle = preset.label,
                progressPercent = 0,
                indeterminate = false
            )
        )

        val tmpDir = File(applicationContext.cacheDir, "compress_tmp").apply { mkdirs() }
        val ext = if (preset.audioOnly) "m4a" else "mp4"
        val tmpOut = File(tmpDir, "cmp_${System.currentTimeMillis()}.$ext")

        val outcome = container.videoCompressor.compress(
            sourceUri = sourceUri,
            outputFile = tmpOut,
            preset = preset
        ) { pct ->
            setProgressAsync(Data.Builder().putInt("progress", pct).build())
        }

        return when (outcome) {
            is CompressionOutcome.Failure -> {
                tmpOut.delete()
                Result.failure()
            }
            is CompressionOutcome.Success -> {
                val settings = container.settingsDataStore.settingsFlow.first()
                val suffix = if (preset.audioOnly) "_audio.m4a" else "_${preset.heightPx}p.mp4"
                val name = "${container.mediaStoreSaver.sanitizeFilename(title)}$suffix"
                val savedUri = container.mediaStoreSaver.saveCompletedFile(
                    sourceFile = outcome.outputFile,
                    displayName = name,
                    mimeType = if (preset.audioOnly) "audio/mp4" else "video/mp4",
                    mediaType = if (preset.audioOnly) MediaType.MUSIC else MediaType.VIDEO,
                    safTreeUriString = settings.saveFolderUri
                )
                val oldSize = downloadId?.let { container.database.downloadDao().getById(it)?.totalBytes } ?: 0L
                outcome.outputFile.delete()

                if (deleteOriginal) {
                    if (sourceUri.scheme == "file") {
                        sourceUri.path?.let { File(it).delete() }
                    } else {
                        runCatching { applicationContext.contentResolver.delete(sourceUri, null, null) }
                    }
                }

                if (downloadId != null && !preset.audioOnly) {
                    container.database.downloadDao().markCompressed(
                        id = downloadId,
                        uri = savedUri.toString(),
                        newSize = outcome.outputBytes,
                        oldSize = oldSize
                    )
                }

                container.downloadNotificationHelper.notifyComplete(
                    id = notifId + 1,
                    title = "Size reduction complete",
                    subtitle = "$title • ${preset.label}",
                    enabled = settings.notifyOnComplete
                )
                Result.success()
            }
        }
    }
}
