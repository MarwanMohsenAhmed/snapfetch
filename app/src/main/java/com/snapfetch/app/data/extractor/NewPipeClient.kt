package com.snapfetch.app.data.extractor

import com.snapfetch.app.domain.model.ChannelInfo
import com.snapfetch.app.domain.model.CommentItem
import com.snapfetch.app.domain.model.FormatCategory
import com.snapfetch.app.domain.model.FormatSizeCalculator
import com.snapfetch.app.domain.model.MediaFormatOption
import com.snapfetch.app.domain.model.MediaPlatform
import com.snapfetch.app.domain.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.atomic.AtomicBoolean

data class ExtractedStreamBundle(
    val video: VideoItem,
    val channel: ChannelInfo,
    val formats: List<MediaFormatOption>,
    val relatedVideos: List<VideoItem>,
    val comments: List<CommentItem>,
    val previewStreamUrl: String?
)

class NewPipeClient(private val okHttpClient: OkHttpClient) {
    private val initialized = AtomicBoolean(false)

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(OkHttpNewPipeDownloader(okHttpClient))
        }
    }

    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            ensureInit()
            ServiceList.YouTube.suggestionExtractor.suggestionList(query).take(10)
        }.getOrDefault(emptyList())
    }

    suspend fun searchYouTube(query: String): SearchBundle = withContext(Dispatchers.IO) {
        ensureInit()
        val info = SearchInfo.getInfo(ServiceList.YouTube, ServiceList.YouTube.searchQHFactory.fromQuery(query))
        val items = mutableListOf<VideoItem>()
        var topChannel: ChannelInfo? = null

        for (item in info.relatedItems) {
            when (item) {
                is StreamInfoItem -> items.add(item.toVideoItem())
                is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> {
                    if (topChannel == null) {
                        topChannel = ChannelInfo(
                            channelId = item.url ?: item.name,
                            name = item.name ?: "",
                            avatarUrl = item.thumbnails.firstOrNull()?.url ?: "",
                            subscriberText = formatSubs(item.subscriberCount, item.streamCount),
                            url = item.url ?: ""
                        )
                    }
                }
                is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> {
                    items.add(
                        VideoItem(
                            id = extractVideoIdFromUrl(item.url ?: item.name),
                            platform = MediaPlatform.YOUTUBE,
                            title = item.name ?: "Playlist",
                            channelName = item.uploaderName ?: "YouTube",
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                            durationSec = 0,
                            streamPageUrl = item.url ?: "",
                            isPlaylist = true,
                            playlistCount = item.streamCount.toInt().coerceAtLeast(1)
                        )
                    )
                }
            }
        }
        SearchBundle(channel = topChannel, videos = items)
    }

    suspend fun getTrendingFeed(): List<VideoItem> = withContext(Dispatchers.IO) {
        ensureInit()
        val kiosk = KioskInfo.getInfo(ServiceList.YouTube, ServiceList.YouTube.kioskList.defaultKioskExtractor.url)
        kiosk.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }
    }

    suspend fun getStreamDetails(urlOrId: String): ExtractedStreamBundle = withContext(Dispatchers.IO) {
        ensureInit()
        val targetUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.youtube.com/watch?v=$urlOrId"
        val service = NewPipe.getServiceByUrl(targetUrl)
        val info = StreamInfo.getInfo(service, targetUrl)
        val durationSec = info.duration.toInt().coerceAtLeast(0)

        val video = VideoItem(
            id = info.id ?: extractVideoIdFromUrl(targetUrl),
            platform = if (service == ServiceList.SoundCloud) MediaPlatform.SOUNDCLOUD else MediaPlatform.YOUTUBE,
            title = info.name ?: "Video",
            channelName = info.uploaderName ?: "Creator",
            channelAvatarUrl = info.uploaderAvatars.firstOrNull()?.url ?: "",
            channelSubscriberText = if (info.uploaderSubscriberCount > 0) formatCompactCount(info.uploaderSubscriberCount) + " subscribers" else "",
            thumbnailUrl = info.thumbnails.maxByOrNull { it.width }?.url ?: "",
            durationSec = durationSec,
            viewCountText = if (info.viewCount > 0) formatCompactCount(info.viewCount) + " views" else "",
            streamPageUrl = info.url ?: targetUrl
        )

        val channel = ChannelInfo(
            channelId = info.uploaderUrl ?: video.channelName,
            name = video.channelName,
            avatarUrl = video.channelAvatarUrl,
            subscriberText = video.channelSubscriberText,
            url = info.uploaderUrl ?: ""
        )

        val bestAudio = info.audioStreams
            .filter { !it.content.isNullOrBlank() }
            .sortedByDescending { it.averageBitrate }
            .firstOrNull()

        val formats = buildFormatOptions(info, durationSec, bestAudio?.content)

        val related = info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { it.toVideoItem() }
            .take(15)

        val comments = runCatching {
            val cInfo = CommentsInfo.getInfo(service, targetUrl)
            cInfo.relatedItems.take(15).mapIndexed { index, c ->
                CommentItem(
                    id = "${info.id}_c_$index",
                    author = c.uploaderName ?: "User",
                    avatarUrl = c.uploaderAvatars.firstOrNull()?.url ?: "",
                    text = c.commentText?.content ?: "",
                    likeCountText = if (c.likeCount > 0) c.likeCount.toString() else "",
                    publishedText = c.textualUploadDate ?: ""
                )
            }
        }.getOrDefault(emptyList())

        val previewUrl = info.videoStreams
            .filter { !it.content.isNullOrBlank() }
            .minByOrNull { kotlin.math.abs((it.height) - 360) }
            ?.content ?: bestAudio?.content

        ExtractedStreamBundle(
            video = video,
            channel = channel,
            formats = formats,
            relatedVideos = related,
            comments = comments,
            previewStreamUrl = previewUrl
        )
    }

    suspend fun getPlaylistStreams(url: String): List<VideoItem> = withContext(Dispatchers.IO) {
        ensureInit()
        val info = PlaylistInfo.getInfo(NewPipe.getServiceByUrl(url), url)
        info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }
    }

    private fun buildFormatOptions(
        info: StreamInfo,
        durationSec: Int,
        bestAudioUrl: String?
    ): List<MediaFormatOption> {
        val out = mutableListOf<MediaFormatOption>()

        val m4aStream = info.audioStreams
            .filter { !it.content.isNullOrBlank() && (it.format?.suffix?.equals("m4a", true) == true) }
            .maxByOrNull { it.averageBitrate }
            ?: info.audioStreams.firstOrNull { !it.content.isNullOrBlank() }

        val m4aBytes = m4aStream?.itagItem?.contentLength?.takeIf { it > 0 }
            ?: FormatSizeCalculator.estimateFallbackBytes(durationSec, "m4a", "")

        out += MediaFormatOption(
            formatId = "music_fast_m4a",
            category = FormatCategory.MUSIC,
            displayLabel = "Fast",
            container = "m4a",
            audioBitrateKbps = m4aStream?.averageBitrate?.takeIf { it > 0 } ?: 128,
            streamUrl = m4aStream?.content ?: bestAudioUrl.orEmpty(),
            estimatedSizeBytes = m4aBytes,
            isQuickPreset = true
        )

        val mp3Bytes = FormatSizeCalculator.estimateFallbackBytes(durationSec, "mp3", "")
        out += MediaFormatOption(
            formatId = "music_classic_mp3",
            category = FormatCategory.MUSIC,
            displayLabel = "Classic MP3",
            container = "mp3",
            audioBitrateKbps = 128,
            streamUrl = m4aStream?.content ?: bestAudioUrl.orEmpty(),
            estimatedSizeBytes = mp3Bytes,
            isQuickPreset = true
        )

        // Progressive or adaptive video streams mapped cleanly
        val muxed = info.videoStreams.filter { !it.content.isNullOrBlank() }
        val videoOnly = info.videoOnlyStreams.filter { !it.content.isNullOrBlank() }

        fun findVideoForHeight(targetHeight: Int): Pair<String, Long>? {
            val m = muxed.filter { it.height == targetHeight }.maxByOrNull { it.bitrate }
            if (m != null) {
                val size = m.itagItem?.contentLength?.takeIf { it > 0 }
                    ?: FormatSizeCalculator.estimateFallbackBytes(durationSec, "mp4", "${targetHeight}p")
                return m.content to size
            }
            val vo = videoOnly.filter { it.height == targetHeight }.maxByOrNull { it.bitrate }
            if (vo != null) {
                val vSize = vo.itagItem?.contentLength?.takeIf { it > 0 }
                    ?: FormatSizeCalculator.estimateFallbackBytes(durationSec, "mp4", "${targetHeight}p")
                return vo.content to vSize
            }
            return null
        }

        val v720 = findVideoForHeight(720)
        val size720 = v720?.second ?: FormatSizeCalculator.estimateFallbackBytes(durationSec, "mp4", "720p")

        val v360 = findVideoForHeight(360)
        val size360 = v360?.second ?: FormatSizeCalculator.estimateFallbackBytes(durationSec, "mp4", "360p")
        val saved360Mb = ((size720 - size360).coerceAtLeast(0L)) / (1024f * 1024f)

        out += MediaFormatOption(
            formatId = "video_fast_360",
            category = FormatCategory.VIDEO,
            displayLabel = "Fast (360p)",
            container = "mp4",
            resolution = "360p",
            streamUrl = v360?.first ?: muxed.firstOrNull()?.content.orEmpty(),
            audioStreamUrl = if (muxed.any { it.height == 360 }) null else bestAudioUrl,
            estimatedSizeBytes = size360,
            isQuickPreset = true,
            saveSpaceBadge = if (saved360Mb >= 1f) String.format("Save %.1f MB", saved360Mb) else "Save space"
        )

        out += MediaFormatOption(
            formatId = "video_hd_720",
            category = FormatCategory.VIDEO,
            displayLabel = "High quality (720p)",
            container = "mp4",
            resolution = "720p",
            streamUrl = v720?.first ?: muxed.maxByOrNull { it.height }?.content.orEmpty(),
            audioStreamUrl = if (muxed.any { it.height == 720 }) null else bestAudioUrl,
            estimatedSizeBytes = size720,
            isQuickPreset = true
        )

        val v240 = findVideoForHeight(240)
        val size240 = v240?.second ?: FormatSizeCalculator.estimateFallbackBytes(durationSec, "mp4", "240p")
        val saved240Mb = ((size720 - size240).coerceAtLeast(0L)) / (1024f * 1024f)
        out += MediaFormatOption(
            formatId = "video_ultra_240",
            category = FormatCategory.VIDEO,
            displayLabel = "Ultra Small (240p)",
            container = "mp4",
            resolution = "240p",
            streamUrl = v240?.first ?: v360?.first.orEmpty(),
            audioStreamUrl = if (muxed.any { it.height == 240 }) null else bestAudioUrl,
            estimatedSizeBytes = size240,
            isQuickPreset = false,
            saveSpaceBadge = if (saved240Mb >= 1f) String.format("Save %.1f MB", saved240Mb) else "Save space"
        )

        val v480 = findVideoForHeight(480)
        if (v480 != null) {
            out += MediaFormatOption(
                formatId = "video_480",
                category = FormatCategory.VIDEO,
                displayLabel = "Standard (480p)",
                container = "mp4",
                resolution = "480p",
                streamUrl = v480.first,
                audioStreamUrl = if (muxed.any { it.height == 480 }) null else bestAudioUrl,
                estimatedSizeBytes = v480.second,
                isQuickPreset = false
            )
        }

        val v1080 = findVideoForHeight(1080)
        if (v1080 != null) {
            out += MediaFormatOption(
                formatId = "video_1080",
                category = FormatCategory.VIDEO,
                displayLabel = "Full HD (1080p)",
                container = "mp4",
                resolution = "1080p",
                streamUrl = v1080.first,
                audioStreamUrl = if (muxed.any { it.height == 1080 }) null else bestAudioUrl,
                estimatedSizeBytes = v1080.second,
                isQuickPreset = false
            )
        }

        return out
    }

    private fun StreamInfoItem.toVideoItem(): VideoItem = VideoItem(
        id = extractVideoIdFromUrl(url ?: name),
        platform = MediaPlatform.YOUTUBE,
        title = name ?: "Untitled",
        channelName = uploaderName ?: "YouTube",
        channelAvatarUrl = uploaderAvatars.firstOrNull()?.url ?: "",
        thumbnailUrl = thumbnails.maxByOrNull { it.width }?.url ?: "",
        durationSec = duration.toInt().coerceAtLeast(0),
        viewCountText = if (viewCount > 0) formatCompactCount(viewCount) + " views" else "",
        streamPageUrl = url ?: ""
    )

    private fun formatSubs(subs: Long, videos: Long): String {
        val parts = mutableListOf<String>()
        if (subs > 0) parts += "${formatCompactCount(subs)} subscribers"
        if (videos > 0) parts += "$videos videos"
        return parts.joinToString(" • ")
    }

    private fun formatCompactCount(count: Long): String {
        return when {
            count >= 1_000_000_000L -> String.format("%.1fB", count / 1_000_000_000.0)
            count >= 1_000_000L -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000L -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    fun extractVideoIdFromUrl(url: String): String {
        val vParam = Regex("[?&]v=([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.getOrNull(1)
        if (vParam != null) return vParam
        val shortMatch = Regex("youtu\\.be/([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.getOrNull(1)
        if (shortMatch != null) return shortMatch
        return url.hashCode().toUInt().toString(16)
    }
}

data class SearchBundle(
    val channel: ChannelInfo?,
    val videos: List<VideoItem>
)

private class OkHttpNewPipeDownloader(
    private val client: OkHttpClient
) : Downloader() {
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val builder = Request.Builder().url(request.url())
        request.headers().forEach { (key, values) ->
            values.forEach { v -> builder.addHeader(key, v) }
        }
        if (request.headers()["User-Agent"].isNullOrEmpty()) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
        }
        val bodyBytes = request.dataToSend()
        when (request.httpMethod().uppercase()) {
            "POST" -> builder.post((bodyBytes ?: ByteArray(0)).toRequestBody(null))
            "HEAD" -> builder.head()
            else -> builder.get()
        }

        val resp = client.newCall(builder.build()).execute()
        if (resp.code == 429) {
            resp.close()
            throw ReCaptchaException("Rate limited (429)", request.url())
        }
        val responseBody = resp.body?.string() ?: ""
        return Response(
            resp.code,
            resp.message,
            resp.headers.toMultimap(),
            responseBody,
            resp.request.url.toString()
        )
    }
}
