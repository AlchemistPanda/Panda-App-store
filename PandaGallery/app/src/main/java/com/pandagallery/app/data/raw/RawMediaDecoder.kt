package com.pandagallery.app.data.raw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles detection, EXIF thumbnail extraction, hardware decoding, and JPEG export
 * for RAW (DNG, CR2, NEF, ARW) and modern formats (HEIC, AVIF).
 */
@Singleton
class RawMediaDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isRawFormat(fileNameOrPath: String): Boolean = isRaw(fileNameOrPath)

    fun isNextGenFormat(fileNameOrPath: String): Boolean = isNextGen(fileNameOrPath)

    companion object {
        private val rawExtensions = setOf("dng", "cr2", "nef", "arw", "raw", "rw2")
        private val nextGenExtensions = setOf("heic", "heif", "avif")

        fun isRaw(fileNameOrPath: String): Boolean {
            val ext = fileNameOrPath.substringAfterLast('.', "").lowercase()
            return ext in rawExtensions
        }

        fun isNextGen(fileNameOrPath: String): Boolean {
            val ext = fileNameOrPath.substringAfterLast('.', "").lowercase()
            return ext in nextGenExtensions
        }
    }

    /**
     * Attempts fast EXIF thumbnail extraction without loading the full 50MB+ RAW sensor data.
     */
    suspend fun extractRawThumbnail(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                if (exif.hasThumbnail()) {
                    val thumbBytes = exif.thumbnailBytes
                    if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                        return@withContext BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size)
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback: decode full stream with downsampling
        decodeDownsampled(uri, targetSize = 1080)
    }

    suspend fun decodeDownsampled(uri: Uri, targetSize: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val maxDim = maxOf(options.outWidth, options.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > targetSize) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun convertToJpeg(uri: Uri, quality: Int = 92): File? = withContext(Dispatchers.IO) {
        val bitmap = decodeDownsampled(uri, targetSize = 4096) ?: return@withContext null
        val outputFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outputFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
        outputFile
    }
}
