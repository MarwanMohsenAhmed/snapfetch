package com.snapfetch.app.data.downloader

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.snapfetch.app.core.datastore.UserSettingsDataStore
import com.snapfetch.app.core.perf.StorageGuard
import com.snapfetch.app.core.storage.StorageScanner
import com.snapfetch.app.data.local.DownloadDao
import com.snapfetch.app.data.local.entities.DownloadItemEntity
import com.snapfetch.app.domain.model.DownloadStatus
import com.snapfetch.app.domain.model.FormatCategory
import com.snapfetch.app.domain.model.MediaFormatOption
import com.snapfetch.app.domain.model.MediaType
import com.snapfetch.app.domain.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed class EnqueueOutcome {
    data class Enqueued(val count: Int) : EnqueueOutcome()
    data class BlockedLowStorage(val availableBytes: Long, val requiredBytes: Long) : EnqueueOutcome()
}

class DownloadQueueCoordinator(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val settingsDataStore: UserSettingsDataStore,
    private val storageGuard: StorageGuard,
    private val storageScanner: StorageScanner
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)

    fun registerNetworkAutoResume() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        val settings = settingsDataStore.settingsFlow.first()
                        if (settings.autoResumeOnNetworkRestore) {
                            val paused = downloadDao.getPendingOrPaused()
                                .filter {
                                    it.status == DownloadStatus.PAUSED &&
                                        (it.errorMessage?.contains("network", true) == true ||
                                            it.errorMessage?.contains("Wi-Fi", true) == true)
                                }
                            if (paused.isNotEmpty()) {
                                paused.forEach { downloadDao.updateStatus(it.downloadId, DownloadStatus.QUEUED, null) }
                                kickQueue()
                            }
                        }
                    }
                }
            })
        }
    }

    suspend fun enqueueSingle(video: VideoItem, format: MediaFormatOption): EnqueueOutcome {
        return enqueueBatch(listOf(video to format))
    }

    suspend fun enqueueBatch(items: List<Pair<VideoItem, MediaFormatOption>>): EnqueueOutcome {
        if (items.isEmpty()) return EnqueueOutcome.Enqueued(0)
        val totalEst = items.sumOf { it.second.estimatedSizeBytes.coerceAtLeast(1024L * 1024L) }
        val check = storageGuard.checkFreeSpace(totalEst)
        if (!check.hasEnoughSpace) {
            return EnqueueOutcome.BlockedLowStorage(check.availableBytes, check.requiredBytes)
        }

        val batchId = if (items.size > 1) UUID.randomUUID().toString() else null
        var nextOrder = downloadDao.getMaxQueueOrder() + 1
        val now = System.currentTimeMillis()
        val entities = items.map { (video, format) ->
            val id = UUID.randomUUID().toString()
            val mediaType = if (format.category == FormatCategory.MUSIC) MediaType.MUSIC else MediaType.VIDEO
            val mime = if (format.category == FormatCategory.MUSIC) "audio/mp4" else "video/mp4"
            DownloadItemEntity(
                downloadId = id,
                batchId = batchId,
                queueOrder = nextOrder++,
                videoId = video.id,
                streamPageUrl = video.streamPageUrl,
                directStreamUrl = format.streamUrl,
                audioStreamUrl = format.audioStreamUrl,
                title = video.title,
                channelName = video.channelName,
                thumbnailUrl = video.thumbnailUrl,
                platform = video.platform.name,
                mediaType = mediaType,
                formatLabel = format.displayLabel,
                mimeType = mime,
                totalBytes = format.estimatedSizeBytes,
                downloadedBytes = 0L,
                resumeOffsetBytes = 0L,
                etag = null,
                lastModifiedHeader = null,
                partFilePath = File(storageScanner.partsDir, "$id.part").absolutePath,
                status = DownloadStatus.QUEUED,
                speedBytesPerSec = 0L,
                localFileUri = null,
                originalSizeBytes = null,
                isCompressed = false,
                errorMessage = null,
                durationSec = video.durationSec,
                createdAtEpochMs = now,
                completedAtEpochMs = null
            )
        }
        downloadDao.upsertAll(entities)
        kickQueue()
        return EnqueueOutcome.Enqueued(entities.size)
    }

    suspend fun kickQueue() {
        val settings = settingsDataStore.settingsFlow.first()
        val all = downloadDao.getPendingOrPaused()
        val nextQueued = all.filter { it.status == DownloadStatus.QUEUED }
            .take(settings.maxConcurrentDownloads.coerceIn(1, 3))

        nextQueued.forEach { entity ->
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(Data.Builder().putString(DownloadWorker.KEY_DOWNLOAD_ID, entity.downloadId).build())
                .addTag("download_${entity.downloadId}")
                .build()
            workManager.enqueueUniqueWork(
                "dl_${entity.downloadId}",
                ExistingWorkPolicy.KEEP,
                req
            )
        }
    }

    suspend fun pauseDownload(downloadId: String) {
        downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED, "Paused by user")
        workManager.cancelUniqueWork("dl_$downloadId")
    }

    suspend fun resumeDownload(downloadId: String) {
        downloadDao.updateStatus(downloadId, DownloadStatus.QUEUED, null)
        kickQueue()
    }

    suspend fun pauseAll() {
        downloadDao.pauseAllActive()
        workManager.cancelAllWorkByTag("download")
    }

    suspend fun resumeAll() {
        downloadDao.resumeAllPaused()
        kickQueue()
    }

    suspend fun cancelDownload(downloadId: String) {
        val item = downloadDao.getById(downloadId)
        workManager.cancelUniqueWork("dl_$downloadId")
        item?.partFilePath?.let { File(it).delete() }
        downloadDao.deleteById(downloadId)
    }

    suspend fun cancelBatch(batchId: String) {
        val items = downloadDao.getPendingOrPaused().filter { it.batchId == batchId }
        items.forEach { item ->
            workManager.cancelUniqueWork("dl_${item.downloadId}")
            item.partFilePath?.let { File(it).delete() }
        }
        downloadDao.deleteBatch(batchId)
    }

    suspend fun reorderQueue(orderedIds: List<String>) {
        downloadDao.reorderQueue(orderedIds)
    }
}
