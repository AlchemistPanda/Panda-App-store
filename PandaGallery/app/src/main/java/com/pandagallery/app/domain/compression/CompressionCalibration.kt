package com.pandagallery.app.domain.compression

/** One finished compression, scored against what was predicted for it beforehand. */
data class CalibrationSample(
    val isVideo: Boolean,
    val predictedBytes: Long,
    val actualBytes: Long,
)

/**
 * Multipliers that correct the estimator toward what this device and this library actually
 * produce.
 *
 * The estimator models bits per pixel from published averages. Real output depends on the
 * encoder implementation, the camera, and how detailed the user's photos happen to be — so
 * the model is right in shape and only approximately right in scale. Measuring the error
 * against completed jobs and folding it back is what turns a generic prediction into one
 * calibrated to the person using it.
 */
data class CompressionCalibration(
    val imageFactor: Double = 1.0,
    val videoFactor: Double = 1.0,
    val imageSamples: Int = 0,
    val videoSamples: Int = 0,
) {
    fun factorFor(isVideo: Boolean): Double = if (isVideo) videoFactor else imageFactor

    /** True once either media kind has enough history to have moved off the defaults. */
    val isCalibrated: Boolean
        get() = imageSamples >= MINIMUM_SAMPLES || videoSamples >= MINIMUM_SAMPLES

    companion object {
        val None = CompressionCalibration()

        /**
         * Below this, one unusual photo would swing every estimate on screen. Five is
         * enough for the median to be stable without making the feature feel dead.
         */
        const val MINIMUM_SAMPLES = 5

        /**
         * Corrections are capped. A factor outside this range means the model is wrong in
         * a way a multiplier cannot fix, and letting it run would produce estimates more
         * misleading than the uncorrected ones.
         */
        const val MIN_FACTOR = 0.4
        const val MAX_FACTOR = 2.5
    }
}

/**
 * Derives correction factors from finished work.
 *
 * Uses the median ratio rather than the mean: a single already-compressed file that barely
 * shrank produces a huge outlier ratio, and one of those would otherwise drag every future
 * estimate with it.
 */
fun calibrationFrom(samples: List<CalibrationSample>): CompressionCalibration {
    val usable = samples.filter { it.predictedBytes > 0 && it.actualBytes > 0 }
    val images = usable.filterNot(CalibrationSample::isVideo)
    val videos = usable.filter(CalibrationSample::isVideo)
    return CompressionCalibration(
        imageFactor = factorFrom(images),
        videoFactor = factorFrom(videos),
        imageSamples = images.size,
        videoSamples = videos.size,
    )
}

private fun factorFrom(samples: List<CalibrationSample>): Double {
    if (samples.size < CompressionCalibration.MINIMUM_SAMPLES) return 1.0
    val ratios = samples
        .map { it.actualBytes.toDouble() / it.predictedBytes.toDouble() }
        .sorted()
    val middle = ratios.size / 2
    val median = if (ratios.size % 2 == 1) {
        ratios[middle]
    } else {
        (ratios[middle - 1] + ratios[middle]) / 2.0
    }
    return median.coerceIn(CompressionCalibration.MIN_FACTOR, CompressionCalibration.MAX_FACTOR)
}
