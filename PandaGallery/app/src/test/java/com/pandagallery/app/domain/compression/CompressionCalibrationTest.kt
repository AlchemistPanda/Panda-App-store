package com.pandagallery.app.domain.compression

import com.pandagallery.app.domain.model.CompressionPreset
import com.pandagallery.app.domain.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionCalibrationTest {

    private fun samples(count: Int, isVideo: Boolean, ratio: Double) = List(count) {
        CalibrationSample(isVideo = isVideo, predictedBytes = 1_000_000, actualBytes = (1_000_000 * ratio).toLong())
    }

    @Test
    fun `too little history leaves estimates uncorrected`() {
        val calibration = calibrationFrom(samples(CompressionCalibration.MINIMUM_SAMPLES - 1, false, 2.0))

        assertEquals(1.0, calibration.imageFactor, 0.0001)
        assertFalse(calibration.isCalibrated)
    }

    @Test
    fun `consistent under-prediction produces a corrective factor above one`() {
        val calibration = calibrationFrom(samples(10, isVideo = false, ratio = 1.6))

        assertEquals(1.6, calibration.imageFactor, 0.01)
        assertTrue(calibration.isCalibrated)
    }

    @Test
    fun `images and videos calibrate independently`() {
        val calibration = calibrationFrom(
            samples(8, isVideo = false, ratio = 1.2) + samples(8, isVideo = true, ratio = 0.7)
        )

        assertEquals(1.2, calibration.imageFactor, 0.01)
        assertEquals(0.7, calibration.videoFactor, 0.01)
    }

    /**
     * The reason this uses a median. One already-compressed file that barely shrank has a
     * huge actual/predicted ratio, and averaging would let it distort every later estimate.
     */
    @Test
    fun `a single wild outlier does not move the factor`() {
        val calibration = calibrationFrom(
            samples(10, isVideo = false, ratio = 1.1) +
                listOf(CalibrationSample(false, predictedBytes = 1_000, actualBytes = 900_000))
        )

        assertEquals(1.1, calibration.imageFactor, 0.05)
    }

    @Test
    fun `corrections are capped so a broken model cannot run away`() {
        val tooHigh = calibrationFrom(samples(10, isVideo = false, ratio = 50.0))
        val tooLow = calibrationFrom(samples(10, isVideo = false, ratio = 0.001))

        assertEquals(CompressionCalibration.MAX_FACTOR, tooHigh.imageFactor, 0.0001)
        assertEquals(CompressionCalibration.MIN_FACTOR, tooLow.imageFactor, 0.0001)
    }

    @Test
    fun `samples with missing numbers are ignored`() {
        val calibration = calibrationFrom(
            samples(6, isVideo = false, ratio = 1.5) +
                List(20) { CalibrationSample(false, predictedBytes = 0, actualBytes = 0) }
        )

        assertEquals(6, calibration.imageSamples)
        assertEquals(1.5, calibration.imageFactor, 0.01)
    }

    // ------------------------------------------------------------------ applied to estimates

    private val photo = CompressionEstimator.Input(
        sizeBytes = 5L * 1024 * 1024,
        width = 4032,
        height = 3024,
        isVideo = false,
    )

    private val preferences = UserPreferences(
        imageFormat = CompressionPreset.MEDIUM.imageFormat,
        imageQuality = CompressionPreset.MEDIUM.imageQuality,
    )

    @Test
    fun `calibration scales the prediction`() {
        val uncorrected = CompressionEstimator.estimate(photo, preferences)
        val corrected = CompressionEstimator.estimate(
            photo,
            preferences,
            calibrationFrom(samples(10, isVideo = false, ratio = 1.5)),
        )

        assertTrue(corrected.estimatedBytes > uncorrected.estimatedBytes)
        assertTrue(corrected.isCalibrated)
        assertFalse(uncorrected.isCalibrated)
    }

    @Test
    fun `a calibrated prediction is still capped at the original size`() {
        // A 12MP frame already squeezed to 300 KB: the model predicts less than that, but the
        // maximum correction pushes the prediction past the source, where the clamp takes over.
        // (The size is well under the old 2 MB now that the bits-per-pixel curve reflects what
        // libwebp actually emits — see CompressionEstimator.WEBP_BITS_PER_PIXEL.)
        val alreadySmall = photo.copy(sizeBytes = 300L * 1024)
        val corrected = CompressionEstimator.estimate(
            alreadySmall,
            preferences,
            calibrationFrom(samples(10, isVideo = false, ratio = 10.0)),
        )

        assertEquals(alreadySmall.sizeBytes, corrected.estimatedBytes)
        assertEquals(0, corrected.savingsPercent)
    }

    @Test
    fun `the raw estimate ignores calibration so scoring stays honest`() {
        // Predictions are stored raw; if calibration were baked in, each round would
        // correct an already-corrected number and the factor would compound.
        val raw = CompressionEstimator.rawEstimate(photo, preferences)
        val uncalibrated = CompressionEstimator.estimate(photo, preferences).estimatedBytes

        assertEquals(uncalibrated, raw)
    }
}
