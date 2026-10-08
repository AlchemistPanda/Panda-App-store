package com.pandagallery.app.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Helper to detect, extract, strip, and preserve embedded MP4 micro-videos in Samsung and Google Motion Photos.
 * Motion photos append an MP4 video container at the tail of the JPEG file with a standard "ftyp" box header.
 */
object MotionPhotoHelper {

    private val FTYP_BYTES = byteArrayOf(0x66.toByte(), 0x74.toByte(), 0x79.toByte(), 0x70.toByte()) // "ftyp"

    /**
     * Locates the start index of the embedded MP4 container within raw byte array.
     * Searches backwards from the end of the file for the "ftyp" box identifier.
     * Returns the start index (4 bytes before "ftyp" where the 32-bit box size begins), or -1 if not found
     * or if the box is at the very start of the file (a plain HEIC/AVIF, not an appended clip).
     */
    fun findMp4StartIndex(bytes: ByteArray): Int {
        if (bytes.size < 1024) return -1
        val searchStart = (bytes.size - 4).coerceAtLeast(0)
        for (i in searchStart downTo 0) {
            if (bytes[i] == FTYP_BYTES[0] &&
                bytes[i + 1] == FTYP_BYTES[1] &&
                bytes[i + 2] == FTYP_BYTES[2] &&
                bytes[i + 3] == FTYP_BYTES[3]
            ) {
                val mp4StartIndex = i - 4
                // Must start after a still: an HEIC/AVIF file opens with its own ftyp box at
                // offset 4, and treating that as a clip made the whole photo its own "video".
                if (mp4StartIndex > 0 && (bytes.size - mp4StartIndex) >= 1024) {
                    return mp4StartIndex
                }
            }
        }
        return -1
    }

    /**
     * Determines whether the given media contains an embedded MP4 micro-video,
     * returning Pair(startIndex, lengthBytes), or null if not a Motion Photo.
     */
    suspend fun getMotionVideoRange(context: Context, uri: Uri): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val startIndex = findMp4StartIndex(bytes)
            if (startIndex >= 0) {
                Pair(startIndex, bytes.size - startIndex)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts raw MP4 video bytes from the Motion Photo file without saving to disk.
     */
    suspend fun extractMotionVideoBytes(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val startIndex = findMp4StartIndex(bytes)
            if (startIndex >= 0) {
                bytes.copyOfRange(startIndex, bytes.size)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts the embedded MP4 video to a cached MP4 file for playback.
     * Returns null if no embedded video is found.
     */
    suspend fun extractMotionVideo(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val videoBytes = extractMotionVideoBytes(context, uri) ?: return@withContext null
            val cacheFile = File(context.cacheDir, "motion_photo_${System.currentTimeMillis()}.mp4")
            FileOutputStream(cacheFile).use { out ->
                out.write(videoBytes)
                out.flush()
            }
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cacheFile
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Strips the embedded MP4 video track from the JPEG, writing only the pure still image bytes
     * (0 until mp4StartIndex) to [targetFile].
     * Retains exact 1:1 original image bitstream while recovering 5-15 MB of storage.
     */
    suspend fun stripMotionVideo(context: Context, uri: Uri, targetFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext false
            val startIndex = findMp4StartIndex(bytes)
            if (startIndex <= 0) return@withContext false

            FileOutputStream(targetFile).use { out ->
                out.write(bytes, 0, startIndex)
                out.flush()
            }
            targetFile.exists() && targetFile.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Exports an extracted MP4 micro-video into Android MediaStore under Movies/PandaGallery.
     */
    suspend fun saveMotionVideoToGallery(
        context: Context,
        videoFile: File,
        baseName: String = "MotionVideo_${System.currentTimeMillis()}",
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val cleanName = if (baseName.endsWith(".mp4", ignoreCase = true)) baseName else "$baseName.mp4"
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, cleanName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/PandaGallery")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val insertedUri = resolver.insert(collection, values) ?: return@withContext null
            resolver.openOutputStream(insertedUri)?.use { outStream ->
                FileInputStream(videoFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalizeValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(insertedUri, finalizeValues, null, null)
            }
            insertedUri
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Turns the compressed JPEG [targetFile] back into a motion photo: writes the Google Motion
     * Photo XMP descriptor and appends the MP4 [videoBytes] after the still.
     *
     * Appending the bytes alone left a file only a byte-scanner (this app) recognised — Google
     * Photos and other galleries find the clip through the XMP `GCamera`/`Container` tags, and
     * showed a plain still. Both tags give the clip's position as a length counted back from the
     * end of the file, so inserting the XMP near the start doesn't change them.
     *
     * Returns false (and leaves the file as it was) when [targetFile] is not a JPEG.
     */
    suspend fun appendMotionVideo(targetFile: File, videoBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!targetFile.exists() || videoBytes.isEmpty()) return@withContext false
            val still = JpegSegments.withXmp(targetFile.readBytes(), motionPhotoXmp(videoBytes.size))
                ?: return@withContext false
            FileOutputStream(targetFile).use { out ->
                out.write(still)
                out.write(videoBytes)
                out.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Motion Photo format v1 descriptor, plus the legacy MicroVideo tags older readers use. */
    internal fun motionPhotoXmp(videoLength: Int): String =
        "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">" +
            "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description rdf:about=\"\"" +
            " xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"" +
            " xmlns:Container=\"http://ns.google.com/photos/1.0/container/\"" +
            " xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\"" +
            " GCamera:MotionPhoto=\"1\" GCamera:MotionPhotoVersion=\"1\"" +
            " GCamera:MotionPhotoPresentationTimestampUs=\"-1\"" +
            " GCamera:MicroVideo=\"1\" GCamera:MicroVideoVersion=\"1\"" +
            " GCamera:MicroVideoOffset=\"$videoLength\" GCamera:MicroVideoPresentationTimestampUs=\"-1\">" +
            "<Container:Directory><rdf:Seq>" +
            "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\" Item:Length=\"0\" Item:Padding=\"0\"/></rdf:li>" +
            "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\" Item:Length=\"$videoLength\" Item:Padding=\"0\"/></rdf:li>" +
            "</rdf:Seq></Container:Directory>" +
            "</rdf:Description></rdf:RDF></x:xmpmeta>" +
            "<?xpacket end=\"w\"?>"
}
