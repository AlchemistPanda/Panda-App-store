package com.pandagallery.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pandagallery.app.MainActivity
import com.pandagallery.app.R

class PandaGalleryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_panda_gallery).apply {
                setOnClickPendingIntent(R.id.widget_root, pendingIntent(context, MainActivity.ACTION_MEMORIES, 1))
                setOnClickPendingIntent(R.id.widget_search, pendingIntent(context, MainActivity.ACTION_SEARCH, 2))
                setOnClickPendingIntent(R.id.widget_cleanup, pendingIntent(context, MainActivity.ACTION_CLEANUP, 3))
            }
            manager.updateAppWidget(id, views)
        }
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
