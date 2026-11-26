package com.snapfetch.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.snapfetch.app.data.local.entities.DownloadItemEntity
import com.snapfetch.app.data.local.entities.PlaybackPositionEntity
import com.snapfetch.app.data.local.entities.PlaylistEntity
import com.snapfetch.app.data.local.entities.PlaylistItemEntity
import com.snapfetch.app.data.local.entities.SearchHistoryEntity
import com.snapfetch.app.data.local.entities.SubscribedChannelEntity

@Database(
    entities = [
        DownloadItemEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlaybackPositionEntity::class,
        SearchHistoryEntity::class,
        SubscribedChannelEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun subscribedChannelDao(): SubscribedChannelDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "snapfetch.db"
            ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }
}
