package com.pandagallery.app.ui.components

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.roundToInt

/**
 * Crops a photo down to one face, as a square around the stored box.
 *
 * Applied at load time rather than by scaling the whole image behind a circular clip: a
 * clip would still decode and hold the entire photo for every chip on screen, and a person
 * group with a dozen members would be loading a dozen full images to show a dozen thumbnails.
 *
 * Coordinates are fractions of the image, so this works regardless of what resolution the
 * loader decided to decode.
 */
class FaceCropTransformation(
    private val left: Float,
    private val top: Float,
    private val right: Float,
    private val bottom: Float,
) : Transformation() {

    override val cacheKey: String = "face-crop:$left:$top:$right:$bottom"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        if (width <= 0 || height <= 0) return input

        val centerX = ((left + right) / 2f) * width
        val centerY = ((top + bottom) / 2f) * height
        val boxWidth = (right - left) * width
        val boxHeight = (bottom - top) * height
        if (boxWidth <= 0f || boxHeight <= 0f) return input

        // A detector's box is tight on the face; widening it includes hair and chin, which
        // is what makes a crop read as a portrait rather than a cut-out of features.
        val side = (maxOf(boxWidth, boxHeight) * HEAD_ROOM)
            .coerceAtMost(minOf(width, height).toFloat())
        val half = side / 2f

        // Keep the square inside the image by sliding it rather than shrinking it, so faces
        // near an edge stay the same size as everyone else's.
        val leftPixel = (centerX - half).coerceIn(0f, width - side).roundToInt()
        val topPixel = (centerY - half).coerceIn(0f, height - side).roundToInt()
        val sidePixels = side.roundToInt().coerceAtLeast(1)

        return Bitmap.createBitmap(
            input,
            leftPixel.coerceIn(0, (width - sidePixels).coerceAtLeast(0)),
            topPixel.coerceIn(0, (height - sidePixels).coerceAtLeast(0)),
            sidePixels.coerceAtMost(width),
            sidePixels.coerceAtMost(height),
        )
    }

    override fun equals(other: Any?): Boolean = other is FaceCropTransformation &&
        left == other.left && top == other.top && right == other.right && bottom == other.bottom

    override fun hashCode(): Int = cacheKey.hashCode()

    private companion object {
        /** How much wider than the detected box the crop is taken. */
        const val HEAD_ROOM = 1.6f
    }
}
