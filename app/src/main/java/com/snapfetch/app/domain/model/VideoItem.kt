package com.snapfetch.app.domain.model

import androidx.compose.runtime.Immutable

enum class MediaPlatform(val displayName: String) {
    YOUTUBE("YouTube"),
    TIKTOK("TikTok"),
    FACEBOOK("Facebook"),
    INSTAGRAM("Instagram"),
    SOUNDCLOUD("SoundCloud"),
    OTHER("Web")
}

@Immutable
data class VideoItem(
    val id: String,
    val platform: MediaPlatform = MediaPlatform.YOUTUBE,
    val title: String,
    val channelName: String,
    val channelAvatarUrl: String = "",
    val channelSubscriberText: String = "",
    val thumbnailUrl: String = "",
    val durationSec: Int = 0,
    val viewCountText: String = "",
    val streamPageUrl: String = "",
    val isPlaylist: Boolean = false,
    val playlistCount: Int = 0
) {
    fun formattedDuration(): String = formatDurationSeconds(durationSec)
}

fun formatDurationSeconds(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0:00"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
