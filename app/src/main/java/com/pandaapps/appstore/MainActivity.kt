package com.pandaapps.appstore

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.pandaapps.appstore.ui.navigation.PandaNavGraph
import com.pandaapps.appstore.ui.theme.PandaTheme
import com.pandaapps.appstore.util.DeepLinks

/**
 * Single activity (singleTop). Deep links (`pandastore://app/<pkg>`, `pandastore://home`) are parsed
 * here and published on [AppContainer.pendingDeepLink]; [PandaNavGraph] consumes them.
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { appContainer }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            container.appLog.i(TAG, "Notification permission ${if (granted) "granted" else "denied"}")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // On recreation (rotation) the original link was already handled.
        if (savedInstanceState == null) {
            handleIntent(intent)
            requestNotificationPermissionOnce()
        }

        setContent {
            PandaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PandaNavGraph()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Installs/uninstalls may have happened while we were away (and permissions may have changed).
        container.installedAppsRepository.refresh()
        container.installManager.resumePendingConfirmations()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        // Reopening a task from Recents re-delivers its base intent — the link that started it,
        // already handled back then. Open the app normally instead of replaying it.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        DeepLinks.parse(intent.data)?.let(container::onDeepLink)
    }

    /** API 33+: ask for POST_NOTIFICATIONS on first launch only; Settings has a fix button afterwards. */
    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        prefs.edit { putBoolean(KEY_ASKED_NOTIFICATIONS, true) }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val TAG = "Main"
        const val PREFS = "ui_state"
        const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
