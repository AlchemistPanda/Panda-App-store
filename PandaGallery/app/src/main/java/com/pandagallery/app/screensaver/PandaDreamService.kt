package com.pandagallery.app.screensaver

import android.content.ContentUris
import android.graphics.Color
import android.provider.MediaStore
import android.service.dreams.DreamService
import android.util.Size
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.ImageView

class PandaDreamService : DreamService() {
    private lateinit var imageView: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private var photoIds = emptyList<Long>()
    private var position = 0
    private val advance = object : Runnable {
        override fun run() {
            showNextPhoto()
            handler.postDelayed(this, DISPLAY_MILLIS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        imageView = ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        setContentView(imageView)
        photoIds = queryRecentPhotoIds()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.post(advance)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(advance)
        super.onDreamingStopped()
    }

    private fun queryRecentPhotoIds(): List<Long> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        return contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < MAX_PHOTOS) add(cursor.getLong(0))
            }
        }.orEmpty()
    }

    private fun showNextPhoto() {
        val id = photoIds.getOrNull(position++ % photoIds.size.coerceAtLeast(1)) ?: return
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val uri = ContentUris.withAppendedId(collection, id)
        runCatching { contentResolver.loadThumbnail(uri, Size(1920, 1080), null) }
            .onSuccess { bitmap ->
                imageView.animate().alpha(0f).setDuration(250).withEndAction {
                    imageView.setImageBitmap(bitmap)
                    imageView.animate().alpha(1f).setDuration(500).start()
                }.start()
            }
    }

    private companion object {
        const val DISPLAY_MILLIS = 10_000L
        const val MAX_PHOTOS = 100
    }
}
