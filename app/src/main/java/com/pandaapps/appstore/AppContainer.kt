package com.pandaapps.appstore

import android.content.Context
import com.pandaapps.appstore.data.CatalogJson
import com.pandaapps.appstore.data.CatalogRepository
import com.pandaapps.appstore.data.InstalledAppsRepository
import com.pandaapps.appstore.data.SettingsRepository
import com.pandaapps.appstore.install.InstallManager
import com.pandaapps.appstore.util.AppLog
import com.pandaapps.appstore.util.DeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual DI: process-wide singletons, created once in [PandaStoreApp.onCreate].
 * Reach it with `context.appContainer` (from Activities, ViewModels via the Application, workers, receivers).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Long-lived scope for repository/installer work that must outlive screens. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val appLog: AppLog = AppLog()

    val json: Json = CatalogJson

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    val installedAppsRepository: InstalledAppsRepository = InstalledAppsRepository(appContext)

    val catalogRepository: CatalogRepository = CatalogRepository(appContext, okHttp, json, appLog, appScope)

    val installManager: InstallManager = InstallManager(appContext, okHttp, installedAppsRepository, appLog, appScope)

    // --- Deep links -------------------------------------------------------------------------------
    // MainActivity parses pandastore:// intents (onCreate + onNewIntent) into [pendingDeepLink].
    // The nav graph collects it, navigates, then calls [consumeDeepLink].

    private val _pendingDeepLink = MutableStateFlow<DeepLink?>(null)
    val pendingDeepLink: StateFlow<DeepLink?> = _pendingDeepLink.asStateFlow()

    fun onDeepLink(link: DeepLink) {
        appLog.i("DeepLink", "Received $link")
        _pendingDeepLink.value = link
    }

    fun consumeDeepLink() {
        _pendingDeepLink.value = null
    }
}

/** The process-wide [AppContainer]. */
val Context.appContainer: AppContainer
    get() = (applicationContext as PandaStoreApp).container
