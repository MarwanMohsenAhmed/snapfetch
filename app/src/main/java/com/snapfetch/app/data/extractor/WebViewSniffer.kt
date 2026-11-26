package com.snapfetch.app.data.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.snapfetch.app.domain.model.FormatCategory
import com.snapfetch.app.domain.model.FormatSizeCalculator
import com.snapfetch.app.domain.model.MediaFormatOption
import com.snapfetch.app.domain.model.MediaPlatform
import com.snapfetch.app.domain.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import kotlin.coroutines.resume

class WebViewSniffer(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    fun detectPlatform(url: String): MediaPlatform {
        val lower = url.lowercase()
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> MediaPlatform.YOUTUBE
            lower.contains("tiktok.com") -> MediaPlatform.TIKTOK
            lower.contains("instagram.com") -> MediaPlatform.INSTAGRAM
            lower.contains("facebook.com") || lower.contains("fb.watch") -> MediaPlatform.FACEBOOK
            lower.contains("soundcloud.com") -> MediaPlatform.SOUNDCLOUD
            else -> MediaPlatform.OTHER
        }
    }

    suspend fun extractPublicSocialUrl(url: String): ExtractedStreamBundle? = withContext(Dispatchers.IO) {
        val platform = detectPlatform(url)
        val htmlResult = runCatching {
            val req = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val html = resp.body?.string() ?: return@use null
                val doc = Jsoup.parse(html, url)
                val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                    ?.ifBlank { doc.title() } ?: "${platform.displayName} Video"
                val image = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
                val ogVideo = doc.selectFirst("meta[property=og:video:secure_url]")?.attr("content")
                    ?.ifBlank { doc.selectFirst("meta[property=og:video]")?.attr("content") }
                    ?: doc.selectFirst("video source[src]")?.attr("abs:src")
                    ?: doc.selectFirst("video[src]")?.attr("abs:src")
                Triple(title, image, ogVideo)
            }
        }.getOrNull()

        val title = htmlResult?.first ?: "${platform.displayName} Video"
        val thumb = htmlResult?.second ?: ""
        var directMedia = htmlResult?.third

        if (directMedia.isNullOrBlank()) {
            directMedia = sniffViaWebView(url)
        }

        if (directMedia.isNullOrBlank()) return@withContext null

        val durationSec = 45
        val video = VideoItem(
            id = url.hashCode().toUInt().toString(16),
            platform = platform,
            title = title,
            channelName = platform.displayName,
            thumbnailUrl = thumb,
            durationSec = durationSec,
            streamPageUrl = url
        )

        val size360 = FormatSizeCalculator.estimateFallbackBytes(durationSec, "mp4", "360p")
        val size720 = FormatSizeCalculator.estimateFallbackBytes(durationSec, "mp4", "720p")
        val sizeM4a = FormatSizeCalculator.estimateFallbackBytes(durationSec, "m4a", "")

        val formats = listOf(
            MediaFormatOption(
                formatId = "social_fast_m4a",
                category = FormatCategory.MUSIC,
                displayLabel = "Fast",
                container = "m4a",
                streamUrl = directMedia,
                estimatedSizeBytes = sizeM4a
            ),
            MediaFormatOption(
                formatId = "social_360",
                category = FormatCategory.VIDEO,
                displayLabel = "Fast (360p)",
                container = "mp4",
                resolution = "360p",
                streamUrl = directMedia,
                estimatedSizeBytes = size360,
                saveSpaceBadge = "Save space"
            ),
            MediaFormatOption(
                formatId = "social_720",
                category = FormatCategory.VIDEO,
                displayLabel = "High quality (720p)",
                container = "mp4",
                resolution = "720p",
                streamUrl = directMedia,
                estimatedSizeBytes = size720
            )
        )

        ExtractedStreamBundle(
            video = video,
            channel = com.snapfetch.app.domain.model.ChannelInfo(
                channelId = platform.name,
                name = platform.displayName,
                avatarUrl = thumb,
                subscriberText = "Public URL extractor"
            ),
            formats = formats,
            relatedVideos = emptyList(),
            comments = emptyList(),
            previewStreamUrl = directMedia
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun sniffViaWebView(pageUrl: String): String? {
        return withTimeoutOrNull(8_000L) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val webView = WebView(context)
                    var settled = false
                    fun finish(found: String?) {
                        if (!settled) {
                            settled = true
                            Handler(Looper.getMainLooper()).post {
                                runCatching {
                                    webView.stopLoading()
                                    webView.destroy()
                                }
                            }
                            if (cont.isActive) cont.resume(found)
                        }
                    }

                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.settings.mediaPlaybackRequiresUserGesture = true
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            if (reqUrl.contains(".mp4", ignoreCase = true) ||
                                reqUrl.contains(".m4a", ignoreCase = true) ||
                                reqUrl.contains("mime=video", ignoreCase = true)
                            ) {
                                finish(reqUrl)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript(
                                "(function(){var v=document.querySelector('video');return v?(v.currentSrc||v.src):'';})()"
                            ) { res ->
                                val cleaned = res?.trim('"')?.takeIf { it.startsWith("http") }
                                if (cleaned != null) finish(cleaned)
                            }
                        }
                    }
                    cont.invokeOnCancellation {
                        Handler(Looper.getMainLooper()).post {
                            runCatching {
                                webView.stopLoading()
                                webView.destroy()
                            }
                        }
                    }
                    webView.loadUrl(pageUrl)
                }
            }
        }
    }
}
