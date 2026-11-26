package com.snapfetch.app.core.perf

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import com.snapfetch.app.core.datastore.ThumbnailQuality

class LowRamConfig(context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    val isLowRamDevice: Boolean = run {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val systemLowRam = activityManager?.isLowRamDevice == true
        val totalUnder25Gb = memInfo.totalMem in 1L..2_600_000_000L
        systemLowRam || totalUnder25Gb
    }

    val coilMemoryCacheBytes: Long = if (isLowRamDevice) 10L * 1024 * 1024 else 16L * 1024 * 1024
    val coilDiskCacheBytes: Long = 25L * 1024 * 1024
    val defaultMaxConcurrentDownloads: Int = if (isLowRamDevice) 1 else 2

    fun bitmapConfig(dataSaverEnabled: Boolean): Bitmap.Config =
        if (isLowRamDevice || dataSaverEnabled) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888

    fun effectiveThumbnailPx(quality: ThumbnailQuality, dataSaverEnabled: Boolean): Int {
        if (dataSaverEnabled) return 320
        return when (quality) {
            ThumbnailQuality.AUTO_LOW_RAM -> if (isLowRamDevice) 320 else 480
            ThumbnailQuality.LOW -> 320
            ThumbnailQuality.MEDIUM -> 480
            ThumbnailQuality.HIGH -> 720
        }
    }
}
