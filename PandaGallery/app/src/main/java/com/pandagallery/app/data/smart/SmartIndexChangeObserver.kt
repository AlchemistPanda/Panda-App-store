package com.pandagallery.app.data.smart

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

/**
 * Keeps the smart index following the device's media without the user asking.
 *
 * Watches images *and* videos: the index covers both, and registering only on the image
 * collection meant a newly recorded video stayed unsearchable until the next charging pass.
 */
class SmartIndexChangeObserver private constructor(
    private val context: Context,
) : ContentObserver(Handler(Looper.getMainLooper())) {
    override fun onChange(selfChange: Boolean) {
        // Debounced downstream — a single camera burst fires this many times.
        SmartIndexScheduler.enqueueContentChange(context)
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(this)
    }

    companion object {
        fun start(context: Context): SmartIndexChangeObserver {
            val applicationContext = context.applicationContext
            return SmartIndexChangeObserver(applicationContext).also { observer ->
                listOf(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                ).forEach { collection ->
                    applicationContext.contentResolver.registerContentObserver(
                        collection,
                        true,
                        observer,
                    )
                }
            }
        }
    }
}
