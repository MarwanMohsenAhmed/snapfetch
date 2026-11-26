package com.snapfetch.app.data.local

import androidx.room.TypeConverter
import com.snapfetch.app.domain.model.DownloadStatus
import com.snapfetch.app.domain.model.MediaType
import com.snapfetch.app.domain.model.PlaylistSortMode

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = runCatching { MediaType.valueOf(value) }.getOrDefault(MediaType.VIDEO)

    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.QUEUED)

    @TypeConverter
    fun fromPlaylistSortMode(value: PlaylistSortMode): String = value.name

    @TypeConverter
    fun toPlaylistSortMode(value: String): PlaylistSortMode =
        runCatching { PlaylistSortMode.valueOf(value) }.getOrDefault(PlaylistSortMode.MANUAL)
}
