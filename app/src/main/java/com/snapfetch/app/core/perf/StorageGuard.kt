package com.snapfetch.app.core.perf

import android.content.Context
import android.os.StatFs
import java.io.File

data class StorageCheckResult(
    val hasEnoughSpace: Boolean,
    val availableBytes: Long,
    val requiredBytes: Long
)

class StorageGuard(private val context: Context) {
    companion object {
        const val SAFETY_BUFFER_BYTES: Long = 100L * 1024L * 1024L // 100 MB
    }

    fun checkFreeSpace(estimatedOutputBytes: Long, targetDir: File = context.cacheDir): StorageCheckResult {
        val dir = if (targetDir.exists()) targetDir else context.filesDir
        val stat = StatFs(dir.absolutePath)
        val avail = stat.availableBytes
        val required = estimatedOutputBytes.coerceAtLeast(0L) + SAFETY_BUFFER_BYTES
        return StorageCheckResult(
            hasEnoughSpace = avail >= required,
            availableBytes = avail,
            requiredBytes = required
        )
    }

    fun availableInternalBytes(): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBytes
    }
}
