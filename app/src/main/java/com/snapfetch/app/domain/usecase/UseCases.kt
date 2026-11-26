package com.snapfetch.app.domain.usecase

import android.content.Context
import com.snapfetch.app.core.datastore.CompressionPreset
import com.snapfetch.app.data.compressor.CompressionWorker
import com.snapfetch.app.data.downloader.DownloadQueueCoordinator
import com.snapfetch.app.data.downloader.EnqueueOutcome
import com.snapfetch.app.data.extractor.ExtractedStreamBundle
import com.snapfetch.app.data.extractor.MediaExtractorRepository
import com.snapfetch.app.data.extractor.SearchBundle
import com.snapfetch.app.data.local.SearchHistoryDao
import com.snapfetch.app.data.local.entities.SearchHistoryEntity
import com.snapfetch.app.domain.model.MediaFormatOption
import com.snapfetch.app.domain.model.MediaPlatform
import com.snapfetch.app.domain.model.VideoItem

class SearchMediaUseCase(
    private val extractorRepository: MediaExtractorRepository,
    private val searchHistoryDao: SearchHistoryDao
) {
    suspend operator fun invoke(query: String, platformFilter: MediaPlatform? = null): SearchBundle {
        val clean = query.trim()
        if (clean.isNotEmpty()) {
            searchHistoryDao.upsert(SearchHistoryEntity(clean, System.currentTimeMillis()))
        }
        return extractorRepository.search(clean, platformFilter)
    }
}

class ResolveFormatsUseCase(
    private val extractorRepository: MediaExtractorRepository
) {
    suspend operator fun invoke(videoIdOrUrl: String): ExtractedStreamBundle {
        return extractorRepository.resolveStreamBundle(videoIdOrUrl)
    }
}

class EnqueueDownloadUseCase(
    private val coordinator: DownloadQueueCoordinator
) {
    suspend operator fun invoke(video: VideoItem, format: MediaFormatOption): EnqueueOutcome {
        return coordinator.enqueueSingle(video, format)
    }

    suspend fun enqueueBatch(items: List<Pair<VideoItem, MediaFormatOption>>): EnqueueOutcome {
        return coordinator.enqueueBatch(items)
    }
}

class CompressVideoUseCase(private val context: Context) {
    operator fun invoke(
        sourceUri: String,
        title: String,
        preset: CompressionPreset,
        deleteOriginal: Boolean,
        downloadId: String? = null
    ) {
        CompressionWorker.enqueue(
            context = context,
            sourceUri = sourceUri,
            title = title,
            preset = preset,
            deleteOriginal = deleteOriginal,
            downloadId = downloadId
        )
    }
}
