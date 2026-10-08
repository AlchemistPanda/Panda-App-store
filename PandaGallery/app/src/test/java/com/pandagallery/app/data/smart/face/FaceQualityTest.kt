package com.pandagallery.app.data.smart.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceQualityTest {

    @Test
    fun `a large front-facing face is usable`() {
        assertTrue(isUsableFace(geometry()))
    }

    @Test
    fun `a tiny background face is rejected`() {
        assertFalse(isUsableFace(geometry(size = MIN_FACE_PIXELS - 1)))
    }

    @Test
    fun `a profile shot is rejected`() {
        assertFalse(isUsableFace(geometry(yaw = MAX_YAW_DEGREES + 5f)))
    }

    @Test
    fun `a face cropped by the frame edge is rejected`() {
        // Half of the box sits off the left edge.
        assertFalse(isUsableFace(geometry(size = 200, left = -100, top = 10)))
    }

    @Test
    fun `quality prefers larger and straighter faces`() {
        val big = faceQuality(geometry(size = 200))
        val small = faceQuality(geometry(size = 80))
        val turned = faceQuality(geometry(size = 200, yaw = 30f))

        assertTrue("larger face should score higher", big > small)
        assertTrue("straighter face should score higher", big > turned)
    }

    @Test
    fun `unusable faces score zero`() {
        assertEquals(0f, faceQuality(geometry(size = 10)), 0f)
    }

    @Test
    fun `alignment levels the eyes and normalizes scale`() {
        val alignment = alignmentFor(
            geometry(
                leftEye = FacePoint(100f, 100f),
                rightEye = FacePoint(140f, 140f),
            )
        )

        assertNotNull(alignment)
        // Eyes on a 45-degree diagonal, so the crop must rotate by 45 degrees to level them.
        assertEquals(45f, alignment!!.rotationDegrees, 0.01f)
        assertEquals(120f, alignment.sourceCenterX, 0.01f)
        assertEquals(120f, alignment.sourceCenterY, 0.01f)
        assertTrue(alignment.scale > 0f)
    }

    @Test
    fun `alignment is unavailable without eye landmarks`() {
        assertNull(alignmentFor(geometry(leftEye = null, rightEye = null)))
    }

    private fun geometry(
        size: Int = 120,
        left: Int = 40,
        top: Int = 40,
        yaw: Float = 0f,
        roll: Float = 0f,
        leftEye: FacePoint? = FacePoint(60f, 70f),
        rightEye: FacePoint? = FacePoint(100f, 70f),
    ) = FaceGeometry(
        left = left,
        top = top,
        right = left + size,
        bottom = top + size,
        imageWidth = 1280,
        imageHeight = 960,
        headEulerY = yaw,
        headEulerZ = roll,
        leftEye = leftEye,
        rightEye = rightEye,
    )
}
