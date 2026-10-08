package com.pandagallery.app.data.coil

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * High-performance Coil 3 Fetcher that delegates directly to Android's [ContentResolver.loadThumbnail].
 *
 * For local MediaStore and content:// URIs (especially videos and large camera photos),
 * this retrieves the pre-generated, hardware-accelerated thumbnail from the Android OS MediaStore
 * in ~2-5ms instead of reading the entire video/image file and decoding full frames on CPU.
 */
class MediaStoreThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        if (!isSupportedUri(uri)) return@withContext null

        val widthPx = options.size.width.toPx(DEFAULT_THUMBNAIL_SIZE)
        val heightPx = options.size.height.toPx(DEFAULT_THUMBNAIL_SIZE)

        // Only use the thumbnail loader when a reasonable thumbnail dimension is requested (<= 1024px)
        // or for any video URI (where decoding the full file via MediaMetadataRetriever is extremely slow).
        val isVideo = isVideoUri(uri)
        if (!isVideo && widthPx > MAX_THUMBNAIL_DIMENSION && heightPx > MAX_THUMBNAIL_DIMENSION) {
            // Fall back to Coil's full image decoders for full-screen / high-res views
            return@withContext null
        }

        val targetWidth = widthPx.coerceIn(MIN_THUMBNAIL_DIMENSION, MAX_THUMBNAIL_DIMENSION)
        val targetHeight = heightPx.coerceIn(MIN_THUMBNAIL_DIMENSION, MAX_THUMBNAIL_DIMENSION)
        val targetSize = Size(targetWidth, targetHeight)

        val signal = CancellationSignal()
        val job = currentCoroutineContext()[Job]
        job?.invokeOnCompletion { signal.cancel() }

        try {
            val bitmap = context.contentResolver.loadThumbnail(uri, targetSize, signal)
            ImageFetchResult(
                image = bitmap.asImage(),
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        } catch (_: Throwable) {
            // If loadThumbnail fails (e.g. non-standard provider or corrupted entry),
            // return null so Coil gracefully falls back to standard decoders
            null
        }
    }

    private fun Dimension.toPx(default: Int): Int = when (this) {
        is Dimension.Pixels -> px
        else -> default
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!isSupportedUri(data)) return null
            return MediaStoreThumbnailFetcher(context, data, options)
        }
    }

    companion object {
        private const val DEFAULT_THUMBNAIL_SIZE = 384
        private const val MIN_THUMBNAIL_DIMENSION = 64
        private const val MAX_THUMBNAIL_DIMENSION = 1024

        fun isSupportedUri(uri: Uri): Boolean {
            val scheme = uri.scheme?.lowercase() ?: return false
            return scheme == ContentResolver.SCHEME_CONTENT
        }

        fun isSupportedUriString(uriString: String): Boolean {
            return uriString.startsWith("content://", ignoreCase = true)
        }

        fun isVideoUri(uri: Uri): Boolean {
            return isVideoUriString(uri.toString())
        }

        fun isVideoUriString(uriString: String): Boolean {
            val lower = uriString.lowercase()
            return lower.contains("/video/") || lower.contains("video")
        }
    }
}
