package com.pandagallery.app.data.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ObjectInpainterTest {

    data class TestPixel(val r: Int, val g: Int, val b: Int) {
        val luminance: Float get() = 0.299f * r + 0.587f * g + 0.114f * b
        val maxChan: Int get() = max(r, max(g, b))
        val minChan: Int get() = min(r, min(g, b))
        val chroma: Float get() = (maxChan - minChan).toFloat()
    }

    /**
     * Replicates shadow lifting algorithm calculation.
     */
    private fun simulateShadowLift(pixel: TestPixel, ambientLum: Float): TestPixel {
        val lum = pixel.luminance
        if (lum < ambientLum * 0.82f && ambientLum > 30f && lum > 10f) {
            val shadowDeficit = ((ambientLum - lum) / (ambientLum + 1e-4f)).coerceIn(0f, 1f)
            val lift = 1f + (shadowDeficit * 0.48f)
            return TestPixel(
                r = (pixel.r * lift).toInt().coerceIn(0, 255),
                g = (pixel.g * lift).toInt().coerceIn(0, 255),
                b = (pixel.b * lift).toInt().coerceIn(0, 255),
            )
        }
        return pixel
    }

    /**
     * Replicates specular reflection suppression algorithm.
     */
    private fun simulateReflectionSuppression(pixel: TestPixel): TestPixel {
        val lum = pixel.luminance
        val chroma = pixel.chroma

        if (lum > 150f && chroma < 42f) {
            val glareIntensity = ((lum - 150f) / 105f) * (1f - (chroma / 42f))
            val attenuation = 1f - (glareIntensity * 0.32f).coerceIn(0f, 0.45f)

            val adjustedR = ((pixel.r - 128f * (1f - attenuation)) * 1.08f).toInt().coerceIn(0, 255)
            val adjustedG = ((pixel.g - 128f * (1f - attenuation)) * 1.08f).toInt().coerceIn(0, 255)
            val adjustedB = ((pixel.b - 128f * (1f - attenuation)) * 1.08f).toInt().coerceIn(0, 255)

            return TestPixel(adjustedR, adjustedG, adjustedB)
        }
        return pixel
    }

    @Test
    fun `shadow lift elevates deep cast shadows toward ambient luminance`() {
        // Deep shadow on desk/document (lum ~ 50), ambient lighting is 160
        val shadowPixel = TestPixel(r = 50, g = 50, b = 50)
        val ambientLum = 160f

        val liftedPixel = simulateShadowLift(shadowPixel, ambientLum)

        assertTrue(
            "Lifted luminance should be greater than original shadow",
            liftedPixel.luminance > shadowPixel.luminance,
        )
        // Verify lift is bounded and does not overshoot ambient
        assertTrue(
            "Lifted luminance should stay below ambient",
            liftedPixel.luminance <= ambientLum,
        )
    }

    @Test
    fun `shadow lift ignores unshadowed pixels with high luminance`() {
        val brightPixel = TestPixel(r = 180, g = 180, b = 180)
        val ambientLum = 160f

        val resultPixel = simulateShadowLift(brightPixel, ambientLum)

        assertEquals("Unshadowed pixel should remain unchanged", brightPixel, resultPixel)
    }

    @Test
    fun `shadow lift ignores natural pitch black pixels like text or pupils`() {
        val blackPixel = TestPixel(r = 5, g = 5, b = 5)
        val ambientLum = 160f

        val resultPixel = simulateShadowLift(blackPixel, ambientLum)

        assertEquals("Natural deep blacks (lum < 10) must be preserved", blackPixel, resultPixel)
    }

    @Test
    fun `reflection eraser attenuates desaturated specular glass glare`() {
        // Glass specular reflection: high luminance, near-zero chroma (milky white glare)
        val glarePixel = TestPixel(r = 230, g = 230, b = 230)

        val recoveredPixel = simulateReflectionSuppression(glarePixel)

        assertTrue(
            "Glare highlight should be attenuated to suppress milky reflection",
            recoveredPixel.luminance < glarePixel.luminance,
        )
    }

    @Test
    fun `reflection eraser preserves saturated vibrant highlights`() {
        // Vibrant yellow flower or neon sign (high luminance, high chroma)
        val vibrantPixel = TestPixel(r = 240, g = 220, b = 20) // chroma > 200

        val resultPixel = simulateReflectionSuppression(vibrantPixel)

        assertEquals("High-chroma vibrant objects should not be mistaken for glass glare", vibrantPixel, resultPixel)
    }
}
