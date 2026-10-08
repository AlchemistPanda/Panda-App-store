package com.pandagallery.app.data.sharing

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class MediaSharePreparer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: PreferencesDataSource? = null,
) {
    /**
     * @param copyOriginals copies each item into our own cache before sharing, for items
     *   whose URI belongs to another app's provider. Such a URI cannot simply be forwarded:
     *   the read grant is ours, not the share target's.
     */
    suspend fun prepare(
        items: List<MediaItem>,
        copyOriginals: Boolean = false,
        convertHeif: Boolean? = null,
        convertRaw: Boolean? = null,
        stripLocation: Boolean? = null,
    ): SharePayload = withContext(Dispatchers.IO) {
        require(items.isNotEmpty()) { "At least one media item is required" }
        clearExpiredFiles()

        val prefs = preferencesDataSource?.userPreferencesFlow?.firstOrNull()
        val shouldConvertHeif = convertHeif ?: (prefs?.convertHeifWhenSharing ?: true)
        val shouldConvertRaw = convertRaw ?: (prefs?.convertRawWhenSharing ?: true)
        val shouldStripLocation = stripLocation ?: (prefs?.removeLocationWhenSharing ?: false)

        val preparedItems = items.map { item ->
            val isHeif = isHeifFormat(item.mimeType)
            val isRaw = isRawFormat(item.mimeType, item.displayName)

            val needsConversion = when {
                item.isVideo -> false
                isHeif -> shouldConvertHeif
                isRaw -> shouldConvertRaw
                else -> requiresJpegShareConversion(item.mimeType)
            }

            when {
                needsConversion -> {
                    try {
                        SharedMedia(convertToTemporaryJpeg(item.uri, shouldStripLocation), JPEG_MIME_TYPE)
                    } catch (error: Exception) {
                        if (copyOriginals || shouldStripLocation) SharedMedia(copyForSharing(item, shouldStripLocation), item.mimeType)
                        else SharedMedia(item.uri, item.mimeType)
                    }
                }
                copyOriginals || shouldStripLocation -> SharedMedia(copyForSharing(item, shouldStripLocation), item.mimeType)
                else -> SharedMedia(item.uri, item.mimeType)
            }
        }
        SharePayload(
            uris = preparedItems.map { it.uri },
            mimeType = sharingMimeType(preparedItems.map { it.mimeType }),
        )
    }

    private fun convertToTemporaryJpeg(sourceUri: Uri, stripLocation: Boolean = false): Uri {
        val output = File(shareDirectory(), "share_${UUID.randomUUID()}.jpg")
        try {
            val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val longestEdge = maxOf(width, height)
                if (longestEdge > MAX_SHARED_IMAGE_EDGE_PX) {
                    val scale = MAX_SHARED_IMAGE_EDGE_PX.toDouble() / longestEdge
                    decoder.setTargetSize(
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
            try {
                val jpegBitmap = bitmap.withOpaqueBackground()
                try {
                    FileOutputStream(output).use { stream ->
                        check(jpegBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                            "Image encoder could not create a temporary JPEG"
                        }
                    }
                } finally {
                    if (jpegBitmap !== bitmap) jpegBitmap.recycle()
                }
            } finally {
                bitmap.recycle()
            }
            if (stripLocation) {
                stripGpsMetadata(output)
            }
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }

    /** Streams an item into the share cache byte for byte, leaving the encoding alone. */
    private fun copyForSharing(item: MediaItem, stripLocation: Boolean = false): Uri {
        val extension = item.displayName.substringAfterLast('.', "").ifBlank {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(item.mimeType) ?: "bin"
        }
        val output = File(shareDirectory(), "share_${UUID.randomUUID()}.$extension")
        try {
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                FileOutputStream(output).use(input::copyTo)
            } ?: throw IOException("Nothing to read behind ${item.uri}")
            if (stripLocation && item.isImage) {
                stripGpsMetadata(output)
            }
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
        } catch (error: IOException) {
            output.delete()
            throw error
        } catch (error: SecurityException) {
            output.delete()
            throw error
        }
    }

    internal fun stripGpsMetadata(file: File) {
        try {
            val exif = ExifInterface(file.absolutePath)
            val gpsTags = listOf(
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_GPS_PROCESSING_METHOD,
                ExifInterface.TAG_GPS_AREA_INFORMATION,
                ExifInterface.TAG_GPS_SPEED,
                ExifInterface.TAG_GPS_SPEED_REF,
                ExifInterface.TAG_GPS_TRACK,
                ExifInterface.TAG_GPS_TRACK_REF,
                ExifInterface.TAG_GPS_IMG_DIRECTION,
                ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                ExifInterface.TAG_GPS_MAP_DATUM,
                ExifInterface.TAG_GPS_DEST_LATITUDE,
                ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
                ExifInterface.TAG_GPS_DEST_LONGITUDE,
                ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
                ExifInterface.TAG_GPS_DEST_BEARING,
                ExifInterface.TAG_GPS_DEST_BEARING_REF,
                ExifInterface.TAG_GPS_DEST_DISTANCE,
                ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
                ExifInterface.TAG_GPS_DIFFERENTIAL,
            )
            var modified = false
            for (tag in gpsTags) {
                if (exif.getAttribute(tag) != null) {
                    exif.setAttribute(tag, null)
                    modified = true
                }
            }
            if (modified) {
                exif.saveAttributes()
            }
        } catch (_: Exception) {
            // Non-fatal if format does not support EXIF modifications
        }
    }

    private fun Bitmap.withOpaqueBackground(): Bitmap {
        if (!hasAlpha()) return this
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).apply {
                drawColor(Color.WHITE)
                drawBitmap(this@withOpaqueBackground, 0f, 0f, null)
            }
        }
    }

    private fun clearExpiredFiles() {
        val expiry = System.currentTimeMillis() - SHARE_FILE_LIFETIME_MS
        shareDirectory().listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < expiry) file.delete()
        }
    }

    private fun shareDirectory(): File = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }

    private data class SharedMedia(val uri: Uri, val mimeType: String)

    private companion object {
        const val SHARE_DIRECTORY = "share"
        const val JPEG_MIME_TYPE = "image/jpeg"
        const val JPEG_QUALITY = 92
        const val MAX_SHARED_IMAGE_EDGE_PX = 5_120
        const val SHARE_FILE_LIFETIME_MS = 24 * 60 * 60 * 1_000L
    }
}

data class SharePayload(
    val uris: List<Uri>,
    val mimeType: String,
) {
    fun createChooserIntent(): Intent {
        val shareIntent = Intent(
            if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE,
        ).apply {
            type = mimeType
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.single())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            // Built from the type we already know rather than ClipData.newUri, which needs a
            // ContentResolver to sniff the type and threw on every share without one.
            clipData = ClipData(
                ClipDescription("Shared media", arrayOf(mimeType)),
                ClipData.Item(uris.first()),
            ).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share via")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal fun requiresJpegShareConversion(mimeType: String): Boolean =
    mimeType.startsWith("image/") && mimeType !in setOf("image/jpeg", "image/png")

internal fun isHeifFormat(mimeType: String): Boolean =
    mimeType in listOf("image/heif", "image/heic")

internal fun isRawFormat(mimeType: String, displayName: String = ""): Boolean =
    mimeType in listOf("image/x-adobe-dng", "image/x-canon-cr2", "image/x-nikon-nef", "image/x-sony-arw") ||
        displayName.endsWith(".dng", true) || displayName.endsWith(".raw", true)

internal fun sharingMimeType(mimeTypes: List<String>): String = when {
    mimeTypes.distinct().size == 1 -> mimeTypes.first()
    mimeTypes.all { it.startsWith("image/") } -> "image/*"
    mimeTypes.all { it.startsWith("video/") } -> "video/*"
    else -> "*/*"
}
