package com.pandagallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to register and track supported RAW, HEIC, and AVIF image formats,
 * along with hardware acceleration flags and decoding strategies.
 */
@Entity(tableName = "raw_format_support")
data class RawFormatSupportEntity(
    @PrimaryKey
    val extension: String, // "dng", "cr2", "nef", "arw", "heic", "heif", "avif"
    val mimeType: String,
    val isHardwareAccelerated: Boolean = true,
    val supportsThumbnailExtraction: Boolean = true,
    val fallbackCodec: String = "SOFTWARE_EXIF"
)
