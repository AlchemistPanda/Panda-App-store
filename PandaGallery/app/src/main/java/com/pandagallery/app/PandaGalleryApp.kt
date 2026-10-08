package com.pandagallery.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.gif.AnimatedImageDecoder
import coil3.video.VideoFrameDecoder
import coil3.svg.SvgDecoder
import dagger.hilt.android.HiltAndroidApp
import com.pandagallery.app.data.diagnostics.CrashLog
import com.pandagallery.app.data.trash.TrashCleanupWorker
import com.pandagallery.app.data.compression.SafetyVaultCleanupWorker
import com.pandagallery.app.data.smart.SmartIndexScheduler
import com.pandagallery.app.data.smart.SmartIndexChangeObserver
import com.pandagallery.app.data.coil.MediaStoreThumbnailFetcher

@HiltAndroidApp
class PandaGalleryApp : Application(), SingletonImageLoader.Factory {
    private lateinit var smartIndexChangeObserver: SmartIndexChangeObserver

    override fun onCreate() {
        super.onCreate()
        // First, so a crash anywhere in the rest of startup is still captured.
        CrashLog.install(this)
        TrashCleanupWorker.schedule(this)
        SafetyVaultCleanupWorker.schedule(this)
        SmartIndexScheduler.ensurePeriodic(this)
        // Picks up anything other apps added while Panda Gallery was not running.
        SmartIndexScheduler.enqueueCatchUp(this)
        smartIndexChangeObserver = SmartIndexChangeObserver.start(this)
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(150)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.30) // Use 30% of available memory
                    .maxSizePercent(context, 0.35) // Use 35% of available memory for instant grid scrolling
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024) // 512MB disk cache
                    .build()
            }
            .components {
                // Without this, animated GIFs render as a single still frame.
                add(AnimatedImageDecoder.Factory())
                // Priority fast thumbnail fetcher for MediaStore / ContentResolver URIs
                add(MediaStoreThumbnailFetcher.Factory(context))
                add(VideoFrameDecoder.Factory())
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
