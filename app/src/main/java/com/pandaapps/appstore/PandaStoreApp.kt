package com.pandaapps.appstore

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.pandaapps.appstore.notify.Notifications
import com.pandaapps.appstore.work.UpdateScheduler

class PandaStoreApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appLog.i("App", "Panda App Store ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) starting")
        Notifications.createChannels(this)
        container.installManager.cleanupOnStart()
        // Background update checks; settings changes re-enqueue via StoreViewModel.
        UpdateScheduler.scheduleFromSettings(this)
    }

    /** Coil shares the app's OkHttp client (connection pool, timeouts). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttp })) }
            .crossfade(true)
            .build()
}
