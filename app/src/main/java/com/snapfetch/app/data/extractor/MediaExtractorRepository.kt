package com.snapfetch.app.data.extractor

import com.snapfetch.app.domain.model.MediaFormatOption
import com.snapfetch.app.domain.model.MediaPlatform
import com.snapfetch.app.domain.model.MusicShelf
import com.snapfetch.app.domain.model.VideoItem
import java.util.concurrent.ConcurrentHashMap

class MediaExtractorRepository(
    private val newPipeClient: NewPipeClient,
    private val webViewSniffer: WebViewSniffer
) {
    private val videoCache = ConcurrentHashMap<String, VideoItem>()
    private val bundleCache = ConcurrentHashMap<String, ExtractedStreamBundle>()

    fun cacheVideos(items: List<VideoItem>) {
        items.forEach { videoCache[it.id] = it }
    }

    fun getCachedVideo(videoId: String): VideoItem? = videoCache[videoId]

    suspend fun getSuggestions(query: String): List<String> {
        val live = newPipeClient.getSuggestions(query)
        return live.ifEmpty { MockPlatformCatalog.fallbackSuggestions(query) }
    }

    suspend fun search(query: String, platformFilter: MediaPlatform? = null): SearchBundle {
        val trimmed = query.trim()
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            val extracted = resolveByUrl(trimmed)
            if (extracted != null) {
                cacheVideos(listOf(extracted.video))
                return SearchBundle(channel = extracted.channel, videos = listOf(extracted.video))
            }
        }

        if (platformFilter != null && platformFilter != MediaPlatform.YOUTUBE && platformFilter != MediaPlatform.SOUNDCLOUD) {
            val cards = MockPlatformCatalog.platformGuidedMockCards(platformFilter, trimmed)
            cacheVideos(cards)
            return SearchBundle(channel = null, videos = cards)
        }

        val live = runCatching { newPipeClient.searchYouTube(trimmed) }.getOrNull()
        val result = if (live != null && live.videos.isNotEmpty()) {
            live
        } else {
            MockPlatformCatalog.shakiraFallbackSearch(trimmed)
        }
        cacheVideos(result.videos)
        return result
    }

    suspend fun getTrendingFeed(): List<VideoItem> {
        val live = runCatching { newPipeClient.getTrendingFeed() }.getOrDefault(emptyList())
        val result = live.ifEmpty { MockPlatformCatalog.defaultYouTubeFeed }
        cacheVideos(result)
        return result
    }

    suspend fun getMusicShelves(): List<MusicShelf> {
        val shelves = MockPlatformCatalog.defaultMusicShelves
        shelves.forEach { shelf -> cacheVideos(shelf.items.map { it.video }) }
        return shelves
    }

    suspend fun resolveStreamBundle(videoIdOrUrl: String): ExtractedStreamBundle {
        bundleCache[videoIdOrUrl]?.let { return it }

        val targetUrl = videoCache[videoIdOrUrl]?.streamPageUrl?.takeIf { it.isNotBlank() }
            ?: videoIdOrUrl

        val extracted = resolveByUrl(targetUrl)
        if (extracted != null) {
            cacheVideos(listOf(extracted.video) + extracted.relatedVideos)
            bundleCache[extracted.video.id] = extracted
            bundleCache[videoIdOrUrl] = extracted
            return extracted
        }

        val fallbackVideo = videoCache[videoIdOrUrl]
            ?: MockPlatformCatalog.defaultYouTubeFeed.firstOrNull { it.id == videoIdOrUrl }
            ?: MockPlatformCatalog.shakiraFallbackSearch("shakira").videos.first()

        return MockPlatformCatalog.defaultFallbackBundle(fallbackVideo)
    }

    suspend fun resolveFreshFormats(streamPageUrl: String, videoId: String): List<MediaFormatOption> {
        bundleCache.remove(videoId)
        bundleCache.remove(streamPageUrl)
        return resolveStreamBundle(streamPageUrl.ifBlank { videoId }).formats
    }

    private suspend fun resolveByUrl(urlOrId: String): ExtractedStreamBundle? {
        val platform = webViewSniffer.detectPlatform(urlOrId)
        return if (!urlOrId.startsWith("http") ||
            platform == MediaPlatform.YOUTUBE ||
            platform == MediaPlatform.SOUNDCLOUD
        ) {
            runCatching { newPipeClient.getStreamDetails(urlOrId) }.getOrNull()
        } else {
            runCatching { webViewSniffer.extractPublicSocialUrl(urlOrId) }.getOrNull()
        }
    }
}
