package com.pandagallery.app.domain.model

import android.graphics.Bitmap

/**
 * Configuration parameters for Samsung One UI AI Photo Assist & Generative Edit.
 */
data class GenerativeEditConfig(
    val rotationDegrees: Float = 0f,    // -45° to +45° horizon leveling
    val verticalTilt: Float = 0f,       // -30° to +30° keystone pitch tilt
    val horizontalTilt: Float = 0f,     // -30° to +30° keystone yaw tilt
    val expandLeftPct: Float = 0f,      // 0.0 to 0.5 canvas expand
    val expandTopPct: Float = 0f,       // 0.0 to 0.5 canvas expand
    val expandRightPct: Float = 0f,     // 0.0 to 0.5 canvas expand
    val expandBottomPct: Float = 0f,    // 0.0 to 0.5 canvas expand
) {
    val hasModifications: Boolean
        get() = rotationDegrees != 0f ||
                verticalTilt != 0f ||
                horizontalTilt != 0f ||
                expandLeftPct > 0f ||
                expandTopPct > 0f ||
                expandRightPct > 0f ||
                expandBottomPct > 0f
}

/**
 * Active editing mode tab for the generative editor.
 */
enum class GenerativeEditMode {
    STRAIGHTEN,
    PERSPECTIVE,
    EXPAND,
}

/**
 * Result payload containing the generated bitmap and performance statistics.
 */
data class GenerativeEditResult(
    val outputBitmap: Bitmap,
    val durationMs: Long,
    val filledPixelsCount: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
)
