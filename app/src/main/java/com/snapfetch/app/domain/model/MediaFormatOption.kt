package com.snapfetch.app.domain.model

import androidx.compose.runtime.Immutable
import java.util.Locale

enum class FormatCategory { MUSIC, VIDEO }

@Immutable
data class MediaFormatOption(
    val formatId: String,
    val category: FormatCategory,
    val displayLabel: String,
    val container: String,
    val resolution: String = "",
    val audioBitrateKbps: Int = 128,
    val streamUrl: String = "",
    val audioStreamUrl: String? = null,
    val estimatedSizeBytes: Long = 0L,
    val isQuickPreset: Boolean = true,
    val saveSpaceBadge: String? = null
)

object FormatSizeCalculator {
    fun estimateFallbackBytes(durationSec: Int, container: String, resolution: String): Long {
        val sec = durationSec.coerceAtLeast(15).toLong()
        return when {
            container.equals("mp3", ignoreCase = true) -> sec * 18L * 1024L
            container.equals("m4a", ignoreCase = true) || resolution.isBlank() -> sec * 16L * 1024L
            resolution.contains("240") -> sec * 44L * 1024L
            resolution.contains("360") -> sec * 88L * 1024L
            resolution.contains("480") -> sec * 135L * 1024L
            resolution.contains("720") -> sec * 215L * 1024L
            resolution.contains("1080") -> sec * 380L * 1024L
            else -> sec * 88L * 1024L
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0.0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
