package com.snapfetch.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.snapfetch.app.domain.model.PlaylistSortMode

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val playlistId: String,
    val name: String,
    val coverUri: String?,
    val sortMode: PlaylistSortMode = PlaylistSortMode.MANUAL,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
