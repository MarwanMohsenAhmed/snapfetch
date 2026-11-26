package com.snapfetch.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAtEpochMs: Long
)

@Entity(tableName = "subscribed_channels")
data class SubscribedChannelEntity(
    @PrimaryKey val channelId: String,
    val name: String,
    val avatarUrl: String,
    val subscriberText: String,
    val url: String,
    val subscribedAtEpochMs: Long
)
