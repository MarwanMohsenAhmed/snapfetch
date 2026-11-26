package com.snapfetch.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.snapfetch.app.domain.model.DownloadItem
import com.snapfetch.app.domain.model.DownloadStatus
import com.snapfetch.app.domain.model.MediaType

@Entity(tableName = "downloads")
data class DownloadItemEntity(
    @PrimaryKey val downloadId: String,
    val batchId: String?,
    val queueOrder: Int,
    val videoId: String,
    val streamPageUrl: String,
    val directStreamUrl: String,
    val audioStreamUrl: String?,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val platform: String,
    val mediaType: MediaType,
    val formatLabel: String,
    val mimeType: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val resumeOffsetBytes: Long,
    val etag: String?,
    val lastModifiedHeader: String?,
    val partFilePath: String?,
    val status: DownloadStatus,
    val speedBytesPerSec: Long,
    val localFileUri: String?,
    val originalSizeBytes: Long?,
    val isCompressed: Boolean,
    val errorMessage: String?,
    val durationSec: Int = 0,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long?
) {
    fun toDomain(): DownloadItem = DownloadItem(
        downloadId = downloadId,
        batchId = batchId,
        queueOrder = queueOrder,
        videoId = videoId,
        streamPageUrl = streamPageUrl,
        directStreamUrl = directStreamUrl,
        audioStreamUrl = audioStreamUrl,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        platform = platform,
        mediaType = mediaType,
        formatLabel = formatLabel,
        mimeType = mimeType,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        resumeOffsetBytes = resumeOffsetBytes,
        status = status,
        speedBytesPerSec = speedBytesPerSec,
        localFileUri = localFileUri,
        originalSizeBytes = originalSizeBytes,
        isCompressed = isCompressed,
        errorMessage = errorMessage,
        durationSec = durationSec,
        createdAtEpochMs = createdAtEpochMs,
        completedAtEpochMs = completedAtEpochMs
    )
}
