package com.snapfetch.app.domain.model

import androidx.compose.runtime.Immutable

enum class PlaylistSortMode(val label: String) {
    MANUAL("Custom order"),
    DATE_ADDED("Date added"),
    TITLE("Title"),
    DURATION("Duration"),
    FILE_SIZE("File size")
}

@Immutable
data class Playlist(
    val playlistId: String,
    val name: String,
    val coverUri: String?,
    val sortMode: PlaylistSortMode,
    val itemCount: Int = 0,
    val totalDurationSec: Int = 0,
    val totalSizeBytes: Long = 0L,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

@Immutable
data class LibraryMediaItem(
    val mediaKey: String,
    val title: String,
    val subtitle: String,
    val uri: String,
    val thumbnailModel: Any?,
    val mediaType: MediaType,
    val durationSec: Int,
    val sizeBytes: Long,
    val dateAddedMs: Long,
    val isAppDownload: Boolean,
    val downloadId: String? = null,
    val resumePositionMs: Long = 0L,
    val resumeDurationMs: Long = 0L
) {
    val resumeRatio: Float
        get() = if (resumeDurationMs > 0L) {
            (resumePositionMs.toFloat() / resumeDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}
