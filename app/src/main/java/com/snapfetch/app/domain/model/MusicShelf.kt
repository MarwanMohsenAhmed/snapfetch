package com.snapfetch.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class MusicShelf(
    val id: String,
    val title: String,
    val items: List<MusicShelfItem>
)

@Immutable
data class MusicShelfItem(
    val video: VideoItem,
    val artistSubtitle: String,
    val isMixOrAlbum: Boolean = false
)
