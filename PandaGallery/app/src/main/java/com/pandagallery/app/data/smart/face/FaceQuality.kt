package com.pandagallery.app.data.smart.face

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** A landmark position in source-image pixels. Kept free of android.graphics for testability. */
data class FacePoint(val x: Float, val y: Float)

/** Everything the quality gate and the aligner need about one detected face. */
data class FaceGeometry(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val headEulerY: Float,
    val headEulerZ: Float,
    val leftEye: FacePoint? = null,
    val rightEye: FacePoint? = null,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val minimumDimension: Int get() = minOf(width, height)
    val hasEyeLandmarks: Boolean get() = leftEye != null && rightEye != null
}

/**
 * Smallest face we will embed, in pixels of the decoded (downscaled) bitmap. Below this the
 * embedding is mostly upscaling artefacts and clusters start absorbing strangers.
 */
const val MIN_FACE_PIXELS = 56

/** Beyond this yaw the face is turning to profile and the embedding stops being comparable. */
const val MAX_YAW_DEGREES = 40f

/** Roll is correctable by rotation, so we tolerate more of it than yaw. */
const val MAX_ROLL_DEGREES = 45f

/** How much of the detected box may fall outside the frame before we treat it as cropped. */
private const val MIN_IN_FRAME_RATIO = 0.85f

/**
 * Whether this face is worth embedding at all.
 *
 * Filtering hard here is what separates usable people groups from noise: ML Kit will
 * happily report a 20px blurred face in the background of a crowd shot, and feeding those
 * to the clusterer is the single fastest way to merge two different people.
 */
fun isUsableFace(geometry: FaceGeometry): Boolean =
    geometry.minimumDimension >= MIN_FACE_PIXELS &&
        abs(geometry.headEulerY) <= MAX_YAW_DEGREES &&
        abs(geometry.headEulerZ) <= MAX_ROLL_DEGREES &&
        inFrameRatio(geometry) >= MIN_IN_FRAME_RATIO

/**
 * 0..1 confidence that this face is a good representative of the person. Drives seeding
 * order in [clusterFaces] and picks the cover face for a group.
 */
fun faceQuality(geometry: FaceGeometry): Float {
    if (!isUsableFace(geometry)) return 0f
    val size = (geometry.minimumDimension / 160f).coerceIn(0f, 1f)
    val yaw = 1f - (abs(geometry.headEulerY) / MAX_YAW_DEGREES) * 0.6f
    val roll = 1f - (abs(geometry.headEulerZ) / MAX_ROLL_DEGREES) * 0.3f
    val landmarks = if (geometry.hasEyeLandmarks) 1f else 0.75f
    return (size * yaw * roll * landmarks).coerceIn(0f, 1f)
}

/** Fraction of the detected box that actually lies inside the image. */
private fun inFrameRatio(geometry: FaceGeometry): Float {
    val area = geometry.width.toFloat() * geometry.height.toFloat()
    if (area <= 0f) return 0f
    val visibleWidth = (minOf(geometry.right, geometry.imageWidth) - maxOf(geometry.left, 0)).coerceAtLeast(0)
    val visibleHeight = (minOf(geometry.bottom, geometry.imageHeight) - maxOf(geometry.top, 0)).coerceAtLeast(0)
    return (visibleWidth.toFloat() * visibleHeight.toFloat()) / area
}

/**
 * The similarity transform that maps this face onto the model's canonical 112x112 input:
 * eyes level, a fixed inter-ocular distance, and the eye midpoint at a fixed height.
 *
 * Alignment matters more than model choice here — an unaligned crop costs more accuracy
 * than swapping between reasonable embedding models does.
 */
data class AlignmentTransform(
    val rotationDegrees: Float,
    val scale: Float,
    val sourceCenterX: Float,
    val sourceCenterY: Float,
)

/** Side length of the aligned crop handed to the embedding model. */
const val ALIGNED_FACE_SIZE = 112

/** Target eye separation inside the aligned crop, as a fraction of [ALIGNED_FACE_SIZE]. */
private const val TARGET_EYE_DISTANCE_RATIO = 0.34f

/**
 * Builds the alignment for [geometry], or null when eye landmarks are missing and the
 * caller must fall back to a plain padded box crop.
 */
fun alignmentFor(geometry: FaceGeometry): AlignmentTransform? {
    val left = geometry.leftEye ?: return null
    val right = geometry.rightEye ?: return null
    val dx = right.x - left.x
    val dy = right.y - left.y
    val eyeDistance = hypot(dx, dy)
    if (eyeDistance < 1f) return null
    return AlignmentTransform(
        rotationDegrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat(),
        scale = (ALIGNED_FACE_SIZE * TARGET_EYE_DISTANCE_RATIO) / eyeDistance,
        sourceCenterX = (left.x + right.x) / 2f,
        sourceCenterY = (left.y + right.y) / 2f,
    )
}

/** Where the eye midpoint lands inside the aligned crop. Slightly above centre, as faces are. */
const val ALIGNED_EYE_CENTER_Y = ALIGNED_FACE_SIZE * 0.42f
const val ALIGNED_EYE_CENTER_X = ALIGNED_FACE_SIZE / 2f
