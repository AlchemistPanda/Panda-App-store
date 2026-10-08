package com.pandagallery.app.data.compression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

/**
 * The quality score has to move when quality moves.
 *
 * Comparing at 256x256 averaged away the high-frequency artifacts lossy encoding produces: the
 * whole range from WebP q92 down to q10 spanned 0.0047 SSIM, all of it above the 0.98
 * INDISTINGUISHABLE threshold. The studio therefore reported "Zero visible loss" for a quality-1
 * encode whose preview is visibly destroyed. These tests pin the behaviour the metric needs —
 * degradation must register, and it must register proportionally.
 */
class PerceptualQualityDiscriminationTest {

    private val dimension = 256
    private val random = Random(7)

    /** Detailed content, the case the metric was blindest to. */
    private fun detailedImage(): IntArray = IntArray(dimension * dimension) { index ->
        val x = index % dimension
        val y = index / dimension
        val base = 128 + (40 * sin(x / 3.0)).toInt() + (30 * sin(y / 2.5)).toInt()
        val noisy = (base + random.nextInt(-35, 35)).coerceIn(0, 255)
        (0xFF shl 24) or (noisy shl 16) or (noisy shl 8) or noisy
    }

    /** Approximates encoder artifacts: detail is flattened toward each block's mean. */
    private fun blockAveraged(source: IntArray, strength: Double): IntArray {
        val out = source.copyOf()
        val block = 8
        for (by in 0 until dimension / block) {
            for (bx in 0 until dimension / block) {
                var sum = 0
                for (y in 0 until block) {
                    val row = (by * block + y) * dimension + bx * block
                    for (x in 0 until block) sum += source[row + x] and 0xFF
                }
                val mean = sum / (block * block)
                for (y in 0 until block) {
                    val row = (by * block + y) * dimension + bx * block
                    for (x in 0 until block) {
                        val original = source[row + x] and 0xFF
                        val flattened = (original + (mean - original) * strength).toInt().coerceIn(0, 255)
                        out[row + x] = (0xFF shl 24) or (flattened shl 16) or (flattened shl 8) or flattened
                    }
                }
            }
        }
        return out
    }

    @Test
    fun `an untouched copy scores a perfect match`() {
        val image = detailedImage()
        val score = PerceptualQualityEstimator.computeSsimFromPixels(image, image.copyOf(), dimension, dimension)

        assertEquals(1f, score.ssim, 1e-4f)
        assertEquals(PerceptualQualityEstimator.QualityGrade.INDISTINGUISHABLE, score.grade)
    }

    @Test
    fun `heavier degradation always scores lower than lighter degradation`() {
        val image = detailedImage()
        val scores = listOf(0.15, 0.4, 0.7, 1.0).map { strength ->
            PerceptualQualityEstimator
                .computeSsimFromPixels(image, blockAveraged(image, strength), dimension, dimension)
                .ssim
        }

        scores.zipWithNext { lighter, heavier ->
            assertTrue("SSIM must fall as degradation rises, got $scores", heavier < lighter)
        }
    }

    @Test
    fun `severe degradation is not reported as visually identical`() {
        val image = detailedImage()
        val wrecked = PerceptualQualityEstimator
            .computeSsimFromPixels(image, blockAveraged(image, 1.0), dimension, dimension)

        // The exact figure depends on content; what matters is that it leaves the grade that
        // tells the user there is "Zero visible loss".
        assertTrue("destroyed detail scored ${wrecked.ssim}", wrecked.ssim < 0.98f)
        assertTrue(wrecked.grade != PerceptualQualityEstimator.QualityGrade.INDISTINGUISHABLE)
    }

    @Test
    fun `the score spans a usable range rather than collapsing near one`() {
        val image = detailedImage()
        val best = PerceptualQualityEstimator
            .computeSsimFromPixels(image, blockAveraged(image, 0.1), dimension, dimension).ssim
        val worst = PerceptualQualityEstimator
            .computeSsimFromPixels(image, blockAveraged(image, 1.0), dimension, dimension).ssim

        // At the old comparison size a full quality sweep spanned under 0.005, which is what
        // pinned every result to one grade.
        assertTrue("range was only ${best - worst}", best - worst > 0.05f)
    }
}
