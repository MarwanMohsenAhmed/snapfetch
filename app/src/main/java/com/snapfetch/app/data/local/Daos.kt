package com.snapfetch.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.snapfetch.app.data.local.entities.DownloadItemEntity
import com.snapfetch.app.data.local.entities.PlaybackPositionEntity
import com.snapfetch.app.data.local.entities.PlaylistEntity
import com.snapfetch.app.data.local.entities.PlaylistItemEntity
import com.snapfetch.app.data.local.entities.SearchHistoryEntity
import com.snapfetch.app.data.local.entities.SubscribedChannelEntity
import com.snapfetch.app.domain.model.DownloadStatus
import com.snapfetch.app.domain.model.PlaylistSortMode
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY queueOrder ASC, createdAtEpochMs DESC")
    fun observeAllDownloads(): Flow<List<DownloadItemEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAtEpochMs DESC, createdAtEpochMs DESC")
    fun observeCompletedDownloads(): Flow<List<DownloadItemEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED', 'MERGING', 'COMPRESSING') ORDER BY queueOrder ASC, createdAtEpochMs ASC")
    fun observeActiveQueue(): Flow<List<DownloadItemEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'PAUSED') ORDER BY queueOrder ASC, createdAtEpochMs ASC")
    suspend fun getPendingOrPaused(): List<DownloadItemEntity>

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY queueOrder ASC, createdAtEpochMs ASC LIMIT :limit")
    suspend fun getNextQueued(limit: Int): List<DownloadItemEntity>

    @Query("SELECT * FROM downloads WHERE downloadId = :id")
    suspend fun getById(id: String): DownloadItemEntity?

    @Query("SELECT COALESCE(MAX(queueOrder), 0) FROM downloads")
    suspend fun getMaxQueueOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DownloadItemEntity>)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, resumeOffsetBytes = :resumeOffset, totalBytes = CASE WHEN :total > 0 THEN :total ELSE totalBytes END, speedBytesPerSec = :speed, etag = COALESCE(:etag, etag), lastModifiedHeader = COALESCE(:lastMod, lastModifiedHeader) WHERE downloadId = :id")
    suspend fun updateProgress(id: String, downloaded: Long, resumeOffset: Long, total: Long, speed: Long, etag: String?, lastMod: String?)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage, speedBytesPerSec = 0 WHERE downloadId = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, errorMessage: String? = null)

    @Query("UPDATE downloads SET directStreamUrl = :streamUrl, audioStreamUrl = :audioUrl WHERE downloadId = :id")
    suspend fun updateStreamUrls(id: String, streamUrl: String, audioUrl: String?)

    @Query("UPDATE downloads SET status = 'COMPLETED', localFileUri = :uri, totalBytes = :finalSize, downloadedBytes = :finalSize, speedBytesPerSec = 0, partFilePath = NULL, errorMessage = NULL, completedAtEpochMs = :completedAt WHERE downloadId = :id")
    suspend fun markCompleted(id: String, uri: String, finalSize: Long, completedAt: Long)

    @Query("UPDATE downloads SET localFileUri = :uri, totalBytes = :newSize, downloadedBytes = :newSize, originalSizeBytes = COALESCE(originalSizeBytes, :oldSize), isCompressed = 1, status = 'COMPLETED', errorMessage = NULL WHERE downloadId = :id")
    suspend fun markCompressed(id: String, uri: String, newSize: Long, oldSize: Long)

    @Query("UPDATE downloads SET status = 'PAUSED', speedBytesPerSec = 0 WHERE status IN ('QUEUED', 'DOWNLOADING')")
    suspend fun pauseAllActive()

    @Query("UPDATE downloads SET status = 'QUEUED', errorMessage = NULL WHERE status IN ('PAUSED', 'FAILED')")
    suspend fun resumeAllPaused()

    @Query("UPDATE downloads SET queueOrder = :order WHERE downloadId = :id")
    suspend fun updateQueueOrder(id: String, order: Int)

    @Transaction
    suspend fun reorderQueue(orderedIds: List<String>) {
        orderedIds.forEachIndexed { idx, id -> updateQueueOrder(id, idx) }
    }

    @Query("DELETE FROM downloads WHERE downloadId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads WHERE batchId = :batchId")
    suspend fun deleteBatch(batchId: String)

    @Query("SELECT partFilePath FROM downloads WHERE partFilePath IS NOT NULL")
    suspend fun getActivePartPaths(): List<String>
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAtEpochMs DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    fun observePlaylist(playlistId: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC, addedAtEpochMs ASC")
    fun observePlaylistItems(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items")
    fun observeAllPlaylistItems(): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistItemsSync(playlistId: String): List<PlaylistItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, updatedAtEpochMs = :updatedAt WHERE playlistId = :playlistId")
    suspend fun renamePlaylist(playlistId: String, name: String, updatedAt: Long)

    @Query("UPDATE playlists SET sortMode = :sortMode, updatedAtEpochMs = :updatedAt WHERE playlistId = :playlistId")
    suspend fun updateSortMode(playlistId: String, sortMode: PlaylistSortMode, updatedAt: Long)

    @Query("UPDATE playlists SET coverUri = :coverUri, updatedAtEpochMs = :updatedAt WHERE playlistId = :playlistId")
    suspend fun updateCover(playlistId: String, coverUri: String?, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaKey = :mediaKey")
    suspend fun removeItem(playlistId: String, mediaKey: String)

    @Query("UPDATE playlist_items SET position = :position WHERE id = :id")
    suspend fun updateItemPosition(id: Long, position: Int)

    @Transaction
    suspend fun reorderPlaylistItems(playlistId: String, orderedItemIds: List<Long>) {
        orderedItemIds.forEachIndexed { index, itemId ->
            updateItemPosition(itemId, index)
        }
        updateSortMode(playlistId, PlaylistSortMode.MANUAL, System.currentTimeMillis())
    }
}

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions ORDER BY lastPlayedAtEpochMs DESC")
    fun observeAll(): Flow<List<PlaybackPositionEntity>>

    @Query("SELECT * FROM playback_positions WHERE mediaKey = :mediaKey")
    suspend fun getPosition(mediaKey: String): PlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackPositionEntity)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAtEpochMs DESC LIMIT 20")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface SubscribedChannelDao {
    @Query("SELECT * FROM subscribed_channels ORDER BY subscribedAtEpochMs DESC")
    fun observeAll(): Flow<List<SubscribedChannelEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM subscribed_channels WHERE channelId = :channelId)")
    fun observeIsSubscribed(channelId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: SubscribedChannelEntity)

    @Query("DELETE FROM subscribed_channels WHERE channelId = :channelId")
    suspend fun delete(channelId: String)
}
