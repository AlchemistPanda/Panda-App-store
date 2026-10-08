package com.pandagallery.app.domain.compression

import com.pandagallery.app.domain.model.ImageFormat
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.UserPreferences
import com.pandagallery.app.domain.model.formatFileSize
import kotlin.math.roundToLong

/**
 * Predicts how large a file will be after compression, before any work is done.
 *
 * This is a model, not a measurement. Real output depends on the content itself — a photo
 * of a blank wall and a photo of foliage at identical dimensions and quality can differ
 * threefold. Everything here is therefore expressed as an approximation with an explicit
 * range, and the UI is expected to present it as such ("~2 MB", not "2 MB").
 *
 * The model is calibrated against what [com.pandagallery.app.data.compression.CompressionEngine]
 * actually does: images are re-encoded at their decoded resolution (capped at [MAX_DECODE_EDGE]
 * on the long edge by the shared sample-size rule), and videos are scaled so their short edge
 * fits the preset and re-encoded with AAC audio.
 */
object CompressionEstimator {

    /** What the estimator needs to know about one file. Deliberately free of Android types. */
    data class Input(
        val sizeBytes: Long,
        val width: Int,
        val height: Int,
        val isVideo: Boolean,
        /** Milliseconds; only read for videos. */
        val durationMillis: Long? = null,
    )

    data class Estimate(
        val originalBytes: Long,
        val estimatedBytes: Long,
        /** Plausible spread, driven by how much image content varies. */
        val lowBytes: Long,
        val highBytes: Long,
        val itemCount: Int,
        /** True when this prediction was corrected by measurements from past jobs. */
        val isCalibrated: Boolean = false,
    ) {
        val savedBytes: Long get() = (originalBytes - estimatedBytes).coerceAtLeast(0)

        /** 0..100. Zero when there is nothing to save, so callers can hide the claim. */
        val savingsPercent: Int
            get() = if (originalBytes <= 0) 0 else ((savedBytes * 100.0) / originalBytes).roundToLong().toInt()

        /** Nothing meaningful to promise — hide the estimate rather than show "0 B → 0 B". */
        val isMeaningful: Boolean get() = itemCount > 0 && originalBytes > 0

        /** e.g. `"5.0 MB → ~2.1 MB"`. The tilde is deliberate; this is a prediction. */
        val summary: String
            get() = "${formatFileSize(originalBytes)} → ~${formatFileSize(estimatedBytes)}"

        /** e.g. `"5.0 MB → ~2.1 MB · saves ~58%"`, or the plain summary when it saves nothing. */
        val summaryWithSavings: String
            get() = if (savingsPercent <= 0) {
                "${formatFileSize(originalBytes)} → no meaningful saving"
            } else {
                "$summary · saves ~$savingsPercent%"
            }

        companion object {
            val Empty = Estimate(0, 0, 0, 0, 0, isCalibrated = false)
        }
    }

    /**
     * Bits per pixel for WebP lossy at a given encoder quality.
     * Anchors are interpolated linearly between; outside the range the ends are held.
     *
     * Measured, not assumed: twelve photos spanning a real library — WhatsApp images at 0.8–8 MP,
     * camera originals at 12–16 MP, and a screenshot — re-encoded with libwebp at each quality.
     * Median bits per pixel came out at 0.14 (q30), 0.21 (q50), 0.29 (q75) and 0.70 (q90); the
     * anchors below sit a little above those, so the prediction under-promises rather than over-.
     *
     * The previous anchors (0.65 / 1.05 / 1.90 / 3.20) were five to six times too high, which had
     * a visible consequence: for the already-compressed photos most libraries are full of, the
     * predicted output came out *larger* than the original, was clamped to it, and every preset
     * reported "no meaningful saving" — while the encoder was in fact saving 40–90%.
     */
    private val WEBP_BITS_PER_PIXEL = listOf(
        10 to 0.06,
        30 to 0.18,
        50 to 0.26,
        75 to 0.38,
        90 to 0.85,
        100 to 2.00,
    )

    /** Relative to WebP at the same quality setting. */
    private const val JPEG_VS_WEBP = 1.35
    private const val AVIF_VS_WEBP = 0.65

    /**
     * Bits per pixel per frame that Media3 asks the encoder for.
     *
     * Not a guess: `DefaultEncoderFactory.getSuggestedBitrate` is `width * height * frameRate *
     * 0.07 * 2.0`. Confirmed against the device — a 848x478 clip had the encoder configured at
     * 1,702,037 bps, against 848 * 478 * 30 * 0.14 = 1,702,445.
     *
     * The previous value of 0.07 missed Media3's doubling, and was then multiplied by a
     * per-codec factor on top, leaving the model about 3.2x optimistic for H.265. That is what
     * let the batch sheet promise "frees ~12.7 MB (60% reduction)" for a video the queue then
     * declined to replace at all, having produced nothing smaller.
     */
    private const val ENCODER_BITS_PER_PIXEL_PER_FRAME = 0.14

    /** Frame rate is not in MediaStore's common columns, so assume the usual capture rate. */
    private const val ASSUMED_FRAME_RATE = 30.0

    /** AAC stereo, matching the engine's audio MIME type. */
    private const val AUDIO_BITS_PER_SECOND = 128_000.0

    /**
     * What [com.pandagallery.app.data.compression.PerceptualQualityEstimator.recommendAdaptiveQuality]
     * settles on, averaged over a mixed library: it returns 78 for flat graphics, 82 for natural
     * scenes and 88 for portraits, and most libraries are mostly natural scenes.
     *
     * Deliberately at the low end of that range — Smart Auto aims for fidelity rather than size,
     * so its prediction should under-promise the saving rather than over-promise it.
     */
    private const val ASSUMED_ADAPTIVE_QUALITY = 80

    /** Spread applied around the point estimate to form the low/high bounds. */
    private const val CONTENT_VARIANCE = 0.30

    /**
     * Estimates the combined result of compressing [inputs] with [preferences].
     *
     * Files the settings would not shrink are counted at their current size rather than
     * inflated: the queue keeps whichever copy is smaller, so promising growth would be
     * both alarming and wrong.
     */
    fun estimate(
        inputs: List<Input>,
        preferences: UserPreferences,
        calibration: CompressionCalibration = CompressionCalibration.None,
    ): Estimate {
        if (inputs.isEmpty()) return Estimate.Empty
        var original = 0L
        var predicted = 0L
        inputs.forEach { input ->
            original += input.sizeBytes
            predicted += estimateOne(input, preferences, calibration)
        }
        return Estimate(
            originalBytes = original,
            estimatedBytes = predicted,
            lowBytes = (predicted * (1 - CONTENT_VARIANCE)).roundToLong(),
            highBytes = (predicted * (1 + CONTENT_VARIANCE)).coerceAtMost(original.toDouble()).roundToLong(),
            itemCount = inputs.size,
            isCalibrated = calibration.isCalibrated,
        )
    }

    /** Single-file convenience, used for the "typical photo" preview in settings. */
    fun estimate(
        input: Input,
        preferences: UserPreferences,
        calibration: CompressionCalibration = CompressionCalibration.None,
    ): Estimate = estimate(listOf(input), preferences, calibration)

    /** The raw model output for one file, before clamping. Used to score past predictions. */
    fun rawEstimate(input: Input, preferences: UserPreferences): Long =
        estimateOne(input, preferences, CompressionCalibration.None)

    private fun estimateOne(
        input: Input,
        preferences: UserPreferences,
        calibration: CompressionCalibration,
    ): Long {
        val modelled = if (input.isVideo) estimateVideo(input, preferences) else estimateImage(input, preferences)
        // Correction is applied before clamping, so a calibrated prediction can still be
        // capped at the original size like any other.
        val predicted = (modelled * calibration.factorFor(input.isVideo)).toLong()
        // Re-encoding an already-small file usually grows it; the queue would keep the
        // original, so the honest prediction is "no change".
        return predicted.coerceIn(MINIMUM_OUTPUT_BYTES, input.sizeBytes.coerceAtLeast(MINIMUM_OUTPUT_BYTES))
    }

    private fun estimateImage(input: Input, preferences: UserPreferences): Long {
        // True lossless WebP (see CompressionPreset.LOSSLESS / CompressionEngine) doesn't follow
        // the lossy bits-per-pixel curve below at all — its size tracks image detail so closely
        // that it is often close to, or larger than, the original. Promising savings here would
        // be the model applying a lossy anchor to an encoder path it was never measured against.
        if (preferences.imageFormat == ImageFormat.WEBP && preferences.imageQuality >= 100) {
            return input.sizeBytes
        }
        val pixels = decodedPixels(input.width, input.height)
        if (pixels <= 0) return input.sizeBytes
        val effectiveQuality =
            if (preferences.imageQuality < 0) ASSUMED_ADAPTIVE_QUALITY else preferences.imageQuality
        val bitsPerPixel = webpBitsPerPixel(effectiveQuality) * when (preferences.imageFormat) {
            ImageFormat.WEBP -> 1.0
            ImageFormat.JPEG -> JPEG_VS_WEBP
            ImageFormat.AVIF -> AVIF_VS_WEBP
        }
        return (pixels * bitsPerPixel / 8.0).roundToLong()
    }

    private fun estimateVideo(input: Input, preferences: UserPreferences): Long {
        val durationSeconds = (input.durationMillis ?: 0L) / 1000.0
        if (durationSeconds <= 0 || input.width <= 0 || input.height <= 0) return input.sizeBytes

        // Same rule as the engine (videoScaledDimensions): the setting caps the short edge and
        // never scales up, so a video already within it keeps its own dimensions. The short
        // edge does not depend on rotation, so MediaStore's coded size is enough here.
        val (targetWidth, targetHeight) = videoScaledDimensions(preferences.videoResolution, input.width, input.height)
            ?: (input.width to input.height)
        val targetPixels = targetWidth.toDouble() * targetHeight

        // Codec deliberately absent. Media3 derives the requested bitrate from dimensions and
        // frame rate alone, so H.265 and AV1 are handed the same budget as H.264 — they spend
        // it on looking better, not on being smaller. Modelling them as smaller predicted
        // savings the encoder was never asked to deliver.
        val videoBitsPerSecond = targetPixels * ASSUMED_FRAME_RATE * ENCODER_BITS_PER_PIXEL_PER_FRAME
        return ((videoBitsPerSecond + AUDIO_BITS_PER_SECOND) * durationSeconds / 8.0).roundToLong()
    }

    /** The pixels the engine actually encodes, via the same sample-size rule it decodes with. */
    internal fun decodedPixels(width: Int, height: Int): Long {
        if (width <= 0 || height <= 0) return 0
        val sample = decodeSampleSize(width, height)
        return (width / sample).toLong() * (height / sample).toLong()
    }

    /**
     * Interpolates *geometrically* between the measured anchors, not linearly.
     *
     * An encoder's size-against-quality curve is convex — each extra quality point costs
     * proportionally more bits than the last, which is why the anchors climb 0.26 → 0.38 → 0.85
     * rather than in even steps. Drawing a straight line between two anchors therefore sits
     * above the real curve everywhere in between, and the further apart the anchors the worse
     * it gets: across the 75→90 gap, linear interpolation put quality 80 at 0.54 bpp when the
     * curve through the same two measurements puts it at 0.50.
     *
     * That mattered most to the two presets that sit between anchors — Smart Auto (which the
     * engine resolves to roughly quality 80) and Near-Lossless (92) — whose predicted output was
     * inflated enough to report almost no saving for work that does save.
     *
     * Anchors themselves are returned exactly, so the measured points are unchanged.
     */
    internal fun webpBitsPerPixel(quality: Int): Double {
        val clamped = quality.coerceIn(1, 100)
        val upperIndex = WEBP_BITS_PER_PIXEL.indexOfFirst { it.first >= clamped }
        if (upperIndex <= 0) return WEBP_BITS_PER_PIXEL.first().second
        val (lowQuality, lowBpp) = WEBP_BITS_PER_PIXEL[upperIndex - 1]
        val (highQuality, highBpp) = WEBP_BITS_PER_PIXEL[upperIndex]
        val position = (clamped - lowQuality).toDouble() / (highQuality - lowQuality)
        // Every anchor is positive, so the log-space step is always defined.
        return lowBpp * Math.pow(highBpp / lowBpp, position)
    }

    /** Below this, a prediction is noise. Keeps a degenerate input from estimating zero. */
    private const val MINIMUM_OUTPUT_BYTES = 1_024L

    /**
     * The photo that best represents this library, for previewing a preset when no
     * selection exists.
     *
     * Median rather than mean: a handful of RAW files or long videos drag an average far
     * above anything the user actually owns, and a preview that claims "38 MB → ~9 MB" for
     * a library of phone snaps is worse than showing nothing.
     */
    fun representativeImage(media: List<MediaItem>): Input? {
        val images = media.asSequence()
            .filter { it.isImage && !it.isTrashed && it.size > 0 && it.width > 0 && it.height > 0 }
            .sortedBy(MediaItem::size)
            .toList()
        if (images.isEmpty()) return null
        val median = images[images.size / 2]
        return Input(
            sizeBytes = median.size,
            width = median.width,
            height = median.height,
            isVideo = false,
        )
    }
}

/** Adapts a [MediaItem] for estimation. */
fun MediaItem.toEstimatorInput(): CompressionEstimator.Input = CompressionEstimator.Input(
    sizeBytes = size,
    width = width,
    height = height,
    isVideo = isVideo,
    durationMillis = duration,
)
