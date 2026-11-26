package com.snapfetch.app.data.extractor

import com.snapfetch.app.domain.model.ChannelInfo
import com.snapfetch.app.domain.model.CommentItem
import com.snapfetch.app.domain.model.FormatCategory
import com.snapfetch.app.domain.model.FormatSizeCalculator
import com.snapfetch.app.domain.model.MediaFormatOption
import com.snapfetch.app.domain.model.MediaPlatform
import com.snapfetch.app.domain.model.MusicShelf
import com.snapfetch.app.domain.model.MusicShelfItem
import com.snapfetch.app.domain.model.VideoItem

object MockPlatformCatalog {

    val popularQueries: List<String> = listOf(
        "Top hits",
        "Taylor Swift",
        "Bad Bunny",
        "Shakira songs",
        "Lo-fi chill"
    )

    fun fallbackSuggestions(prefix: String): List<String> {
        val all = listOf(
            "shakira songs",
            "shaky shaky",
            "shaky shaky song",
            "shakira shakira",
            "shakira waka waka",
            "shakira dai dai",
            "bad bunny hits",
            "taylor swift anti hero",
            "sidhu moose wala 295"
        )
        val q = prefix.trim().lowercase()
        if (q.isEmpty()) return all.take(6)
        val matched = all.filter { it.contains(q) }
        return if (matched.isNotEmpty()) matched else listOf("$prefix official video", "$prefix audio", "$prefix remix")
    }

    fun shakiraFallbackSearch(query: String): SearchBundle {
        val channel = ChannelInfo(
            channelId = "UCYLNGLProofShakira",
            name = "Shakira",
            avatarUrl = "https://i.ytimg.com/vi/pRpeEdMmmQ0/hqdefault.jpg",
            subscriberText = "54.3M subscribers • 655 videos",
            url = "https://www.youtube.com/@Shakira"
        )
        val videos = listOf(
            VideoItem(
                id = "mix_shakira",
                platform = MediaPlatform.YOUTUBE,
                title = "Mix - Shakira",
                channelName = "YouTube",
                thumbnailUrl = "https://i.ytimg.com/vi/pRpeEdMmmQ0/hqdefault.jpg",
                durationSec = 0,
                streamPageUrl = "https://www.youtube.com/watch?v=pRpeEdMmmQ0",
                isPlaylist = true,
                playlistCount = 25
            ),
            VideoItem(
                id = "daidai_official",
                platform = MediaPlatform.YOUTUBE,
                title = "Shakira, Burna Boy - Dai Dai (Official Video)",
                channelName = "Shakira and 2 more",
                thumbnailUrl = "https://i.ytimg.com/vi/DUT5rEU6pqM/hqdefault.jpg",
                durationSec = 241,
                viewCountText = "18M views",
                streamPageUrl = "https://www.youtube.com/watch?v=DUT5rEU6pqM"
            ),
            VideoItem(
                id = "zoo_zootopia",
                platform = MediaPlatform.YOUTUBE,
                title = "Shakira - Zoo (From \"Zootopia 2\") Official Music Video",
                channelName = "DisneyMusicVEVO",
                thumbnailUrl = "https://i.ytimg.com/vi/c6rP-YP4c5I/hqdefault.jpg",
                durationSec = 198,
                viewCountText = "42M views",
                streamPageUrl = "https://www.youtube.com/watch?v=c6rP-YP4c5I"
            ),
            VideoItem(
                id = "pRpeEdMmmQ0",
                platform = MediaPlatform.YOUTUBE,
                title = "Shakira - Waka Waka (This Time for Africa) (The Official 2010 FIFA World Cup™ Song)",
                channelName = "Shakira",
                channelAvatarUrl = "https://i.ytimg.com/vi/pRpeEdMmmQ0/hqdefault.jpg",
                channelSubscriberText = "54.3M subscribers",
                thumbnailUrl = "https://i.ytimg.com/vi/pRpeEdMmmQ0/hqdefault.jpg",
                durationSec = 211,
                viewCountText = "4.1B views",
                streamPageUrl = "https://www.youtube.com/watch?v=pRpeEdMmmQ0"
            )
        )
        return if (query.contains("shakira", ignoreCase = true) || query.isBlank()) {
            SearchBundle(channel = channel, videos = videos)
        } else {
            SearchBundle(
                channel = null,
                videos = videos.mapIndexed { i, v ->
                    if (i == 0) v.copy(id = "q_${query.hashCode()}", title = "$query — Official Music Video", isPlaylist = false) else v
                }
            )
        }
    }

    fun platformGuidedMockCards(platform: MediaPlatform, query: String): List<VideoItem> {
        val label = platform.displayName
        val suffix = if (query.isBlank()) "Trending" else query
        return listOf(
            VideoItem(
                id = "${platform.name.lowercase()}_1",
                platform = platform,
                title = "$label Guide: Paste a public $label link to extract & download ($suffix)",
                channelName = "$label Public Extractor",
                thumbnailUrl = "https://i.ytimg.com/vi/pRpeEdMmmQ0/hqdefault.jpg",
                durationSec = 64,
                streamPageUrl = "https://www.youtube.com/watch?v=pRpeEdMmmQ0"
            ),
            VideoItem(
                id = "${platform.name.lowercase()}_2",
                platform = platform,
                title = "$suffix — Short Clip ($label Public Reel)",
                channelName = "Creator on $label",
                thumbnailUrl = "https://i.ytimg.com/vi/DUT5rEU6pqM/hqdefault.jpg",
                durationSec = 38,
                streamPageUrl = "https://www.youtube.com/watch?v=DUT5rEU6pqM"
            )
        )
    }

    val defaultYouTubeFeed: List<VideoItem> = listOf(
        VideoItem(
            id = "DUT5rEU6pqM",
            platform = MediaPlatform.YOUTUBE,
            title = "Dai Dai",
            channelName = "shakiraVEVO",
            thumbnailUrl = "https://i.ytimg.com/vi/DUT5rEU6pqM/hqdefault.jpg",
            durationSec = 241,
            streamPageUrl = "https://www.youtube.com/watch?v=DUT5rEU6pqM"
        ),
        VideoItem(
            id = "n_FCrCQ6-bA",
            platform = MediaPlatform.YOUTUBE,
            title = "295 (MUSIC VIDEO) SIDHU MOOSE WALA",
            channelName = "Sidhu Moose Wala",
            thumbnailUrl = "https://i.ytimg.com/vi/n_FCrCQ6-bA/hqdefault.jpg",
            durationSec = 357,
            streamPageUrl = "https://www.youtube.com/watch?v=n_FCrCQ6-bA"
        ),
        VideoItem(
            id = "pRpeEdMmmQ0",
            platform = MediaPlatform.YOUTUBE,
            title = "Shakira - Waka Waka (This Time for Africa)",
            channelName = "Shakira",
            thumbnailUrl = "https://i.ytimg.com/vi/pRpeEdMmmQ0/hqdefault.jpg",
            durationSec = 211,
            streamPageUrl = "https://www.youtube.com/watch?v=pRpeEdMmmQ0"
        )
    )

    val defaultMusicShelves: List<MusicShelf> = listOf(
        MusicShelf(
            id = "fresh_drops",
            title = "Fresh drops",
            items = listOf(
                MusicShelfItem(
                    video = VideoItem(
                        id = "hindi_hits",
                        title = "New Music Hindi",
                        channelName = "Rochak Kohli, Shreya Ghoshal",
                        thumbnailUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg",
                        durationSec = 225,
                        streamPageUrl = "https://www.youtube.com/watch?v=BddP6PYo2gs",
                        isPlaylist = true,
                        playlistCount = 30
                    ),
                    artistSubtitle = "Rochak Kohli, Shrey...",
                    isMixOrAlbum = true
                ),
                MusicShelfItem(
                    video = VideoItem(
                        id = "telugu_hits",
                        title = "New Music Telugu",
                        channelName = "G. V. Prakash Kumar",
                        thumbnailUrl = "https://i.ytimg.com/vi/1y6smkh6c-0/hqdefault.jpg",
                        durationSec = 210,
                        streamPageUrl = "https://www.youtube.com/watch?v=1y6smkh6c-0",
                        isPlaylist = true,
                        playlistCount = 24
                    ),
                    artistSubtitle = "G. V. Prakash Kumar ...",
                    isMixOrAlbum = true
                ),
                MusicShelfItem(
                    video = VideoItem(
                        id = "global_pop",
                        title = "Global Pop Radar",
                        channelName = "Various Artists",
                        thumbnailUrl = "https://i.ytimg.com/vi/c6rP-YP4c5I/hqdefault.jpg",
                        durationSec = 198,
                        streamPageUrl = "https://www.youtube.com/watch?v=c6rP-YP4c5I",
                        isPlaylist = true,
                        playlistCount = 20
                    ),
                    artistSubtitle = "Dua Lipa, Shakira...",
                    isMixOrAlbum = true
                )
            )
        ),
        MusicShelf(
            id = "new_releases",
            title = "New releases",
            items = listOf(
                MusicShelfItem(
                    video = VideoItem(
                        id = "call_me",
                        title = "Call Me?",
                        channelName = "Mary Ann Alexander",
                        thumbnailUrl = "https://i.ytimg.com/vi/DUT5rEU6pqM/hqdefault.jpg",
                        durationSec = 185,
                        streamPageUrl = "https://www.youtube.com/watch?v=DUT5rEU6pqM"
                    ),
                    artistSubtitle = "Single • Mary Ann A...",
                    isMixOrAlbum = true
                ),
                MusicShelfItem(
                    video = VideoItem(
                        id = "mangda_wapis",
                        title = "Mangda Wapis Kyun",
                        channelName = "Talwiinder",
                        thumbnailUrl = "https://i.ytimg.com/vi/n_FCrCQ6-bA/hqdefault.jpg",
                        durationSec = 204,
                        streamPageUrl = "https://www.youtube.com/watch?v=n_FCrCQ6-bA"
                    ),
                    artistSubtitle = "Single • Talwiinder, ...",
                    isMixOrAlbum = true
                ),
                MusicShelfItem(
                    video = VideoItem(
                        id = "waka_single",
                        title = "Waka Waka (Remastered)",
                        channelName = "Shakira",
                        thumbnailUrl = "https://i.ytimg.com/vi/pRpeEdMmmQ0/hqdefault.jpg",
                        durationSec = 211,
                        streamPageUrl = "https://www.youtube.com/watch?v=pRpeEdMmmQ0"
                    ),
                    artistSubtitle = "Single • Shakira",
                    isMixOrAlbum = false
                )
            )
        ),
        MusicShelf(
            id = "indian_music",
            title = "Indian Music",
            items = listOf(
                MusicShelfItem(
                    video = VideoItem(
                        id = "punjabi_party",
                        title = "Punjabi Party",
                        channelName = "Karan Aujla, Diljit Dosanjh",
                        thumbnailUrl = "https://i.ytimg.com/vi/n_FCrCQ6-bA/hqdefault.jpg",
                        durationSec = 240,
                        streamPageUrl = "https://www.youtube.com/watch?v=n_FCrCQ6-bA",
                        isPlaylist = true
                    ),
                    artistSubtitle = "Karan Aujla, Diljit D...",
                    isMixOrAlbum = true
                ),
                MusicShelfItem(
                    video = VideoItem(
                        id = "bollywood_jams",
                        title = "Make Out Jams: Bollywood",
                        channelName = "Arijit Singh, Jubin Nautiyal",
                        thumbnailUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg",
                        durationSec = 265,
                        streamPageUrl = "https://www.youtube.com/watch?v=BddP6PYo2gs",
                        isPlaylist = true
                    ),
                    artistSubtitle = "Arijit Singh, Jubin N...",
                    isMixOrAlbum = true
                )
            )
        )
    )

    fun defaultFallbackBundle(video: VideoItem): ExtractedStreamBundle {
        val dur = video.durationSec.takeIf { it > 0 } ?: 211
        val s360 = FormatSizeCalculator.estimateFallbackBytes(dur, "mp4", "360p")
        val s720 = FormatSizeCalculator.estimateFallbackBytes(dur, "mp4", "720p")
        val saved = (s720 - s360).coerceAtLeast(0L) / (1024f * 1024f)
        val formats = listOf(
            MediaFormatOption(
                formatId = "music_fast_m4a",
                category = FormatCategory.MUSIC,
                displayLabel = "Fast",
                container = "m4a",
                estimatedSizeBytes = FormatSizeCalculator.estimateFallbackBytes(dur, "m4a", "")
            ),
            MediaFormatOption(
                formatId = "music_classic_mp3",
                category = FormatCategory.MUSIC,
                displayLabel = "Classic MP3",
                container = "mp3",
                estimatedSizeBytes = FormatSizeCalculator.estimateFallbackBytes(dur, "mp3", "")
            ),
            MediaFormatOption(
                formatId = "video_fast_360",
                category = FormatCategory.VIDEO,
                displayLabel = "Fast (360p)",
                container = "mp4",
                resolution = "360p",
                estimatedSizeBytes = s360,
                saveSpaceBadge = String.format("Save %.1f MB", saved)
            ),
            MediaFormatOption(
                formatId = "video_hd_720",
                category = FormatCategory.VIDEO,
                displayLabel = "High quality (720p)",
                container = "mp4",
                resolution = "720p",
                estimatedSizeBytes = s720
            ),
            MediaFormatOption(
                formatId = "video_ultra_240",
                category = FormatCategory.VIDEO,
                displayLabel = "Ultra Small (240p)",
                container = "mp4",
                resolution = "240p",
                estimatedSizeBytes = FormatSizeCalculator.estimateFallbackBytes(dur, "mp4", "240p"),
                isQuickPreset = false,
                saveSpaceBadge = "Save space"
            )
        )

        return ExtractedStreamBundle(
            video = video,
            channel = ChannelInfo(
                channelId = video.channelName,
                name = video.channelName,
                avatarUrl = video.channelAvatarUrl.ifBlank { video.thumbnailUrl },
                subscriberText = video.channelSubscriberText
            ),
            formats = formats,
            relatedVideos = defaultYouTubeFeed.filter { it.id != video.id },
            comments = listOf(
                CommentItem(
                    id = "c1",
                    author = "Rafael",
                    text = "I Don't Wanna Be The Only One To Listening This Masterpiece Today!",
                    likeCountText = "1.4K",
                    publishedText = "2 weeks ago"
                ),
                CommentItem(
                    id = "c2",
                    author = "Elena M.",
                    text = "Still hits just as hard on low-data mode. Crisp audio!",
                    likeCountText = "382",
                    publishedText = "5 days ago"
                )
            ),
            previewStreamUrl = null
        )
    }
}
