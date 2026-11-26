package com.snapfetch.app

import android.content.Context
import com.snapfetch.app.core.datastore.UserSettingsDataStore
import com.snapfetch.app.core.perf.CoilLowMemFactory
import com.snapfetch.app.core.perf.LowRamConfig
import com.snapfetch.app.core.perf.StorageGuard
import com.snapfetch.app.core.storage.MediaStoreSaver
import com.snapfetch.app.core.storage.SafDirectoryHelper
import com.snapfetch.app.core.storage.StorageScanner
import com.snapfetch.app.data.compressor.Media3VideoCompressor
import com.snapfetch.app.data.downloader.DataUsageTracker
import com.snapfetch.app.data.downloader.DownloadNotificationHelper
import com.snapfetch.app.data.downloader.DownloadQueueCoordinator
import com.snapfetch.app.data.downloader.ResumableHttpDownloader
import com.snapfetch.app.data.extractor.MediaExtractorRepository
import com.snapfetch.app.data.extractor.NewPipeClient
import com.snapfetch.app.data.extractor.WebViewSniffer
import com.snapfetch.app.data.local.AppDatabase
import com.snapfetch.app.domain.usecase.CompressVideoUseCase
import com.snapfetch.app.domain.usecase.EnqueueDownloadUseCase
import com.snapfetch.app.domain.usecase.ResolveFormatsUseCase
import com.snapfetch.app.domain.usecase.SearchMediaUseCase
import com.snapfetch.app.feature.player.PlaybackQueueController
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val lowRamConfig by lazy { LowRamConfig(appContext) }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val coilFactory by lazy { CoilLowMemFactory(lowRamConfig, okHttpClient) }
    val settingsDataStore by lazy { UserSettingsDataStore(appContext, lowRamConfig) }
    val database by lazy { AppDatabase.build(appContext) }

    val storageGuard by lazy { StorageGuard(appContext) }
    val safHelper by lazy { SafDirectoryHelper(appContext) }
    val mediaStoreSaver by lazy { MediaStoreSaver(appContext, safHelper) }
    val storageScanner by lazy { StorageScanner(appContext) }

    val newPipeClient by lazy { NewPipeClient(okHttpClient) }
    val webViewSniffer by lazy { WebViewSniffer(appContext, okHttpClient) }
    val extractorRepository by lazy { MediaExtractorRepository(newPipeClient, webViewSniffer) }

    val dataUsageTracker by lazy { DataUsageTracker(appContext, settingsDataStore) }
    val downloadNotificationHelper by lazy { DownloadNotificationHelper(appContext) }
    val resumableDownloader by lazy {
        ResumableHttpDownloader(
            okHttpClient = okHttpClient,
            downloadDao = database.downloadDao(),
            dataUsageTracker = dataUsageTracker,
            settingsDataStore = settingsDataStore
        )
    }

    val downloadCoordinator by lazy {
        DownloadQueueCoordinator(
            context = appContext,
            downloadDao = database.downloadDao(),
            settingsDataStore = settingsDataStore,
            storageGuard = storageGuard,
            storageScanner = storageScanner
        )
    }

    val videoCompressor by lazy { Media3VideoCompressor(appContext) }
    val playbackQueueController by lazy { PlaybackQueueController() }

    val searchMediaUseCase by lazy { SearchMediaUseCase(extractorRepository, database.searchHistoryDao()) }
    val resolveFormatsUseCase by lazy { ResolveFormatsUseCase(extractorRepository) }
    val enqueueDownloadUseCase by lazy { EnqueueDownloadUseCase(downloadCoordinator) }
    val compressVideoUseCase by lazy { CompressVideoUseCase(appContext) }
}
