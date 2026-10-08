package com.pandagallery.app.domain.compression

import com.pandagallery.app.domain.model.VideoResolution
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Sizing rules shared by [com.pandagallery.app.data.compression.CompressionEngine] and
 * [CompressionEstimator]. They live in one place so the prediction can never drift from what
 * the engine actually does — the estimator used to assume a 4096 px decode while the engine
 * picked 2560, 4096 or 6144 from whatever RAM happened to be free at that moment.
 */

/**
 * The long-edge ceiling for a full-resolution image decode.
 *
 * Fixed rather than derived from free RAM: a cap that followed `availMem` gave photos in the
 * same batch different resolutions depending on what else was running, and quietly published
 * a 2560 px copy of a 50 MP original whenever the system was briefly short of memory. One
 * 6144 px ARGB decode is ~113 MB of native memory, and the engine's decode gate allows only one
 * at a time on ordinary phones. A decode that still runs out of memory fails the task (the
 * original is kept) instead of retrying smaller.
 */
const val MAX_DECODE_EDGE = 6144

/** Power-of-two sample size that brings both edges within [maxEdge], as the decoders apply it. */
fun decodeSampleSize(width: Int, height: Int, maxEdge: Int = MAX_DECODE_EDGE): Int {
    var sample = 1
    while (width / sample > maxEdge || height / sample > maxEdge) sample *= 2
    return sample
}

/**
 * Encoded size grows close to linearly with pixel count — slightly less, because larger images
 * have more smooth area per edge. Scaling by sqrt(ratio), as the previews did, understated a
 * full-size result two to three times and overstated the saving by the same margin.
 */
private const val PREVIEW_SIZE_EXPONENT = 0.9

/**
 * How many times bigger the real job's output will be than a preview encode of the same image
 * at [previewWidth]x[previewHeight]. The job decodes at [MAX_DECODE_EDGE], not at the source's
 * full size, so that is the resolution the estimate extrapolates to.
 */
fun previewByteScale(sourceWidth: Int, sourceHeight: Int, previewWidth: Int, previewHeight: Int): Double {
    if (sourceWidth <= 0 || sourceHeight <= 0 || previewWidth <= 0 || previewHeight <= 0) return 1.0
    val sample = decodeSampleSize(sourceWidth, sourceHeight)
    val jobPixels = (sourceWidth / sample).toDouble() * (sourceHeight / sample)
    val previewPixels = previewWidth.toDouble() * previewHeight
    if (jobPixels <= previewPixels) return 1.0
    return (jobPixels / previewPixels).pow(PREVIEW_SIZE_EXPONENT)
}

/** The encode a budget search settled on. It fits only if [bytes] is no larger than the budget. */
class BudgetedEncode(val quality: Int, val bytes: ByteArray)

/**
 * Binary-searches encoder quality for the best result no larger than [budgetBytes].
 *
 * Runs until the range is exhausted (seven encodes over 15..95), so the lowest quality is
 * actually reachable. When nothing fits, the *smallest* attempt is returned — keeping the first
 * overshoot, as this did before, wrote the q55 encode, often several times the budget, while a
 * q19 encode that came much closer was already in memory.
 *
 * Inline so [encode] can check for cancellation between attempts.
 */
inline fun encodeWithinBudget(
    budgetBytes: Long,
    minQuality: Int = 15,
    maxQuality: Int = 95,
    onAttempt: (attempt: Int) -> Unit = {},
    encode: (quality: Int) -> ByteArray,
): BudgetedEncode {
    var low = minQuality
    var high = maxQuality
    var bestFit: BudgetedEncode? = null
    var smallest: BudgetedEncode? = null
    var attempt = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        val bytes = encode(mid)
        onAttempt(++attempt)
        if (bytes.size <= budgetBytes) {
            bestFit = BudgetedEncode(mid, bytes)
            low = mid + 1
        } else {
            if (smallest == null || bytes.size < smallest.bytes.size) {
                smallest = BudgetedEncode(mid, bytes)
            }
            high = mid - 1
        }
    }
    return bestFit ?: smallest ?: error("Empty quality range $minQuality..$maxQuality")
}

/**
 * The displayed size to scale a video to, or null to leave it alone.
 *
 * A resolution setting caps the *short* edge — "1080p" means 1080 lines whichever way the phone
 * was held. Capping the displayed height instead, as this did before, turned a 1080x1920
 * portrait clip into 608x1080 under the 1080p setting while a landscape clip of the same pixel
 * count was left untouched.
 *
 * The setting is a ceiling, never a target: anything at or below it is not scaled, since
 * scaling up only makes the file bigger. Both edges come out even, which encoders require.
 */
fun videoScaledDimensions(resolution: VideoResolution, width: Int, height: Int): Pair<Int, Int>? {
    if (resolution == VideoResolution.ORIGINAL || width <= 0 || height <= 0) return null
    val shortEdge = minOf(width, height)
    if (shortEdge <= resolution.height) return null
    val scale = resolution.height.toDouble() / shortEdge
    fun even(edge: Double) = ((edge / 2.0).roundToLong() * 2).toInt().coerceAtLeast(2)
    return even(width * scale) to even(height * scale)
}
