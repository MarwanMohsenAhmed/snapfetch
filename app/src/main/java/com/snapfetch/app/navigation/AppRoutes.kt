package com.snapfetch.app.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable
    data class DownloadRoot(val subTab: Int = 0) : AppRoute

    @Serializable
    data object PlayRoot : AppRoute

    @Serializable
    data object SettingsRoot : AppRoute

    @Serializable
    data class SearchInput(val initialQuery: String = "") : AppRoute

    @Serializable
    data class SearchResults(val query: String, val filter: String? = null) : AppRoute

    @Serializable
    data class UnifiedPlayer(val mediaKey: String, val isLocal: Boolean) : AppRoute

    @Serializable
    data class PlaylistDetail(val playlistId: String) : AppRoute

    @Serializable
    data object DownloadQueue : AppRoute

    @Serializable
    data object DownloadSettings : AppRoute

    @Serializable
    data object PlayerSettings : AppRoute

    @Serializable
    data object NotificationSettings : AppRoute

    @Serializable
    data object Customization : AppRoute

    @Serializable
    data class HelperTool(val toolId: String) : AppRoute
}
