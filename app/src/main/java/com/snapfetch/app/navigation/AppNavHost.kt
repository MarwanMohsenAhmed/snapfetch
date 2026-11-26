package com.snapfetch.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.components.BottomNavBar
import com.snapfetch.app.core.components.BottomNavTab
import com.snapfetch.app.core.components.TopTabBar
import com.snapfetch.app.core.datastore.CompressionPreset
import com.snapfetch.app.core.datastore.StartupTab
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.domain.model.LibraryMediaItem
import com.snapfetch.app.domain.model.MediaType
import com.snapfetch.app.domain.model.VideoItem
import com.snapfetch.app.feature.batch.BatchDownloadSheet
import com.snapfetch.app.feature.batch.DownloadQueueScreen
import com.snapfetch.app.feature.compress.VideoCompressSheet
import com.snapfetch.app.feature.format.DownloadFormatSheet
import com.snapfetch.app.feature.home.HomeScreen
import com.snapfetch.app.feature.library.PlayLibraryScreen
import com.snapfetch.app.feature.more.MoreTabScreen
import com.snapfetch.app.feature.music.MusicTabScreen
import com.snapfetch.app.feature.player.UnifiedPlayerScreen
import com.snapfetch.app.feature.playlists.AddToPlaylistSheet
import com.snapfetch.app.feature.playlists.PlaylistDetailScreen
import com.snapfetch.app.feature.search.SearchInputScreen
import com.snapfetch.app.feature.search.SearchResultsScreen
import com.snapfetch.app.feature.settings.CustomizationScreen
import com.snapfetch.app.feature.settings.DownloadSettingsScreen
import com.snapfetch.app.feature.settings.HelperToolStubScreen
import com.snapfetch.app.feature.settings.NotificationSettingsScreen
import com.snapfetch.app.feature.settings.PlayerSettingsScreen
import com.snapfetch.app.feature.settings.SettingsScreen
import com.snapfetch.app.feature.youtube.YouTubeFeedScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class CompressRequest(
    val sourceUri: String,
    val title: String,
    val durationSec: Int,
    val originalBytes: Long,
    val downloadId: String?
)

@Composable
fun AppNavHost(
    container: AppContainer,
    sharedIncomingUrl: String?,
    onClearSharedUrl: () -> Unit,
    isInPipMode: Boolean
) {
    val navController = rememberNavController()
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())

    var startupApplied by remember { mutableStateOf(false) }
    var activeDownloadSubTab by remember { mutableIntStateOf(0) }

    var formatSheetVideo by remember { mutableStateOf<VideoItem?>(null) }
    var batchSheetVideos by remember { mutableStateOf<List<VideoItem>?>(null) }
    var compressRequest by remember { mutableStateOf<CompressRequest?>(null) }
    var addPlaylistTarget by remember { mutableStateOf<LibraryMediaItem?>(null) }

    // Apply startup tab preference once at cold start
    LaunchedEffect(Unit) {
        if (!startupApplied) {
            startupApplied = true
            val initial = container.settingsDataStore.settingsFlow.first()
            when (initial.startupTab) {
                StartupTab.SEARCH -> activeDownloadSubTab = 0
                StartupTab.YOUTUBE -> activeDownloadSubTab = 1
                StartupTab.MUSIC -> activeDownloadSubTab = 2
                StartupTab.PLAY_LIBRARY -> {
                    navController.navigate(AppRoute.PlayRoot) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            }
        }
    }

    // Route incoming ACTION_SEND / ACTION_VIEW shared URLs straight into format extractor
    LaunchedEffect(sharedIncomingUrl) {
        val url = sharedIncomingUrl?.trim()
        if (!url.isNullOrBlank()) {
            formatSheetVideo = VideoItem(
                id = url.hashCode().toUInt().toString(16),
                title = url,
                channelName = "Shared link",
                streamPageUrl = url
            )
            onClearSharedUrl()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val dest = backStackEntry?.destination
    val hideBottomNav = isInPipMode ||
        dest?.hasRoute<AppRoute.SearchInput>() == true

    val activeBottomTab = when {
        dest?.hasRoute<AppRoute.PlayRoot>() == true ||
            dest?.hasRoute<AppRoute.PlaylistDetail>() == true ||
            dest?.hasRoute<AppRoute.DownloadQueue>() == true -> BottomNavTab.PLAY
        dest?.hasRoute<AppRoute.SettingsRoot>() == true ||
            dest?.hasRoute<AppRoute.DownloadSettings>() == true ||
            dest?.hasRoute<AppRoute.PlayerSettings>() == true ||
            dest?.hasRoute<AppRoute.NotificationSettings>() == true ||
            dest?.hasRoute<AppRoute.Customization>() == true ||
            dest?.hasRoute<AppRoute.HelperTool>() == true -> BottomNavTab.SETTINGS
        else -> BottomNavTab.DOWNLOAD
    }

    fun openVideo(video: VideoItem) {
        container.extractorRepository.cacheVideos(listOf(video))
        navController.navigate(AppRoute.UnifiedPlayer(video.id, isLocal = false))
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!hideBottomNav) {
                BottomNavBar(
                    selectedTab = activeBottomTab,
                    onSelectTab = { tab ->
                        when (tab) {
                            BottomNavTab.DOWNLOAD -> navController.navigate(AppRoute.DownloadRoot(activeDownloadSubTab)) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                launchSingleTop = true
                            }
                            BottomNavTab.PLAY -> navController.navigate(AppRoute.PlayRoot) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                launchSingleTop = true
                            }
                            BottomNavTab.SETTINGS -> navController.navigate(AppRoute.SettingsRoot) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = AppRoute.DownloadRoot(0)
            ) {
                composable<AppRoute.DownloadRoot> { entry ->
                    val route = entry.toRoute<AppRoute.DownloadRoot>()
                    val topTabs = listOf("Search", "YouTube", "Music", "More")
                    val pagerState = rememberPagerState(
                        initialPage = activeDownloadSubTab.coerceIn(0, 3),
                        pageCount = { topTabs.size }
                    )

                    LaunchedEffect(route.subTab) {
                        if (route.subTab in 0..3 && pagerState.currentPage != route.subTab) {
                            pagerState.scrollToPage(route.subTab)
                        }
                    }
                    LaunchedEffect(pagerState.currentPage) {
                        activeDownloadSubTab = pagerState.currentPage
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.background)
                            .statusBarsPadding()
                    ) {
                        TopTabBar(
                            tabs = topTabs,
                            selectedIndex = pagerState.currentPage,
                            onSelectTab = { idx ->
                                activeDownloadSubTab = idx
                                scope.launch { pagerState.animateScrollToPage(idx) }
                            }
                        )
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) { page ->
                            when (page) {
                                0 -> HomeScreen(
                                    onSearchBarClick = { navController.navigate(AppRoute.SearchInput("")) },
                                    onDetectedUrlClick = { url ->
                                        formatSheetVideo = VideoItem(
                                            id = url.hashCode().toUInt().toString(16),
                                            title = url,
                                            channelName = "Clipboard URL",
                                            streamPageUrl = url
                                        )
                                    }
                                )
                                1 -> YouTubeFeedScreen(
                                    container = container,
                                    onOpenVideo = ::openVideo,
                                    onOpenFormatSheet = { formatSheetVideo = it },
                                    onOpenBatchSheet = { batchSheetVideos = it }
                                )
                                2 -> MusicTabScreen(
                                    container = container,
                                    onOpenVideo = ::openVideo,
                                    onOpenFormatSheet = { formatSheetVideo = it }
                                )
                                else -> MoreTabScreen(
                                    onOpenWhatsAppSaver = { navController.navigate(AppRoute.HelperTool("whatsapp")) }
                                )
                            }
                        }
                    }
                }

                composable<AppRoute.SearchInput> { entry ->
                    val route = entry.toRoute<AppRoute.SearchInput>()
                    SearchInputScreen(
                        initialQuery = route.initialQuery,
                        container = container,
                        onBack = { navController.popBackStack() },
                        onSubmitSearch = { q, filter ->
                            navController.navigate(AppRoute.SearchResults(q, filter)) {
                                popUpTo<AppRoute.SearchInput> { inclusive = true }
                            }
                        },
                        onOpenMoreTab = {
                            activeDownloadSubTab = 3
                            navController.navigate(AppRoute.DownloadRoot(3)) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable<AppRoute.SearchResults> { entry ->
                    val route = entry.toRoute<AppRoute.SearchResults>()
                    SearchResultsScreen(
                        query = route.query,
                        initialFilter = route.filter,
                        container = container,
                        onBack = { navController.popBackStack() },
                        onEditQuery = { q -> navController.navigate(AppRoute.SearchInput(q)) },
                        onClearToSearch = { navController.navigate(AppRoute.SearchInput("")) },
                        onOpenVideo = ::openVideo,
                        onOpenFormatSheet = { formatSheetVideo = it },
                        onOpenBatchSheet = { batchSheetVideos = it }
                    )
                }

                composable<AppRoute.UnifiedPlayer> { entry ->
                    val route = entry.toRoute<AppRoute.UnifiedPlayer>()
                    UnifiedPlayerScreen(
                        mediaKey = route.mediaKey,
                        isLocal = route.isLocal,
                        container = container,
                        onBack = { navController.popBackStack() },
                        onOpenFormatSheet = { formatSheetVideo = it },
                        onOpenRelatedVideo = { v ->
                            container.extractorRepository.cacheVideos(listOf(v))
                            navController.navigate(AppRoute.UnifiedPlayer(v.id, isLocal = false))
                        },
                        onOpenAddToPlaylist = { key, title ->
                            addPlaylistTarget = LibraryMediaItem(
                                mediaKey = key,
                                title = title,
                                subtitle = "SnapFetch",
                                uri = key,
                                thumbnailModel = null,
                                mediaType = MediaType.VIDEO,
                                durationSec = 0,
                                sizeBytes = 0L,
                                dateAddedMs = System.currentTimeMillis(),
                                isAppDownload = true
                            )
                        },
                        onOpenCompressSheet = { uri, title ->
                            compressRequest = CompressRequest(uri, title, 60, 0L, null)
                        },
                        isInPipMode = isInPipMode
                    )
                }

                composable<AppRoute.PlayRoot> {
                    PlayLibraryScreen(
                        container = container,
                        onOpenPlayer = { key, isLocal ->
                            navController.navigate(AppRoute.UnifiedPlayer(key, isLocal))
                        },
                        onOpenDownloadQueue = { navController.navigate(AppRoute.DownloadQueue) },
                        onOpenPlaylistDetail = { pid -> navController.navigate(AppRoute.PlaylistDetail(pid)) },
                        onOpenCompressSheet = { uri, title, dur, bytes, dlId ->
                            compressRequest = CompressRequest(uri, title, dur, bytes, dlId)
                        },
                        onOpenTrashCleaner = { navController.navigate(AppRoute.HelperTool("trash")) }
                    )
                }

                composable<AppRoute.PlaylistDetail> { entry ->
                    val route = entry.toRoute<AppRoute.PlaylistDetail>()
                    PlaylistDetailScreen(
                        playlistId = route.playlistId,
                        container = container,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { key, isLocal ->
                            navController.navigate(AppRoute.UnifiedPlayer(key, isLocal))
                        }
                    )
                }

                composable<AppRoute.DownloadQueue> {
                    DownloadQueueScreen(
                        container = container,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<AppRoute.SettingsRoot> {
                    SettingsScreen(
                        container = container,
                        onOpenDownloadSettings = { navController.navigate(AppRoute.DownloadSettings) },
                        onOpenPlayerSettings = { navController.navigate(AppRoute.PlayerSettings) },
                        onOpenNotificationSettings = { navController.navigate(AppRoute.NotificationSettings) },
                        onOpenCustomization = { navController.navigate(AppRoute.Customization) },
                        onOpenTool = { id -> navController.navigate(AppRoute.HelperTool(id)) }
                    )
                }

                composable<AppRoute.DownloadSettings> {
                    DownloadSettingsScreen(container = container, onBack = { navController.popBackStack() })
                }

                composable<AppRoute.PlayerSettings> {
                    PlayerSettingsScreen(container = container, onBack = { navController.popBackStack() })
                }

                composable<AppRoute.NotificationSettings> {
                    NotificationSettingsScreen(container = container, onBack = { navController.popBackStack() })
                }

                composable<AppRoute.Customization> {
                    CustomizationScreen(container = container, onBack = { navController.popBackStack() })
                }

                composable<AppRoute.HelperTool> { entry ->
                    val route = entry.toRoute<AppRoute.HelperTool>()
                    HelperToolStubScreen(
                        toolId = route.toolId,
                        container = container,
                        onBack = { navController.popBackStack() },
                        onPlayUri = { uri -> navController.navigate(AppRoute.UnifiedPlayer(uri, isLocal = true)) }
                    )
                }
            }
        }
    }

    formatSheetVideo?.let { video ->
        DownloadFormatSheet(
            video = video,
            container = container,
            onDismiss = { formatSheetVideo = null },
            onDownloadEnqueued = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    batchSheetVideos?.let { list ->
        BatchDownloadSheet(
            videos = list,
            container = container,
            onDismiss = { batchSheetVideos = null },
            onEnqueued = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    compressRequest?.let { req ->
        VideoCompressSheet(
            sourceUri = req.sourceUri,
            title = req.title,
            durationSec = req.durationSec,
            originalSizeBytes = req.originalBytes,
            container = container,
            initialPreset = settings.compressionPreset,
            initialDeleteOriginal = settings.deleteOriginalAfterCompress,
            onDismiss = { compressRequest = null },
            onStartCompression = { preset: CompressionPreset, deleteOrig: Boolean ->
                container.compressVideoUseCase(
                    sourceUri = req.sourceUri,
                    title = req.title,
                    preset = preset,
                    deleteOriginal = deleteOrig,
                    downloadId = req.downloadId
                )
                scope.launch { snackbarHostState.showSnackbar("Compressing \"${req.title}\" in background...") }
            }
        )
    }

    addPlaylistTarget?.let { target ->
        AddToPlaylistSheet(
            itemsToAdd = listOf(target),
            container = container,
            onDismiss = { addPlaylistTarget = null },
            onAdded = { plName ->
                scope.launch { snackbarHostState.showSnackbar("Added to \"$plName\"") }
            }
        )
    }
}
