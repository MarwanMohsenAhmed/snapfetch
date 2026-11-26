package com.snapfetch.app.core.datastore

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.snapfetch.app.core.perf.LowRamConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "snapfetch_settings")

enum class DownloadSizeMode { STANDARD, DATA_SAVER_360P, ULTRA_SMALL_240P, AUDIO_ONLY }
enum class CompressionPreset(val label: String, val bitrateKbps: Int, val heightPx: Int, val audioOnly: Boolean) {
    SMALL_360P_500K("Small (360p)", 500, 360, false),
    COMPACT_480P_900K("Compact (480p)", 900, 480, false),
    AUDIO_M4A_96K("Audio Only (M4A 96k)", 96, 0, true)
}
enum class ThemeMode { LIGHT, DARK, AMOLED }
enum class StartupTab { SEARCH, YOUTUBE, MUSIC, PLAY_LIBRARY }
enum class LibraryLayoutDensity { COMPACT_LIST, LARGE_CARDS }
enum class ThumbnailQuality { AUTO_LOW_RAM, LOW, MEDIUM, HIGH }

@Immutable
data class UserSettings(
    val saveFolderUri: String? = null,
    val maxConcurrentDownloads: Int = 1,
    val wifiOnlyEnabled: Boolean = false,
    val dataSaverEnabled: Boolean = false,
    val mobileDataLimitMbPerDay: Int = 0,
    val mobileBytesUsedToday: Long = 0L,
    val mobileUsageResetDate: String = "",
    val speedLimitKbps: Int = 0,
    val autoResumeOnNetworkRestore: Boolean = true,
    val defaultDownloadSizeMode: DownloadSizeMode = DownloadSizeMode.STANDARD,
    val compressionPreset: CompressionPreset = CompressionPreset.SMALL_360P_500K,
    val deleteOriginalAfterCompress: Boolean = false,
    val resumePlaybackEnabled: Boolean = true,
    val defaultPlaybackSpeed: Float = 1.0f,
    val seekStepSeconds: Int = 10,
    val autoPlayNext: Boolean = true,
    val backgroundAudioEnabled: Boolean = true,
    val gestureControlsEnabled: Boolean = true,
    val preferSoftwareDecoderFallback: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val accentColorHex: String = "#FFCC21",
    val startupTab: StartupTab = StartupTab.SEARCH,
    val libraryLayoutDensity: LibraryLayoutDensity = LibraryLayoutDensity.COMPACT_LIST,
    val thumbnailQuality: ThumbnailQuality = ThumbnailQuality.AUTO_LOW_RAM,
    val notifyOnProgress: Boolean = true,
    val notifyOnComplete: Boolean = true,
    val vaultPinHash: String? = null,
    val proEngineInstalled: Boolean = false,
    val appLanguageTag: String = ""
)

class UserSettingsDataStore(
    private val context: Context,
    private val lowRamConfig: LowRamConfig
) {
    private object Keys {
        val SAVE_FOLDER_URI = stringPreferencesKey("save_folder_uri")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent_downloads")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_enabled")
        val DATA_SAVER = booleanPreferencesKey("data_saver_enabled")
        val MOBILE_DATA_LIMIT_MB = intPreferencesKey("mobile_data_limit_mb")
        val MOBILE_BYTES_USED = longPreferencesKey("mobile_bytes_used_today")
        val MOBILE_RESET_DATE = stringPreferencesKey("mobile_usage_reset_date")
        val SPEED_LIMIT_KBPS = intPreferencesKey("speed_limit_kbps")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume_on_network")
        val DOWNLOAD_SIZE_MODE = stringPreferencesKey("default_download_size_mode")
        val COMPRESSION_PRESET = stringPreferencesKey("compression_preset")
        val DELETE_ORIGINAL = booleanPreferencesKey("delete_original_after_compress")
        val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback_enabled")
        val DEFAULT_SPEED = floatPreferencesKey("default_playback_speed")
        val SEEK_STEP = intPreferencesKey("seek_step_seconds")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val BG_AUDIO = booleanPreferencesKey("background_audio_enabled")
        val GESTURES = booleanPreferencesKey("gesture_controls_enabled")
        val SW_DECODER = booleanPreferencesKey("prefer_sw_decoder_fallback")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_HEX = stringPreferencesKey("accent_color_hex")
        val STARTUP_TAB = stringPreferencesKey("startup_tab")
        val LIBRARY_DENSITY = stringPreferencesKey("library_layout_density")
        val THUMB_QUALITY = stringPreferencesKey("thumbnail_quality")
        val NOTIFY_PROGRESS = booleanPreferencesKey("notify_progress")
        val NOTIFY_COMPLETE = booleanPreferencesKey("notify_complete")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val PRO_ENGINE = booleanPreferencesKey("pro_engine_installed")
        val LANGUAGE_TAG = stringPreferencesKey("app_language_tag")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val today = LocalDate.now().toString()
        val storedDate = prefs[Keys.MOBILE_RESET_DATE] ?: today
        val bytesToday = if (storedDate == today) prefs[Keys.MOBILE_BYTES_USED] ?: 0L else 0L

        UserSettings(
            saveFolderUri = prefs[Keys.SAVE_FOLDER_URI],
            maxConcurrentDownloads = (prefs[Keys.MAX_CONCURRENT] ?: lowRamConfig.defaultMaxConcurrentDownloads).coerceIn(1, 3),
            wifiOnlyEnabled = prefs[Keys.WIFI_ONLY] ?: false,
            dataSaverEnabled = prefs[Keys.DATA_SAVER] ?: false,
            mobileDataLimitMbPerDay = prefs[Keys.MOBILE_DATA_LIMIT_MB] ?: 0,
            mobileBytesUsedToday = bytesToday,
            mobileUsageResetDate = today,
            speedLimitKbps = prefs[Keys.SPEED_LIMIT_KBPS] ?: 0,
            autoResumeOnNetworkRestore = prefs[Keys.AUTO_RESUME] ?: true,
            defaultDownloadSizeMode = enumValueOrDefault(prefs[Keys.DOWNLOAD_SIZE_MODE], DownloadSizeMode.STANDARD),
            compressionPreset = enumValueOrDefault(prefs[Keys.COMPRESSION_PRESET], CompressionPreset.SMALL_360P_500K),
            deleteOriginalAfterCompress = prefs[Keys.DELETE_ORIGINAL] ?: false,
            resumePlaybackEnabled = prefs[Keys.RESUME_PLAYBACK] ?: true,
            defaultPlaybackSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
            seekStepSeconds = prefs[Keys.SEEK_STEP] ?: 10,
            autoPlayNext = prefs[Keys.AUTO_PLAY_NEXT] ?: true,
            backgroundAudioEnabled = prefs[Keys.BG_AUDIO] ?: true,
            gestureControlsEnabled = prefs[Keys.GESTURES] ?: true,
            preferSoftwareDecoderFallback = prefs[Keys.SW_DECODER] ?: false,
            themeMode = enumValueOrDefault(prefs[Keys.THEME_MODE], ThemeMode.LIGHT),
            accentColorHex = prefs[Keys.ACCENT_HEX] ?: "#FFCC21",
            startupTab = enumValueOrDefault(prefs[Keys.STARTUP_TAB], StartupTab.SEARCH),
            libraryLayoutDensity = enumValueOrDefault(prefs[Keys.LIBRARY_DENSITY], LibraryLayoutDensity.COMPACT_LIST),
            thumbnailQuality = enumValueOrDefault(prefs[Keys.THUMB_QUALITY], ThumbnailQuality.AUTO_LOW_RAM),
            notifyOnProgress = prefs[Keys.NOTIFY_PROGRESS] ?: true,
            notifyOnComplete = prefs[Keys.NOTIFY_COMPLETE] ?: true,
            vaultPinHash = prefs[Keys.VAULT_PIN_HASH],
            proEngineInstalled = prefs[Keys.PRO_ENGINE] ?: false,
            appLanguageTag = prefs[Keys.LANGUAGE_TAG] ?: ""
        )
    }

    suspend fun setSaveFolderUri(uri: String?) = context.dataStore.edit {
        if (uri == null) it.remove(Keys.SAVE_FOLDER_URI) else it[Keys.SAVE_FOLDER_URI] = uri
    }

    suspend fun setMaxConcurrentDownloads(count: Int) = context.dataStore.edit {
        it[Keys.MAX_CONCURRENT] = count.coerceIn(1, 3)
    }

    suspend fun setWifiOnly(enabled: Boolean) = context.dataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    suspend fun setDataSaver(enabled: Boolean) = context.dataStore.edit { it[Keys.DATA_SAVER] = enabled }
    suspend fun setMobileDataLimitMb(mb: Int) = context.dataStore.edit { it[Keys.MOBILE_DATA_LIMIT_MB] = mb.coerceAtLeast(0) }

    suspend fun addMobileBytesUsed(deltaBytes: Long): Long {
        var total = 0L
        val today = LocalDate.now().toString()
        context.dataStore.edit { prefs ->
            val currentDate = prefs[Keys.MOBILE_RESET_DATE]
            val current = if (currentDate == today) prefs[Keys.MOBILE_BYTES_USED] ?: 0L else 0L
            total = current + deltaBytes
            prefs[Keys.MOBILE_RESET_DATE] = today
            prefs[Keys.MOBILE_BYTES_USED] = total
        }
        return total
    }

    suspend fun resetMobileUsage() = context.dataStore.edit {
        it[Keys.MOBILE_RESET_DATE] = LocalDate.now().toString()
        it[Keys.MOBILE_BYTES_USED] = 0L
    }

    suspend fun setSpeedLimitKbps(kbps: Int) = context.dataStore.edit { it[Keys.SPEED_LIMIT_KBPS] = kbps.coerceAtLeast(0) }
    suspend fun setAutoResume(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_RESUME] = enabled }
    suspend fun setDefaultDownloadSizeMode(mode: DownloadSizeMode) = context.dataStore.edit { it[Keys.DOWNLOAD_SIZE_MODE] = mode.name }
    suspend fun setCompressionPreset(preset: CompressionPreset) = context.dataStore.edit { it[Keys.COMPRESSION_PRESET] = preset.name }
    suspend fun setDeleteOriginalAfterCompress(enabled: Boolean) = context.dataStore.edit { it[Keys.DELETE_ORIGINAL] = enabled }

    suspend fun setResumePlayback(enabled: Boolean) = context.dataStore.edit { it[Keys.RESUME_PLAYBACK] = enabled }
    suspend fun setDefaultPlaybackSpeed(speed: Float) = context.dataStore.edit { it[Keys.DEFAULT_SPEED] = speed }
    suspend fun setSeekStepSeconds(sec: Int) = context.dataStore.edit { it[Keys.SEEK_STEP] = sec }
    suspend fun setAutoPlayNext(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }
    suspend fun setBackgroundAudio(enabled: Boolean) = context.dataStore.edit { it[Keys.BG_AUDIO] = enabled }
    suspend fun setGestureControls(enabled: Boolean) = context.dataStore.edit { it[Keys.GESTURES] = enabled }
    suspend fun setPreferSoftwareDecoder(enabled: Boolean) = context.dataStore.edit { it[Keys.SW_DECODER] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setAccentColorHex(hex: String) = context.dataStore.edit { it[Keys.ACCENT_HEX] = hex }
    suspend fun setStartupTab(tab: StartupTab) = context.dataStore.edit { it[Keys.STARTUP_TAB] = tab.name }
    suspend fun setLibraryDensity(density: LibraryLayoutDensity) = context.dataStore.edit { it[Keys.LIBRARY_DENSITY] = density.name }
    suspend fun setThumbnailQuality(quality: ThumbnailQuality) = context.dataStore.edit { it[Keys.THUMB_QUALITY] = quality.name }

    suspend fun setNotifyOnProgress(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIFY_PROGRESS] = enabled }
    suspend fun setNotifyOnComplete(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIFY_COMPLETE] = enabled }
    suspend fun setVaultPinHash(hash: String?) = context.dataStore.edit {
        if (hash == null) it.remove(Keys.VAULT_PIN_HASH) else it[Keys.VAULT_PIN_HASH] = hash
    }
    suspend fun setProEngineInstalled(installed: Boolean) = context.dataStore.edit { it[Keys.PRO_ENGINE] = installed }
    suspend fun setAppLanguageTag(tag: String) = context.dataStore.edit { it[Keys.LANGUAGE_TAG] = tag }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return try {
            enumValueOf<T>(raw)
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }
}
