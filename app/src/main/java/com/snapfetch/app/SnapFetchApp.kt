package com.snapfetch.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader

class SnapFetchApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.downloadNotificationHelper.createNotificationChannels()
        container.downloadCoordinator.registerNetworkAutoResume()
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return container.coilFactory.newImageLoader(context)
    }
}
