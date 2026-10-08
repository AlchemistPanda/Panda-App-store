package com.pandagallery.app.data.smart

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Recognized word item with its native image-coordinate bounding box.
 */
data class RecognizedTextWord(
    val text: String,
    val boundingBox: Rect?,
)

/**
 * Recognized text line containing words and line-level bounding box.
 */
data class RecognizedTextLine(
    val text: String,
    val boundingBox: Rect?,
    val words: List<RecognizedTextWord> = emptyList(),
)

/**
 * Recognized block of text (paragraph/section) containing lines and block bounding box.
 */
data class RecognizedTextBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<RecognizedTextLine> = emptyList(),
)

/**
 * Complete document-level recognition result with full text and dimensions.
 */
data class RecognizedTextResult(
    val fullText: String,
    val blocks: List<RecognizedTextBlock> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
) {
    val isEmpty: Boolean get() = fullText.isBlank() || blocks.isEmpty()
    val isNotEmpty: Boolean get() = !isEmpty

    /** Flat list of all recognized lines for quick spatial hit-testing. */
    val allLines: List<RecognizedTextLine> by lazy {
        blocks.flatMap { it.lines }
    }
}

/**
 * Supported contextual smart actions detected from text semantics.
 */
enum class SmartActionType {
    CALL,
    URL,
    EMAIL,
    MAP,
}

/**
 * Actionable smart pill button representation.
 */
data class SmartAction(
    val label: String,
    val actionType: SmartActionType,
    val targetUri: String,
)

/**
 * Classifies selected text to detect Phone numbers, URLs, and Email addresses
 * for One UI 6.1 contextual smart action chips.
 */
object SmartActionClassifier {
    private val PHONE_PATTERN = Pattern.compile(
        "(?:\\+?\\d{1,4}[\\s-]?)?(?:\\(?\\d{2,4}\\)?[\\s-]?)?\\d{3,4}[\\s-]?[\\d]{3,5}"
    )
    private val URL_PATTERN = Pattern.compile(
        "(?i)\\b(?:https?://|www\\.)[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+(?:/[^\\s]*)?"
    )
    private val EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"
    )

    fun classify(text: String): List<SmartAction> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val actions = mutableListOf<SmartAction>()

        // 1. URL Check
        val urlMatcher = URL_PATTERN.matcher(trimmed)
        if (urlMatcher.find()) {
            val rawUrl = urlMatcher.group(0) ?: ""
            val fullUrl = if (rawUrl.startsWith("http://", ignoreCase = true) ||
                rawUrl.startsWith("https://", ignoreCase = true)
            ) {
                rawUrl
            } else {
                "https://$rawUrl"
            }
            actions.add(SmartAction(label = "Open link", actionType = SmartActionType.URL, targetUri = fullUrl))
        }

        // 2. Email Check
        val emailMatcher = EMAIL_PATTERN.matcher(trimmed)
        if (emailMatcher.find()) {
            val email = emailMatcher.group(0) ?: ""
            actions.add(SmartAction(label = "Email", actionType = SmartActionType.EMAIL, targetUri = "mailto:$email"))
        }

        // 3. Phone Number Check
        val phoneMatcher = PHONE_PATTERN.matcher(trimmed)
        if (phoneMatcher.find()) {
            val phone = phoneMatcher.group(0) ?: ""
            val digits = phone.filter { it.isDigit() }
            if (digits.length in 7..15) {
                actions.add(SmartAction(label = "Call", actionType = SmartActionType.CALL, targetUri = "tel:$phone"))
            }
        }

        return actions
    }
}

/**
 * Projected bounding rectangle in screen pixel space.
 */
data class ScreenBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Geometric projection utility mapping image-pixel coordinate bounding boxes into Compose
 * screen viewport coordinates with contentScale Fit letterbox scaling, zoom scale, and pan offsets.
 */
object TextGeometryProjector {
    fun projectToScreen(
        imageRect: Rect,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
        scale: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f,
    ): ScreenBoundingBox {
        return projectCoordinates(
            imageLeft = imageRect.left,
            imageTop = imageRect.top,
            imageRight = imageRect.right,
            imageBottom = imageRect.bottom,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            scale = scale,
            panX = panX,
            panY = panY,
        )
    }

    fun projectCoordinates(
        imageLeft: Int,
        imageTop: Int,
        imageRight: Int,
        imageBottom: Int,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
        scale: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f,
    ): ScreenBoundingBox {
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) {
            return ScreenBoundingBox(0f, 0f, 0f, 0f)
        }

        val fitScale = minOf(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
        val renderedW = imageWidth * fitScale * scale
        val renderedH = imageHeight * fitScale * scale
        val leftOrigin = (viewWidth - renderedW) / 2f + panX
        val topOrigin = (viewHeight - renderedH) / 2f + panY

        val totalScale = fitScale * scale
        return ScreenBoundingBox(
            left = leftOrigin + imageLeft * totalScale,
            top = topOrigin + imageTop * totalScale,
            right = leftOrigin + imageRight * totalScale,
            bottom = topOrigin + imageBottom * totalScale,
        )
    }

    /**
     * Hit-tests touch coordinates (screen space) against a list of screen-projected line bounding boxes.
     */
    fun findLineAt(
        touchX: Float,
        touchY: Float,
        lines: List<RecognizedTextLine>,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
        scale: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f,
        touchSlopPx: Float = 16f,
    ): RecognizedTextLine? {
        // Expand hit target by touchSlopPx for easy finger tapping
        return lines.firstOrNull { line ->
            val box = line.boundingBox ?: return@firstOrNull false
            val projected = projectToScreen(
                imageRect = box,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                scale = scale,
                panX = panX,
                panY = panY,
            )
            touchX >= (projected.left - touchSlopPx) &&
                touchX <= (projected.right + touchSlopPx) &&
                touchY >= (projected.top - touchSlopPx) &&
                touchY <= (projected.bottom + touchSlopPx)
        }
    }
}

/**
 * On-device ML Kit text recognition runner.
 */
object LiveTextRecognizer {
    suspend fun recognizeText(context: Context, uri: Uri): RecognizedTextResult = withContext(Dispatchers.IO) {
        val input = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val visionText = recognizer.process(input).await()
            val blocks = visionText.textBlocks.map { block ->
                RecognizedTextBlock(
                    text = block.text,
                    boundingBox = block.boundingBox,
                    lines = block.lines.map { line ->
                        RecognizedTextLine(
                            text = line.text,
                            boundingBox = line.boundingBox,
                            words = line.elements.map { el ->
                                RecognizedTextWord(
                                    text = el.text,
                                    boundingBox = el.boundingBox,
                                )
                            },
                        )
                    },
                )
            }
            RecognizedTextResult(
                fullText = visionText.text,
                blocks = blocks,
                imageWidth = input.width,
                imageHeight = input.height,
            )
        } finally {
            recognizer.close()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
    }
}
