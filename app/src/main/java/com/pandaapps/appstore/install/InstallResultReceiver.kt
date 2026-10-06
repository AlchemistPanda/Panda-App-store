package com.pandaapps.appstore.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pandaapps.appstore.PandaStoreApp

/**
 * Receives PackageInstaller session status (explicit, non-exported). All logic lives in
 * [InstallManager.onSessionResult]; this only forwards. Works after process death because the
 * commit intent carries everything needed (see [InstallRequest]).
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SESSION_RESULT) return
        val app = context.applicationContext as? PandaStoreApp ?: return
        app.container.installManager.onSessionResult(intent)
    }

    companion object {
        const val ACTION_SESSION_RESULT = "com.pandaapps.appstore.action.INSTALL_SESSION_RESULT"
        const val EXTRA_PACKAGE = "com.pandaapps.appstore.extra.PACKAGE"
        const val EXTRA_APP_NAME = "com.pandaapps.appstore.extra.APP_NAME"
        const val EXTRA_VERSION_NAME = "com.pandaapps.appstore.extra.VERSION_NAME"
        const val EXTRA_VERSION_CODE = "com.pandaapps.appstore.extra.VERSION_CODE"
        const val EXTRA_BACKGROUND = "com.pandaapps.appstore.extra.BACKGROUND"
        const val EXTRA_APK_PATH = "com.pandaapps.appstore.extra.APK_PATH"
    }
}
