package com.pandagallery.app.domain.model

import android.net.Uri

/**
 * Playback speed options specifically for Samsung One UI Instant Slow-Mo.
 */
enum class SlowMoSpeed(val multiplier: Float, val label: String) {
    QUARTER(0.25f, "0.25x (Super Slow-Mo)"),
    HALF(0.5f, "0.5x (Smooth Slow-Mo)"),
    THREE_QUARTERS(0.75f, "0.75x"),
    NORMAL(1.0f, "1.0x (Normal)");

    companion object {
        fun fromMultiplier(multiplier: Float): SlowMoSpeed {
            return entries.minByOrNull { kotlin.math.abs(it.multiplier - multiplier) } ?: NORMAL
        }
    }
}

/**
 * Represents the live interactive state during touch-and-hold Instant Slow-Mo.
 */
data class SlowMoSessionState(
    val isActive: Boolean = false,
    val speedMultiplier: Float = 0.25f,
    val originalSpeed: Float = 1.0f,
    val wasPlayingBeforeHold: Boolean = true,
    val startPositionMs: Long = 0L,
)

/**
 * Configuration for exporting a slowed down video clip.
 */
data class SlowMoExportConfig(
    val speedMultiplier: Float = 0.25f,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val compressToVault: Boolean = false,
    val muteAudio: Boolean = false,
    val keepOriginalSpeedOutsideTrim: Boolean = false,
) {
    /**
     * Computes the calculated output duration in milliseconds based on speed multiplier.
     */
    fun calculateOutputDurationMs(originalDurationMs: Long): Long {
        val effectiveRange = if (trimEndMs > trimStartMs) {
            trimEndMs - trimStartMs
        } else {
            originalDurationMs
        }
        val safeMultiplier = speedMultiplier.coerceAtLeast(0.05f)
        return (effectiveRange / safeMultiplier).toLong()
    }
}

/**
 * Result returned after saving or exporting a slow-mo clip.
 */
data class SlowMoExportResult(
    val isSuccess: Boolean,
    val destinationUri: Uri? = null,
    val destinationPath: String? = null,
    val originalDurationMs: Long = 0L,
    val outputDurationMs: Long = 0L,
    val savedToVault: Boolean = false,
    val bytesWritten: Long = 0L,
    val errorMessage: String? = null,
)
