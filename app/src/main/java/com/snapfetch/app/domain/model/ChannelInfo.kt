package com.snapfetch.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class ChannelInfo(
    val channelId: String,
    val name: String,
    val avatarUrl: String,
    val subscriberText: String,
    val url: String = "",
    val isSubscribed: Boolean = false
)

@Immutable
data class CommentItem(
    val id: String,
    val author: String,
    val avatarUrl: String = "",
    val text: String,
    val likeCountText: String = "",
    val publishedText: String = ""
)
