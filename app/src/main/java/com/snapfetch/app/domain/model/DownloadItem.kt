package com.snapfetch.app.domain.model

import androidx.compose.runtime.Immutable

enum class MediaType { VIDEO, MUSIC, PICTURE }

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    MERGING,
    COMPRESSING,
    COMPLETED,
    FAILED
}

@Immutable
data class DownloadItem(
    val downloadId: String,
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
    val progressFraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
