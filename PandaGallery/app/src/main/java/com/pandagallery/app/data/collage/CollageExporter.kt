package com.pandagallery.app.data.collage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.effect.Presentation
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

@Singleton
@OptIn(UnstableApi::class)
internal class CollageExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun export(
        items: List<MediaItem>,
        configuration: CollageConfiguration,
        onProgress: (Int) -> Unit,
    ): Uri = if (items.any(MediaItem::isVideo) || configuration.audioTrack != null) {
        exportVideo(items, configuration, onProgress)
    } else {
        exportImage(items, configuration)
    }

    private suspend fun exportImage(
        items: List<MediaItem>,
        configuration: CollageConfiguration,
    ): Uri = withContext(Dispatchers.IO) {
        val (width, height) = outputSize(configuration.aspect, fallbackRatio = items.originalRatio())
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            drawBackground(canvas, width, height, configuration, items)

            if (configuration.layoutMode == CollageLayoutMode.FREESTYLE) {
                val freestyleCells = FreestyleLayouts.cells(items.size, configuration.freestyleIndex)
                items.forEachIndexed { index, item ->
                    val cell = freestyleCells[index]
                    val bitmap = decodeStill(item)
                    try {
                        drawFreestyleCell(
                            canvas = canvas,
                            bitmap = bitmap,
                            cell = cell,
                            transform = configuration.itemTransforms[item.id] ?: CollageItemTransform(),
                            globalFilter = configuration.globalFilter,
                            cornerPercent = configuration.cornerPercent,
                            outputWidth = width,
                            outputHeight = height,
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            } else {
                val cells = CollageLayouts.cells(items.size, configuration.layoutIndex, configuration.splitRatio, configuration.subSplitRatio)
                val margin = configuration.outerMarginPercent
                items.forEachIndexed { index, item ->
                    val cell = cells[index].inset(configuration.spacingPercent / 2f)
                    val insetCell = NormalizedRect(
                        left = cell.left * (1f - margin * 2f) + margin,
                        top = cell.top * (1f - margin * 2f) + margin,
                        right = cell.right * (1f - margin * 2f) + margin,
                        bottom = cell.bottom * (1f - margin * 2f) + margin,
                    )
                    val bitmap = decodeStill(item)
                    try {
                        drawGridCell(
                            canvas = canvas,
                            bitmap = bitmap,
                            normalizedCell = insetCell,
                            transform = configuration.itemTransforms[item.id] ?: CollageItemTransform(),
                            globalFilter = configuration.globalFilter,
                            cornerPercent = configuration.cornerPercent,
                            outputWidth = width,
                            outputHeight = height,
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }

            // Draw sticker overlays
            configuration.stickerOverlays.forEach { sticker ->
                drawStickerOverlay(canvas, sticker, width, height)
            }

            // Draw text overlays
            configuration.textOverlays.forEach { textOverlay ->
                drawTextOverlay(canvas, textOverlay, width, height)
            }

            // Freehand marks sit on top of everything else, the way they were drawn.
            drawStrokes(canvas, configuration.strokes, width, height)

            publishBitmap(output)
        } finally {
            output.recycle()
        }
    }

    private fun drawStickerOverlay(canvas: Canvas, sticker: CollageStickerOverlay, width: Int, height: Int) {
        val cx = sticker.x * width
        val cy = sticker.y * height
        val textSizePx = width * sticker.sizeFraction
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            textAlign = Paint.Align.CENTER
            setShadowLayer(8f, 0f, 4f, 0x55000000)
        }
        canvas.save()
        if (sticker.rotation != 0f) {
            canvas.rotate(sticker.rotation, cx, cy)
        }
        val bounds = Rect()
        paint.getTextBounds(sticker.emoji, 0, sticker.emoji.length, bounds)
        val yOffset = bounds.height() / 2f
        canvas.drawText(sticker.emoji, cx, cy + yOffset, paint)
        canvas.restore()
    }

    private fun drawTextOverlay(canvas: Canvas, textOverlay: CollageTextOverlay, width: Int, height: Int) {
        val cx = textOverlay.x * width
        val cy = textOverlay.y * height
        val textSizePx = width * textOverlay.fontSizeFraction
        val font = textOverlay.font
        val typefaceStyle = when {
            font.bold && font.italic -> Typeface.BOLD_ITALIC
            font.bold -> Typeface.BOLD
            font.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val face = Typeface.create(font.familyName, typefaceStyle)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            textAlign = Paint.Align.CENTER
            typeface = face
            color = textOverlay.color
        }
        canvas.save()
        if (textOverlay.rotation != 0f) {
            canvas.rotate(textOverlay.rotation, cx, cy)
        }
        val bounds = Rect()
        paint.getTextBounds(textOverlay.text, 0, textOverlay.text.length, bounds)

        textOverlay.backgroundColor?.let { plate ->
            val paddingX = textSizePx * 0.45f
            val paddingY = textSizePx * 0.30f
            val bgRect = RectF(
                cx - bounds.width() / 2f - paddingX,
                cy - bounds.height() / 2f - paddingY,
                cx + bounds.width() / 2f + paddingX,
                cy + bounds.height() / 2f + paddingY,
            )
            val pillRadius = bgRect.height() / 2f
            canvas.drawRoundRect(bgRect, pillRadius, pillRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plate })
        }

        val yOffset = bounds.height() / 2f - bounds.bottom / 2f
        if (textOverlay.outlined) {
            // Stroked first, filled second: a contrasting outline is what keeps a caption readable
            // when it happens to land on a photo of the same tone as the text.
            val outline = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = textSizePx * 0.09f
                strokeJoin = Paint.Join.ROUND
                color = if (isLightColor(textOverlay.color)) 0xCC000000.toInt() else 0xCCFFFFFF.toInt()
            }
            canvas.drawText(textOverlay.text, cx, cy + yOffset, outline)
        } else {
            paint.setShadowLayer(8f, 0f, 4f, 0x77000000)
        }
        canvas.drawText(textOverlay.text, cx, cy + yOffset, paint)
        canvas.restore()
    }

    private fun isLightColor(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) > 150f
    }

    /** Renders the freehand layer. Stroke coordinates are normalised, so this scales to any output. */
    private fun drawStrokes(canvas: Canvas, strokes: List<CollageStroke>, width: Int, height: Int) {
        if (strokes.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            if (stroke.points.size < 4) continue
            paint.color = stroke.color
            paint.strokeWidth = (stroke.widthFraction * width).coerceAtLeast(1f)
            val path = Path()
            path.moveTo(stroke.points[0] * width, stroke.points[1] * height)
            var index = 2
            while (index + 1 < stroke.points.size) {
                path.lineTo(stroke.points[index] * width, stroke.points[index + 1] * height)
                index += 2
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawBackground(
        canvas: Canvas,
        width: Int,
        height: Int,
        configuration: CollageConfiguration,
        items: List<MediaItem>,
    ) {
        when (configuration.backgroundMode) {
            CollageBackgroundMode.COLOR -> {
                canvas.drawColor(configuration.backgroundColor)
            }
            CollageBackgroundMode.GRADIENT -> {
                val gradient = COLLAGE_GRADIENTS.getOrElse(configuration.gradientIndex) { COLLAGE_GRADIENTS[0] }
                val shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    gradient.startColor, gradient.endColor,
                    Shader.TileMode.CLAMP,
                )
                val paint = Paint().apply { this.shader = shader }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            CollageBackgroundMode.PATTERN -> {
                drawPattern(canvas, width, height, COLLAGE_PATTERNS.getOrElse(configuration.patternIndex) { COLLAGE_PATTERNS[0] })
            }
            CollageBackgroundMode.BLURRED_MEDIA -> {
                val bgUri = configuration.backgroundImageUri ?: items.firstOrNull()?.uri
                if (bgUri != null) {
                    try {
                        val src = ImageDecoder.createSource(context.contentResolver, bgUri)
                        val sample = ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.setTargetSampleSize(4)
                        }
                        val blurred = fastBlur(sample, 24)
                        val dest = RectF(0f, 0f, width.toFloat(), height.toFloat())
                        canvas.drawBitmap(blurred, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
                        canvas.drawColor(0x33000000)
                        sample.recycle()
                        blurred.recycle()
                    } catch (_: Exception) {
                        canvas.drawColor(configuration.backgroundColor)
                    }
                } else {
                    canvas.drawColor(configuration.backgroundColor)
                }
            }
        }
    }

    /**
     * Procedural pattern fill. Generated rather than shipped as assets so it stays crisp at any
     * export size and costs nothing in the APK.
     */
    private fun drawPattern(canvas: Canvas, width: Int, height: Int, pattern: CollagePattern) {
        canvas.drawColor(pattern.backgroundColor)
        val tile = (minOf(width, height) * pattern.scale).coerceAtLeast(4f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pattern.inkColor }
        when (pattern.kind) {
            CollagePatternKind.DOTS -> {
                val radius = tile * 0.16f
                var y = tile / 2f
                var row = 0
                while (y < height + tile) {
                    var x = if (row % 2 == 0) tile / 2f else tile
                    while (x < width + tile) {
                        canvas.drawCircle(x, y, radius, paint)
                        x += tile
                    }
                    y += tile
                    row++
                }
            }
            CollagePatternKind.STRIPES -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = tile * 0.45f
                // Diagonal, so the stripe never lines up with a cell edge and reads as a texture.
                var x = -height.toFloat()
                while (x < width + height) {
                    canvas.drawLine(x, 0f, x + height, height.toFloat(), paint)
                    x += tile * 2f
                }
            }
            CollagePatternKind.GRID -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (tile * 0.06f).coerceAtLeast(1f)
                var x = 0f
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height.toFloat(), paint)
                    x += tile
                }
                var y = 0f
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, paint)
                    y += tile
                }
            }
            CollagePatternKind.CHECKER -> {
                var row = 0
                var y = 0f
                while (y < height) {
                    var column = 0
                    var x = 0f
                    while (x < width) {
                        if ((row + column) % 2 == 0) {
                            canvas.drawRect(x, y, x + tile, y + tile, paint)
                        }
                        x += tile
                        column++
                    }
                    y += tile
                    row++
                }
            }
        }
    }

    private fun drawGridCell(
        canvas: Canvas,
        bitmap: Bitmap,
        normalizedCell: NormalizedRect,
        transform: CollageItemTransform,
        globalFilter: CollageFilter,
        cornerPercent: Float,
        outputWidth: Int,
        outputHeight: Int,
    ) {
        val destination = RectF(
            normalizedCell.left * outputWidth,
            normalizedCell.top * outputHeight,
            normalizedCell.right * outputWidth,
            normalizedCell.bottom * outputHeight,
        )
        val radius = max(outputWidth, outputHeight) * cornerPercent
        drawTransformedBitmap(
            canvas = canvas,
            bitmap = bitmap,
            destination = destination,
            rotationDegrees = 0f,
            elevation = 0f,
            radius = radius,
            transform = transform,
            globalFilter = globalFilter,
        )
    }

    private fun drawFreestyleCell(
        canvas: Canvas,
        bitmap: Bitmap,
        cell: FreestyleCell,
        transform: CollageItemTransform,
        globalFilter: CollageFilter,
        cornerPercent: Float,
        outputWidth: Int,
        outputHeight: Int,
    ) {
        val destination = RectF(
            cell.rect.left * outputWidth,
            cell.rect.top * outputHeight,
            cell.rect.right * outputWidth,
            cell.rect.bottom * outputHeight,
        )
        val radius = max(outputWidth, outputHeight) * (cornerPercent.coerceAtLeast(0.015f))
        drawTransformedBitmap(
            canvas = canvas,
            bitmap = bitmap,
            destination = destination,
            rotationDegrees = cell.rotationDegrees,
            elevation = cell.elevation,
            radius = radius,
            transform = transform,
            globalFilter = globalFilter,
            drawFrame = true,
        )
    }

    private fun drawTransformedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        rotationDegrees: Float,
        elevation: Float,
        radius: Float,
        transform: CollageItemTransform,
        globalFilter: CollageFilter,
        drawFrame: Boolean = false,
    ) {
        canvas.save()

        if (rotationDegrees != 0f) {
            canvas.rotate(rotationDegrees, destination.centerX(), destination.centerY())
        }

        if (elevation > 0f) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x40000000
                setShadowLayer(elevation * 3f, 0f, elevation * 1.5f, 0x55000000)
            }
            canvas.drawRoundRect(destination, radius, radius, shadowPaint)
        }

        val clipPath = Path().apply {
            addRoundRect(destination, radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        val effectiveFilter = if (transform.filter != CollageFilter.ORIGINAL) transform.filter else globalFilter
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (effectiveFilter != CollageFilter.ORIGINAL) {
                colorFilter = ColorMatrixColorFilter(effectiveFilter.toColorMatrix())
            }
        }

        val matrix = Matrix()
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        val destW = destination.width()
        val destH = destination.height()

        // Crop to fill by default; contentFit letterboxes the whole photo instead.
        val scaleBase = if (transform.contentFit) min(destW / srcW, destH / srcH) else max(destW / srcW, destH / srcH)
        val initialDx = destination.left + (destW - srcW * scaleBase) / 2f
        val initialDy = destination.top + (destH - srcH * scaleBase) / 2f

        matrix.postScale(scaleBase, scaleBase)
        matrix.postTranslate(initialDx, initialDy)

        val pivotX = destination.centerX()
        val pivotY = destination.centerY()

        val flipX = if (transform.flipHorizontal) -1f else 1f
        val flipY = if (transform.flipVertical) -1f else 1f
        if (flipX != 1f || flipY != 1f) {
            matrix.postScale(flipX, flipY, pivotX, pivotY)
        }

        if (transform.rotationDegrees != 0) {
            matrix.postRotate(transform.rotationDegrees.toFloat(), pivotX, pivotY)
        }

        if (transform.scale != 1f) {
            matrix.postScale(transform.scale, transform.scale, pivotX, pivotY)
        }

        if (transform.offsetX != 0f || transform.offsetY != 0f) {
            matrix.postTranslate(transform.offsetX * destW, transform.offsetY * destH)
        }

        canvas.drawBitmap(bitmap, matrix, paint)

        if (drawFrame) {
            val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = 0xFFFFFFFF.toInt()
            }
            canvas.drawRoundRect(destination, radius, radius, framePaint)
        }

        canvas.restore()
    }

    /** A deliberately tiny decode, used for colour sampling rather than rendering. */
    fun decodeThumbnail(item: MediaItem, targetPx: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(sampleSize(item.width, item.height, targetPx))
        }.copy(Bitmap.Config.ARGB_8888, false)

    private fun decodeStill(item: MediaItem): Bitmap {
        if (!item.isVideo) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(sampleSize(item.width, item.height, 2400))
            }
        }
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, item.uri)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: throw IOException("Unable to read ${item.displayName}")
        }
    }

    private fun publishBitmap(bitmap: Bitmap): Uri {
        val destination = insertPending(
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            displayName = "Collage_${System.currentTimeMillis()}.jpg",
            mimeType = "image/jpeg",
            relativePath = "Pictures/PandaGallery/Collages/",
        )
        try {
            context.contentResolver.openOutputStream(destination)?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, output)) {
                    throw IOException("Unable to encode collage")
                }
            } ?: throw IOException("Unable to write collage")
            finishPending(destination)
            return destination
        } catch (error: Exception) {
            context.contentResolver.delete(destination, null, null)
            throw error
        }
    }

    private suspend fun exportVideo(
        items: List<MediaItem>,
        configuration: CollageConfiguration,
        onProgress: (Int) -> Unit,
    ): Uri = withContext(Dispatchers.Main.immediate) {
        val audioTrack = configuration.audioTrack
        val durationMs = if (items.any(MediaItem::isVideo)) {
            items.mapNotNull { item ->
                val transform = configuration.itemTransforms[item.id]
                val itemDuration = item.duration ?: 5_000L
                val trimmedDuration = if (transform?.trimEndMs != null && transform.trimEndMs > transform.trimStartMs) {
                    transform.trimEndMs - transform.trimStartMs
                } else {
                    itemDuration - (transform?.trimStartMs ?: 0L)
                }
                trimmedDuration.coerceAtLeast(1_000L)
            }.maxOrNull()?.coerceIn(3_000L, 60_000L) ?: 10_000L
        } else if (audioTrack != null) {
            val trimmedAudio = if (audioTrack.trimEndMs != null && audioTrack.trimEndMs > audioTrack.trimStartMs) {
                audioTrack.trimEndMs - audioTrack.trimStartMs
            } else if (audioTrack.durationMs > 0) {
                audioTrack.durationMs - audioTrack.trimStartMs
            } else {
                10_000L
            }
            trimmedAudio.coerceIn(3_000L, 60_000L)
        } else {
            5_000L
        }

        // 1920 rather than the old 1080: a video collage was coming out at less than half the
        // resolution of the same collage saved as a still, with nothing telling the user. Kept an
        // even number in both axes because encoders reject odd dimensions.
        val (rawWidth, rawHeight) = outputSize(configuration.aspect, VIDEO_LONG_EDGE, items.originalRatio())
        val outputWidth = rawWidth / 2 * 2
        val outputHeight = rawHeight / 2 * 2
        val background = createBackgroundFile(outputWidth, outputHeight, configuration, items)
        val output = File(context.cacheDir, "collage_${UUID.randomUUID()}.mp4")
        var cachedAudioFile: File? = null
        var overlayLayer: Bitmap? = null

        try {
            val effectiveConfig = if (audioTrack != null && (audioTrack.isOnline || audioTrack.uri.scheme?.startsWith("http") == true)) {
                val file = downloadRemoteAudio(audioTrack.uri.toString())
                cachedAudioFile = file
                configuration.copy(audioTrack = audioTrack.copy(uri = Uri.fromFile(file)))
            } else {
                configuration
            }

            val sequences = buildVideoSequences(
                items = items,
                durationMs = durationMs,
                outputWidth = outputWidth,
                outputHeight = outputHeight,
                background = background,
                configuration = effectiveConfig,
            )
            val cells = if (configuration.layoutMode == CollageLayoutMode.FREESTYLE) {
                FreestyleLayouts.cells(items.size, configuration.freestyleIndex).map { it.rect }
            } else {
                CollageLayouts.cells(items.size, configuration.layoutIndex, configuration.splitRatio, configuration.subSplitRatio)
                    .map { it.inset(configuration.spacingPercent / 2f) }
            }
            // Applied to the composition rather than to any one sequence, so the overlay lands on
            // top of every cell instead of inside one of them.
            overlayLayer = renderOverlayLayer(effectiveConfig, outputWidth, outputHeight)
            val compositionBuilder = Composition.Builder(sequences)
                .setVideoCompositorSettings(CollageCompositorSettings(outputWidth, outputHeight, cells))
            overlayLayer?.let { layer ->
                compositionBuilder.setEffects(
                    Effects(
                        emptyList(),
                        listOf(OverlayEffect(ImmutableList.of<TextureOverlay>(BitmapOverlay.createStaticBitmapOverlay(layer)))),
                    )
                )
            }
            val composition = compositionBuilder.build()
            transform(composition, output, onProgress)
            publishVideo(output)
        } finally {
            background.delete()
            output.delete()
            cachedAudioFile?.delete()
            overlayLayer?.recycle()
        }
    }

    private fun downloadRemoteAudio(urlString: String): File {
        val cacheFile = File(context.cacheDir, "export_audio_${UUID.randomUUID()}.mp3")
        val url = java.net.URL(urlString)
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PandaGallery/1.0")
        }
        conn.inputStream.use { input ->
            java.io.FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }
        return cacheFile
    }

    private fun buildVideoSequences(
        items: List<MediaItem>,
        durationMs: Long,
        outputWidth: Int,
        outputHeight: Int,
        background: File,
        configuration: CollageConfiguration,
    ): List<EditedMediaItemSequence> {
        val presentation: List<Effect> = listOf(
            Presentation.createForWidthAndHeight(
                outputWidth,
                outputHeight,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
            )
        )
        val backgroundItem = EditedMediaItem.Builder(
            Media3Item.Builder().setUri(Uri.fromFile(background)).setImageDurationMs(durationMs).build()
        ).setDurationUs(durationMs * 1_000L).setFrameRate(30).setRemoveAudio(true).build()
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(backgroundItem).build())
        val audibleVideoIndex = items.indexOfFirst(MediaItem::isVideo)
        val audioTrack = configuration.audioTrack
        val hasCustomAudio = audioTrack != null

        items.forEachIndexed { index, item ->
            val transform = configuration.itemTransforms[item.id]
            val mediaBuilder = Media3Item.Builder().setUri(item.uri)

            if (!item.isVideo) {
                mediaBuilder.setImageDurationMs(durationMs)
            } else if (transform != null && (transform.trimStartMs > 0 || transform.trimEndMs != null)) {
                val clipBuilder = Media3Item.ClippingConfiguration.Builder()
                    .setStartPositionMs(transform.trimStartMs)
                transform.trimEndMs?.let { clipBuilder.setEndPositionMs(it) }
                mediaBuilder.setClippingConfiguration(clipBuilder.build())
            }

            val removeAudio = configuration.muteVideos || (hasCustomAudio && configuration.muteVideos) || (index != audibleVideoIndex && !hasCustomAudio)

            val edited = EditedMediaItem.Builder(mediaBuilder.build())
                .setDurationUs(durationMs * 1_000L)
                .setFrameRate(30)
                .setRemoveAudio(removeAudio)
                .setEffects(Effects(emptyList(), presentation))
                .build()
            sequences += EditedMediaItemSequence.Builder(edited).setIsLooping(true).build()
        }

        if (audioTrack != null) {
            val audioMediaBuilder = Media3Item.Builder().setUri(audioTrack.uri)
            if (audioTrack.trimStartMs > 0 || audioTrack.trimEndMs != null) {
                val clipBuilder = Media3Item.ClippingConfiguration.Builder()
                    .setStartPositionMs(audioTrack.trimStartMs)
                audioTrack.trimEndMs?.let { clipBuilder.setEndPositionMs(it) }
                audioMediaBuilder.setClippingConfiguration(clipBuilder.build())
            }
            val audioItem = EditedMediaItem.Builder(audioMediaBuilder.build())
                .setDurationUs(durationMs * 1_000L)
                .build()
            sequences += EditedMediaItemSequence.Builder(audioItem).setIsLooping(true).build()
        }

        return sequences
    }

    private suspend fun transform(
        composition: Composition,
        output: File,
        onProgress: (Int) -> Unit,
    ) = suspendCancellableCoroutine { continuation ->
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            })
            .build()
        val progress = ProgressHolder()
        val poller = object : Runnable {
            override fun run() {
                if (!continuation.isActive) return
                if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progress.progress)
                }
                mainHandler.postDelayed(this, 350)
            }
        }
        continuation.invokeOnCancellation { mainHandler.post(transformer::cancel) }
        transformer.start(composition, output.absolutePath)
        mainHandler.post(poller)
    }

    private fun publishVideo(output: File): Uri {
        val destination = insertPending(
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            displayName = "Collage_${System.currentTimeMillis()}.mp4",
            mimeType = "video/mp4",
            relativePath = "Movies/PandaGallery/Collages/",
        )
        try {
            context.contentResolver.openOutputStream(destination)?.use { stream ->
                output.inputStream().use { it.copyTo(stream) }
            } ?: throw IOException("Unable to write video collage")
            finishPending(destination)
            return destination
        } catch (error: Exception) {
            context.contentResolver.delete(destination, null, null)
            throw error
        }
    }

    private fun insertPending(collection: Uri, displayName: String, mimeType: String, relativePath: String): Uri =
        context.contentResolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: throw IOException("Unable to create collage")

    private fun finishPending(uri: Uri) {
        context.contentResolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
    }

    /**
     * The video path's backdrop. It used to be a flat fill of [CollageConfiguration.backgroundColor],
     * which silently threw away gradients, patterns and the blurred-media mode whenever the collage
     * contained a video. It now runs the same [drawBackground] the still path does.
     */
    private fun createBackgroundFile(
        width: Int,
        height: Int,
        configuration: CollageConfiguration,
        items: List<MediaItem>,
    ): File {
        val file = File(context.cacheDir, "collage_background_${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            drawBackground(Canvas(bitmap), width, height, configuration, items)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    /**
     * Stickers, captions and freehand marks rendered onto transparency, to be laid over the
     * composited video. Returns null when there is nothing to draw, so the common case adds no
     * effect to the pipeline at all.
     */
    private fun renderOverlayLayer(configuration: CollageConfiguration, width: Int, height: Int): Bitmap? {
        if (configuration.stickerOverlays.isEmpty() &&
            configuration.textOverlays.isEmpty() &&
            configuration.strokes.isEmpty()
        ) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        configuration.stickerOverlays.forEach { drawStickerOverlay(canvas, it, width, height) }
        configuration.textOverlays.forEach { drawTextOverlay(canvas, it, width, height) }
        drawStrokes(canvas, configuration.strokes, width, height)
        return bitmap
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / sample > target * 2 || height / sample > target * 2) sample *= 2
        return sample
    }

    private fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = Bitmap.createScaledBitmap(sentBitmap, sentBitmap.width / 4, sentBitmap.height / 4, false)
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        yi = 0
        yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        y = 0
        while (y < h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (idx in -radius..radius) {
                p = pix[yi + minOf(wm, maxOf(idx, 0))]
                val sir = stack[idx + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - Math.abs(idx)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (idx > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                val sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = minOf(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                val sir2 = stack[stackpointer]

                routsum += sir2[0]
                goutsum += sir2[1]
                boutsum += sir2[2]

                rinsum -= sir2[0]
                ginsum -= sir2[1]
                binsum -= sir2[2]

                yi++
                x++
            }
            yw += w
            y++
        }

        x = 0
        while (x < w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (idx in -radius..radius) {
                yi = maxOf(0, yp) + x
                val sir = stack[idx + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - Math.abs(idx)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (idx > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (idx < hm) {
                    yp += w
                }
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                val sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = minOf(y + r1, hm) * w
                }
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                val sir2 = stack[stackpointer]

                routsum += sir2[0]
                goutsum += sir2[1]
                boutsum += sir2[2]

                rinsum -= sir2[0]
                ginsum -= sir2[1]
                binsum -= sir2[2]

                yi += w
                y++
            }
            x++
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}

@OptIn(UnstableApi::class)
private class CollageCompositorSettings(
    private val width: Int,
    private val height: Int,
    private val cells: List<NormalizedRect>,
) : VideoCompositorSettings {
    override fun getOutputSize(inputSizes: List<Size>) = Size(width, height)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        if (inputId == 0) return OverlaySettings.Builder().build()
        val cell = cells.getOrNull(inputId - 1) ?: return OverlaySettings.Builder().build()
        return OverlaySettings.Builder()
            .setScale(cell.width, cell.height)
            .setBackgroundFrameAnchor(cell.centerX * 2f - 1f, 1f - cell.centerY * 2f)
            .setOverlayFrameAnchor(0f, 0f)
            .build()
    }
}
