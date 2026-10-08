package com.pandagallery.app.data.smart.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect

/**
 * Turns a detected face into the canonical square crop the embedding model expects.
 *
 * When eye landmarks are available the crop is a similarity transform — rotated so the eyes
 * are level and scaled to a fixed inter-ocular distance. That is what lets the same person
 * photographed at a tilt still land near their own cluster.
 */
object FaceCrops {

    /** Extra context kept around the box when we have to fall back to a plain crop. */
    private const val FALLBACK_MARGIN = 0.25f

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /**
     * @return a [ALIGNED_FACE_SIZE] square crop, or null when the face lies outside the
     *   bitmap entirely. Caller owns the returned bitmap and must recycle it.
     */
    fun alignedCrop(source: Bitmap, geometry: FaceGeometry): Bitmap? {
        val alignment = alignmentFor(geometry)
        return if (alignment != null) {
            similarityCrop(source, alignment)
        } else {
            paddedBoxCrop(source, geometry)
        }
    }

    private fun similarityCrop(source: Bitmap, alignment: AlignmentTransform): Bitmap {
        val output = Bitmap.createBitmap(ALIGNED_FACE_SIZE, ALIGNED_FACE_SIZE, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            // Move the eye midpoint to the origin, level the eyes, normalize the scale,
            // then drop it at the canonical eye position inside the output square.
            postTranslate(-alignment.sourceCenterX, -alignment.sourceCenterY)
            postRotate(-alignment.rotationDegrees)
            postScale(alignment.scale, alignment.scale)
            postTranslate(ALIGNED_EYE_CENTER_X, ALIGNED_EYE_CENTER_Y)
        }
        Canvas(output).drawBitmap(source, matrix, paint)
        return output
    }

    private fun paddedBoxCrop(source: Bitmap, geometry: FaceGeometry): Bitmap? {
        val marginX = (geometry.width * FALLBACK_MARGIN).toInt()
        val marginY = (geometry.height * FALLBACK_MARGIN).toInt()
        val bounds = Rect(
            (geometry.left - marginX).coerceIn(0, source.width),
            (geometry.top - marginY).coerceIn(0, source.height),
            (geometry.right + marginX).coerceIn(0, source.width),
            (geometry.bottom + marginY).coerceIn(0, source.height),
        )
        if (bounds.width() < 8 || bounds.height() < 8) return null
        val output = Bitmap.createBitmap(ALIGNED_FACE_SIZE, ALIGNED_FACE_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            source,
            bounds,
            Rect(0, 0, ALIGNED_FACE_SIZE, ALIGNED_FACE_SIZE),
            paint,
        )
        return output
    }
}
