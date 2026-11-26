package com.snapfetch.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "mediaKey"], unique = true)
    ]
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val playlistId: String,
    val mediaKey: String,
    val title: String = "",
    val subtitle: String = "",
    val thumbnailUri: String? = null,
    val mediaUri: String = "",
    val durationSec: Int = 0,
    val sizeBytes: Long = 0L,
    val isAudio: Boolean = false,
    val position: Int,
    val addedAtEpochMs: Long
)
