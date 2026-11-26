package com.snapfetch.app.data.downloader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.datastore.UserSettingsDataStore

enum class NetworkGateResult {
    ALLOWED,
    NO_NETWORK,
    WIFI_REQUIRED,
    DAILY_MOBILE_CAP_REACHED
}

class DataUsageTracker(
    private val context: Context,
    private val settingsDataStore: UserSettingsDataStore
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isOnUnmeteredWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun isNetworkConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun checkNetworkGate(settings: UserSettings): NetworkGateResult {
        if (!isNetworkConnected()) return NetworkGateResult.NO_NETWORK
        val wifi = isOnUnmeteredWifi()
        if (!wifi && settings.wifiOnlyEnabled) return NetworkGateResult.WIFI_REQUIRED
        if (!wifi && settings.mobileDataLimitMbPerDay > 0) {
            val limitBytes = settings.mobileDataLimitMbPerDay.toLong() * 1024L * 1024L
            if (settings.mobileBytesUsedToday >= limitBytes) {
                return NetworkGateResult.DAILY_MOBILE_CAP_REACHED
            }
        }
        return NetworkGateResult.ALLOWED
    }

    suspend fun recordBytesAndCheckCap(bytes: Long, settings: UserSettings): Boolean {
        if (bytes <= 0L || isOnUnmeteredWifi()) return true
        val newTotal = settingsDataStore.addMobileBytesUsed(bytes)
        if (settings.mobileDataLimitMbPerDay <= 0) return true
        val limitBytes = settings.mobileDataLimitMbPerDay.toLong() * 1024L * 1024L
        return newTotal < limitBytes
    }
}
