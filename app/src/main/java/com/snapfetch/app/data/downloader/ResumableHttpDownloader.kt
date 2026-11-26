package com.snapfetch.app.data.downloader

import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.datastore.UserSettingsDataStore
import com.snapfetch.app.data.local.DownloadDao
import com.snapfetch.app.domain.model.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

sealed class DownloadStreamResult {
    data class Success(val partFile: File, val totalBytes: Long) : DownloadStreamResult()
    data class Paused(val reason: String) : DownloadStreamResult()
    data class Failed(val reason: String) : DownloadStreamResult()
}

class ResumableHttpDownloader(
    private val okHttpClient: OkHttpClient,
    private val downloadDao: DownloadDao,
    private val dataUsageTracker: DataUsageTracker,
    private val settingsDataStore: UserSettingsDataStore
) {
    suspend fun downloadToPartFile(
        downloadId: String,
        initialUrl: String,
        partFile: File,
        isAudioTrack: Boolean = false,
        onRefreshUrl: suspend () -> String?,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit
    ): DownloadStreamResult {
        partFile.parentFile?.mkdirs()
        var activeUrl = initialUrl
        var refreshedOnce = false

        while (true) {
            val entity = downloadDao.getById(downloadId)
                ?: return DownloadStreamResult.Paused("Cancelled")
            if (entity.status == DownloadStatus.PAUSED) {
                return DownloadStreamResult.Paused("Paused by user")
            }

            var currentSettings = settingsDataStore.settingsFlow.first()
            when (dataUsageTracker.checkNetworkGate(currentSettings)) {
                NetworkGateResult.NO_NETWORK -> return DownloadStreamResult.Paused("Waiting for network")
                NetworkGateResult.WIFI_REQUIRED -> return DownloadStreamResult.Paused("Paused: Wi-Fi only enabled")
                NetworkGateResult.DAILY_MOBILE_CAP_REACHED -> return DownloadStreamResult.Paused("Paused: Daily mobile data cap reached")
                NetworkGateResult.ALLOWED -> Unit
            }

            val existingLen = if (partFile.exists()) partFile.length() else 0L
            val resumeOffset = if (isAudioTrack) existingLen else maxOf(existingLen, entity.resumeOffsetBytes).coerceAtMost(existingLen)

            val reqBuilder = Request.Builder()
                .url(activeUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
            if (resumeOffset > 0L) {
                reqBuilder.header("Range", "bytes=$resumeOffset-")
                if (!entity.etag.isNullOrBlank()) {
                    reqBuilder.header("If-Range", entity.etag)
                }
            }

            val call = okHttpClient.newCall(reqBuilder.build())
            val response = try {
                call.execute()
            } catch (t: Throwable) {
                return DownloadStreamResult.Paused("Network interrupted: ${t.message ?: "offline"}")
            }

            response.use { resp ->
                if ((resp.code == 403 || resp.code == 410) && !refreshedOnce) {
                    refreshedOnce = true
                    val fresh = onRefreshUrl()
                    if (!fresh.isNullOrBlank()) {
                        activeUrl = fresh
                        return@use
                    }
                    return DownloadStreamResult.Failed("Stream URL expired (HTTP ${resp.code})")
                }

                if (resp.code == 416 && partFile.exists() && partFile.length() > 0L) {
                    return DownloadStreamResult.Success(partFile, partFile.length())
                }

                if (!resp.isSuccessful) {
                    return DownloadStreamResult.Failed("HTTP ${resp.code}: ${resp.message}")
                }

                val isPartial = resp.code == 206
                val append = isPartial && resumeOffset > 0L
                val startOffset = if (append) resumeOffset else 0L
                val contentLength = resp.body?.contentLength() ?: -1L
                val totalExpected = when {
                    contentLength > 0 && isPartial -> startOffset + contentLength
                    contentLength > 0 -> contentLength
                    entity.totalBytes > 0 -> entity.totalBytes
                    else -> 0L
                }
                val etag = resp.header("ETag")
                val lastMod = resp.header("Last-Modified")

                val body = resp.body ?: return DownloadStreamResult.Failed("Empty response body")
                val input = body.byteStream()
                val buffer = ByteArray(64 * 1024)

                var writtenTotal = startOffset
                var bytesSincePersist = 0L
                var bytesSinceCellularCheck = 0L
                var windowBytes = 0L
                var windowStartNs = System.nanoTime()
                var lastPersistMs = System.currentTimeMillis()

                FileOutputStream(partFile, append).use { out ->
                    while (true) {
                        val latest = downloadDao.getById(downloadId)
                        if (latest == null || latest.status == DownloadStatus.PAUSED) {
                            out.flush()
                            if (!isAudioTrack) {
                                downloadDao.updateProgress(downloadId, writtenTotal, writtenTotal, totalExpected, 0L, etag, lastMod)
                            }
                            return DownloadStreamResult.Paused("Paused")
                        }

                        val read = input.read(buffer)
                        if (read == -1) break

                        out.write(buffer, 0, read)
                        writtenTotal += read
                        bytesSincePersist += read
                        bytesSinceCellularCheck += read
                        windowBytes += read

                        // Cellular cap check every 256 KB
                        if (bytesSinceCellularCheck >= 256L * 1024L) {
                            currentSettings = settingsDataStore.settingsFlow.first()
                            val ok = dataUsageTracker.recordBytesAndCheckCap(bytesSinceCellularCheck, currentSettings)
                            bytesSinceCellularCheck = 0L
                            if (!ok) {
                                out.flush()
                                if (!isAudioTrack) {
                                    downloadDao.updateProgress(downloadId, writtenTotal, writtenTotal, totalExpected, 0L, etag, lastMod)
                                }
                                return DownloadStreamResult.Paused("Paused: Daily mobile data cap reached")
                            }
                            if (!dataUsageTracker.isOnUnmeteredWifi() && currentSettings.wifiOnlyEnabled) {
                                out.flush()
                                return DownloadStreamResult.Paused("Paused: Wi-Fi only enabled")
                            }
                        }

                        // Speed limit throttle
                        val limitKbps = currentSettings.speedLimitKbps
                        if (limitKbps > 0) {
                            val elapsedMs = (System.nanoTime() - windowStartNs) / 1_000_000L
                            val expectedMs = (windowBytes * 1000L) / (limitKbps.toLong() * 1024L)
                            if (expectedMs > elapsedMs) {
                                delay((expectedMs - elapsedMs).coerceAtMost(500L))
                            }
                        }

                        val nowMs = System.currentTimeMillis()
                        if (bytesSincePersist >= 512L * 1024L || nowMs - lastPersistMs >= 1_000L) {
                            val elapsedSec = ((System.nanoTime() - windowStartNs) / 1_000_000_000.0).coerceAtLeast(0.1)
                            val speedBps = (windowBytes / elapsedSec).toLong()
                            if (!isAudioTrack) {
                                downloadDao.updateProgress(downloadId, writtenTotal, writtenTotal, totalExpected, speedBps, etag, lastMod)
                            }
                            onProgress(writtenTotal, totalExpected, speedBps)
                            bytesSincePersist = 0L
                            windowBytes = 0L
                            windowStartNs = System.nanoTime()
                            lastPersistMs = nowMs
                        }
                    }
                    out.flush()
                }

                if (bytesSinceCellularCheck > 0L) {
                    dataUsageTracker.recordBytesAndCheckCap(bytesSinceCellularCheck, currentSettings)
                }
                if (!isAudioTrack) {
                    downloadDao.updateProgress(downloadId, writtenTotal, writtenTotal, totalExpected.coerceAtLeast(writtenTotal), 0L, etag, lastMod)
                }
                return DownloadStreamResult.Success(partFile, writtenTotal)
            }
        }
    }
}
