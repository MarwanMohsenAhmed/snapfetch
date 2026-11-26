package com.snapfetch.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val mediaKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val playbackSpeed: Float = 1.0f,
    val lastPlayedAtEpochMs: Long,
    val isFinished: Boolean = false
)
