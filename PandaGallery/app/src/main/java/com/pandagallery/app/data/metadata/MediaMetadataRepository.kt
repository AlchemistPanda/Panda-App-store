package com.pandagallery.app.data.metadata

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaMetadataRepository @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun read(item: MediaItem): MediaMetadata = withContext(Dispatchers.IO) {
        if (!item.isImage) return@withContext MediaMetadata(item.id, format = classifyFormat(item, null))
        val descriptor = context.contentResolver.openFileDescriptor(item.uri, "r")
            ?: throw IOException("Unable to read metadata")
        descriptor.use {
            val exif = ExifInterface(it.fileDescriptor)
            val latLong = exif.latLong
            val xmp = exif.getAttribute(ExifInterface.TAG_XMP)
            MediaMetadata(
                mediaId = item.id,
                cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE),
                cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
                lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, Double.NaN).takeUnless(Double::isNaN),
                exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
                iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { value -> value > 0 },
                focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, Double.NaN).takeUnless(Double::isNaN),
                dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                latitude = latLong?.getOrNull(0),
                longitude = latLong?.getOrNull(1),
                altitude = exif.getAltitude(Double.NaN).takeUnless(Double::isNaN),
                format = classifyFormat(item, xmp),
            )
        }
    }

    suspend fun removeLocation(item: MediaItem) = withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(item.uri, "rw")
            ?: throw IOException("Unable to edit metadata")
        descriptor.use {
            val exif = ExifInterface(it.fileDescriptor)
            GPS_TAGS.forEach { tag -> exif.setAttribute(tag, null) }
            exif.saveAttributes()
        }
    }

    private fun classifyFormat(item: MediaItem, xmp: String?): MediaFormat {
        val mime = item.mimeType.lowercase()
        val name = item.displayName.lowercase()
        return when {
            mime.contains("svg") || name.endsWith(".svg") -> MediaFormat.SVG
            mime.contains("tiff") || name.endsWith(".tif") || name.endsWith(".tiff") -> MediaFormat.TIFF
            mime.contains("dng") || mime.contains("raw") || name.endsWith(".dng") || name.endsWith(".raw") -> MediaFormat.RAW
            xmp?.contains("MotionPhoto", true) == true || xmp?.contains("MicroVideo", true) == true -> MediaFormat.MOTION_PHOTO
            xmp?.contains("equirectangular", true) == true || xmp?.contains("ProjectionType", true) == true -> MediaFormat.MEDIA_360
            item.width > item.height * 2 -> MediaFormat.PANORAMA
            item.isVideo -> MediaFormat.VIDEO
            item.mediaType == com.pandagallery.app.domain.model.MediaType.GIF -> MediaFormat.GIF
            else -> MediaFormat.STANDARD_IMAGE
        }
    }

    companion object {
        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
        )

        val EXIF_TAGS_TO_PRESERVE = listOf(
            // Date & Time
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,

            // GPS & Location
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_MEASURE_MODE,

            // Camera & Lens Details
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SPECIFICATION,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,

            // Camera / Exposure Settings
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_APERTURE_VALUE,
            ExifInterface.TAG_BRIGHTNESS_VALUE,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_MAX_APERTURE_VALUE,
            ExifInterface.TAG_METERING_MODE,
            ExifInterface.TAG_LIGHT_SOURCE,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_COLOR_SPACE,
            ExifInterface.TAG_EXPOSURE_PROGRAM,
            ExifInterface.TAG_EXPOSURE_MODE,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
            ExifInterface.TAG_SCENE_CAPTURE_TYPE,
            ExifInterface.TAG_CONTRAST,
            ExifInterface.TAG_SATURATION,
            ExifInterface.TAG_SHARPNESS,
            ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
            ExifInterface.TAG_SUBJECT_DISTANCE,
            ExifInterface.TAG_SENSING_METHOD,
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
        )
    }
}

data class MediaMetadata(
    val mediaId: Long,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val aperture: Double? = null,
    val exposureTime: String? = null,
    val iso: Int? = null,
    val focalLength: Double? = null,
    val dateTimeOriginal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val format: MediaFormat = MediaFormat.STANDARD_IMAGE,
) {
    val hasLocation get() = latitude != null && longitude != null
}

enum class MediaFormat {
    STANDARD_IMAGE, VIDEO, GIF, RAW, SVG, TIFF, MOTION_PHOTO, PANORAMA, MEDIA_360,
}
