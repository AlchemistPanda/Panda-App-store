package com.pandagallery.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pandagallery.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receiver for handling Edge Panel Quick Access actions and system shortcuts.
 */
@AndroidEntryPoint
class EdgePanelService : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            ACTION_EDGE_OPEN_CAMERA -> {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    this.action = MainActivity.ACTION_SEARCH
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launchIntent)
            }
            ACTION_EDGE_OPEN_FAVORITES -> {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("target_route", "favorites")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launchIntent)
            }
            ACTION_EDGE_OPEN_STORIES -> {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("target_route", "stories")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launchIntent)
            }
        }
    }

    companion object {
        const val ACTION_EDGE_OPEN_CAMERA = "com.pandagallery.app.EDGE_CAMERA"
        const val ACTION_EDGE_OPEN_FAVORITES = "com.pandagallery.app.EDGE_FAVORITES"
        const val ACTION_EDGE_OPEN_STORIES = "com.pandagallery.app.EDGE_STORIES"
    }
}
