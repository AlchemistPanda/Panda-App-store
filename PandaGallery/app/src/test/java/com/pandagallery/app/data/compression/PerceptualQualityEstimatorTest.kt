package com.pandagallery.app.data.compression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualQualityEstimatorTest {

    @Test
    fun `identical pixels produce 100 percent visual match and INDISTINGUISHABLE grade`() {
        val width = 64
        val height = 64
        val total = width * height
        val orig = IntArray(total) { (it % 255) or ((it * 2 % 255) shl 8) or ((it * 3 % 255) shl 16) }
        val comp = orig.clone()

        val score = PerceptualQualityEstimator.computeSsimFromPixels(orig, comp, width, height)

        assertTrue("SSIM should be 1.0 for identical pixels, was ${score.ssim}", score.ssim >= 0.999f)
        assertEquals(100f, score.matchPercentage, 0.1f)
        assertEquals(PerceptualQualityEstimator.QualityGrade.INDISTINGUISHABLE, score.grade)
    }

    @Test
    fun `noisy compressed pixels produce lower SSIM and valid percentage`() {
        val width = 64
        val height = 64
        val total = width * height
        val orig = IntArray(total) { 0xFF7F7F7F.toInt() }
        // Introduce random/alternate high contrast noise in comp
        val comp = IntArray(total) { if (it % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }

        val score = PerceptualQualityEstimator.computeSsimFromPixels(orig, comp, width, height)

        assertTrue("Degraded pixels should produce lower SSIM", score.ssim < 0.90f)
        assertTrue("Percentage should be within 0..100", score.matchPercentage in 0f..100f)
    }

    @Test
    fun `subtle luma variation yields high fidelity score`() {
        val width = 64
        val height = 64
        val total = width * height
        // Gradient image
        val orig = IntArray(total) { i ->
            val v = (i % 256)
            0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
        }
        // Minimal quantization noise (+1 / -1)
        val comp = IntArray(total) { i ->
            val v = ((i % 256) + (if (i % 2 == 0) 1 else -1)).coerceIn(0, 255)
            0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
        }

        val score = PerceptualQualityEstimator.computeSsimFromPixels(orig, comp, width, height)

        assertTrue("Subtle quantization should maintain high fidelity (>= 0.98), was ${score.ssim}", score.ssim >= 0.98f)
        assertEquals(PerceptualQualityEstimator.QualityGrade.INDISTINGUISHABLE, score.grade)
    }

    @Test
    fun `recommendAdaptiveQuality returns optimal quality targets for content types`() {
        val flatQuality = PerceptualQualityEstimator.recommendAdaptiveQuality(
            PerceptualQualityEstimator.ContentComplexity.FLAT_GRAPHIC
        )
        val portraitQuality = PerceptualQualityEstimator.recommendAdaptiveQuality(
            PerceptualQualityEstimator.ContentComplexity.PORTRAIT
        )
        val sceneQuality = PerceptualQualityEstimator.recommendAdaptiveQuality(
            PerceptualQualityEstimator.ContentComplexity.NATURAL_SCENE
        )

        assertTrue("Flat graphics should recommend high compression ratio", flatQuality in 70..80)
        assertTrue("Portraits should recommend high fidelity to preserve facial details", portraitQuality in 85..95)
        assertTrue("Natural scenes should recommend balanced quality", sceneQuality in 80..85)
        assertTrue("Portrait quality should exceed flat graphic quality", portraitQuality > flatQuality)
    }
}
