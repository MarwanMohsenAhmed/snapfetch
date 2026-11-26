package com.snapfetch.app.data.downloader

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.snapfetch.app.SnapFetchApp
import com.snapfetch.app.domain.model.DownloadStatus
import com.snapfetch.app.domain.model.FormatSizeCalculator
import kotlinx.coroutines.flow.first
import java.io.File
import java.nio.ByteBuffer

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val container = (applicationContext as SnapFetchApp).container
        val dao = container.database.downloadDao()
        val item = dao.getById(downloadId) ?: return Result.success()

        if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.COMPLETED) {
            return Result.success()
        }

        val settings = container.settingsDataStore.settingsFlow.first()
        val remaining = (item.totalBytes - item.resumeOffsetBytes).coerceAtLeast(1024L * 1024L)
        val spaceCheck = container.storageGuard.checkFreeSpace(remaining)
        if (!spaceCheck.hasEnoughSpace) {
            dao.updateStatus(downloadId, DownloadStatus.FAILED, "Low storage: needs 100 MB safety margin")
            return Result.failure()
        }

        val notifId = downloadId.hashCode() and 0x0FFFFFFF
        setForeground(
            container.downloadNotificationHelper.buildProgressForegroundInfo(
                notificationId = notifId,
                title = item.title,
                subtitle = "Starting download...",
                progressPercent = 0,
                indeterminate = true
            )
        )

        dao.updateStatus(downloadId, DownloadStatus.DOWNLOADING, null)
        val partsDir = container.storageScanner.partsDir
        val partFile = File(item.partFilePath ?: File(partsDir, "$downloadId.part").absolutePath)
        if (item.partFilePath == null) {
            dao.upsert(item.copy(status = DownloadStatus.DOWNLOADING, partFilePath = partFile.absolutePath))
        }

        val streamUrl = item.directStreamUrl
        if (streamUrl.isBlank()) {
            // Attempt live extraction if queued before URL resolution
            val refreshed = resolveFreshUrls(container, item.videoId, item.streamPageUrl, item.formatLabel)
            if (refreshed.first.isNullOrBlank()) {
                dao.updateStatus(downloadId, DownloadStatus.FAILED, "No downloadable stream URL found")
                return Result.failure()
            }
            dao.updateStreamUrls(downloadId, refreshed.first!!, refreshed.second)
        }

        val currentEntity = dao.getById(downloadId) ?: return Result.success()

        val result = container.resumableDownloader.downloadToPartFile(
            downloadId = downloadId,
            initialUrl = currentEntity.directStreamUrl,
            partFile = partFile,
            isAudioTrack = false,
            onRefreshUrl = {
                val refreshed = resolveFreshUrls(container, currentEntity.videoId, currentEntity.streamPageUrl, currentEntity.formatLabel)
                if (!refreshed.first.isNullOrBlank()) {
                    dao.updateStreamUrls(downloadId, refreshed.first!!, refreshed.second)
                }
                refreshed.first
            },
            onProgress = { downloaded, total, speed ->
                val pct = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                val sub = "${FormatSizeCalculator.formatBytes(downloaded)} / ${FormatSizeCalculator.formatBytes(total)} • ${FormatSizeCalculator.formatBytes(speed)}/s"
                if (settings.notifyOnProgress) {
                    setForeground(
                        container.downloadNotificationHelper.buildProgressForegroundInfo(
                            notificationId = notifId,
                            title = currentEntity.title,
                            subtitle = sub,
                            progressPercent = pct,
                            indeterminate = total <= 0L
                        )
                    )
                }
            }
        )

        when (result) {
            is DownloadStreamResult.Paused -> {
                dao.updateStatus(downloadId, DownloadStatus.PAUSED, result.reason)
                return Result.success()
            }
            is DownloadStreamResult.Failed -> {
                dao.updateStatus(downloadId, DownloadStatus.FAILED, result.reason)
                return Result.failure()
            }
            is DownloadStreamResult.Success -> {
                var finalSourceFile = result.partFile
                var audioTempFile: File? = null

                val audioUrl = currentEntity.audioStreamUrl
                if (!audioUrl.isNullOrBlank()) {
                    dao.updateStatus(downloadId, DownloadStatus.MERGING, null)
                    audioTempFile = File(partsDir, "${downloadId}_audio.part")
                    val audioRes = container.resumableDownloader.downloadToPartFile(
                        downloadId = downloadId,
                        initialUrl = audioUrl,
                        partFile = audioTempFile,
                        isAudioTrack = true,
                        onRefreshUrl = {
                            resolveFreshUrls(container, currentEntity.videoId, currentEntity.streamPageUrl, currentEntity.formatLabel).second
                        },
                        onProgress = { _, _, _ -> }
                    )
                    if (audioRes is DownloadStreamResult.Success) {
                        val merged = File(partsDir, "${downloadId}_muxed.mp4")
                        val muxOk = muxVideoAndAudio(result.partFile, audioRes.partFile, merged)
                        if (muxOk && merged.exists() && merged.length() > 0L) {
                            finalSourceFile = merged
                        }
                    }
                }

                val ext = when {
                    currentEntity.mimeType.contains("audio") -> if (currentEntity.formatLabel.contains("MP3", true)) "m4a" else "m4a"
                    else -> "mp4"
                }
                val safeName = "${container.mediaStoreSaver.sanitizeFilename(currentEntity.title)}.$ext"
                val savedUri = container.mediaStoreSaver.saveCompletedFile(
                    sourceFile = finalSourceFile,
                    displayName = safeName,
                    mimeType = if (ext == "m4a") "audio/mp4" else "video/mp4",
                    mediaType = currentEntity.mediaType,
                    safTreeUriString = settings.saveFolderUri
                )

                val finalBytes = finalSourceFile.length()
                partFile.delete()
                audioTempFile?.delete()
                if (finalSourceFile != partFile) finalSourceFile.delete()

                dao.markCompleted(
                    id = downloadId,
                    uri = savedUri.toString(),
                    finalSize = finalBytes,
                    completedAt = System.currentTimeMillis()
                )
                container.downloadNotificationHelper.notifyComplete(
                    id = notifId + 1,
                    title = "Download complete",
                    subtitle = currentEntity.title,
                    enabled = settings.notifyOnComplete
                )
                container.downloadCoordinator.kickQueue()
                return Result.success()
            }
        }
    }

    private suspend fun resolveFreshUrls(
        container: com.snapfetch.app.AppContainer,
        videoId: String,
        streamPageUrl: String,
        formatLabel: String
    ): Pair<String?, String?> {
        return runCatching {
            val formats = container.extractorRepository.resolveFreshFormats(streamPageUrl, videoId)
            val chosen = formats.firstOrNull { it.displayLabel.equals(formatLabel, ignoreCase = true) }
                ?: formats.firstOrNull()
            chosen?.streamUrl to chosen?.audioStreamUrl
        }.getOrDefault(null to null)
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outFile: File): Boolean {
        return runCatching {
            val videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            val audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            var muxVideoTrack = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoExtractor.selectTrack(i)
                    muxVideoTrack = muxer.addTrack(format)
                    break
                }
            }

            var audioTrackIndex = -1
            var muxAudioTrack = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioExtractor.selectTrack(i)
                    muxAudioTrack = muxer.addTrack(format)
                    break
                }
            }

            if (muxVideoTrack < 0 && muxAudioTrack < 0) {
                muxer.release()
                videoExtractor.release()
                audioExtractor.release()
                return false
            }

            muxer.start()
            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()

            if (videoTrackIndex >= 0 && muxVideoTrack >= 0) {
                while (true) {
                    val sampleSize = videoExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = videoExtractor.sampleTime
                    info.flags = videoExtractor.sampleFlags
                    muxer.writeSampleData(muxVideoTrack, buffer, info)
                    videoExtractor.advance()
                }
            }

            if (audioTrackIndex >= 0 && muxAudioTrack >= 0) {
                while (true) {
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = audioExtractor.sampleTime
                    info.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(muxAudioTrack, buffer, info)
                    audioExtractor.advance()
                }
            }

            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()
            true
        }.getOrDefault(false)
    }
}
