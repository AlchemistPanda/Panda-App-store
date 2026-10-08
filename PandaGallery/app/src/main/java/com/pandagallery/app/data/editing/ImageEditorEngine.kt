package com.pandagallery.app.data.editing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Shader
import android.graphics.RadialGradient
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageEditorEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: NondestructiveBackupManager,
) {

    suspend fun saveCopy(
        item: MediaItem,
        edit: ImageEditState,
        saveAsCopy: Boolean = true,
    ): Uri = withContext(Dispatchers.IO) {
        if (!saveAsCopy) {
            backupManager.ensureBackup(item)
        }

        var bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

        // 0. Samsung One UI Document Scan & Perspective Dewarp
        if (edit.documentCorners != null) {
            val dewarped = DocumentDewarper.dewarp(bitmap, edit.documentCorners, edit.documentEnhancement)
            if (dewarped !== bitmap) bitmap.recycle()
            bitmap = dewarped
        }

        // 1. Perspective Keystone Skew Transform
        if (edit.perspectiveVertical != 0f || edit.perspectiveHorizontal != 0f) {
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val src = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
            val pv = (edit.perspectiveVertical / 45f).coerceIn(-0.4f, 0.4f)
            val ph = (edit.perspectiveHorizontal / 45f).coerceIn(-0.4f, 0.4f)

            val dxTop = if (pv > 0) w * pv * 0.35f else 0f
            val dxBottom = if (pv < 0) w * -pv * 0.35f else 0f
            val dyLeft = if (ph < 0) h * -ph * 0.35f else 0f
            val dyRight = if (ph > 0) h * ph * 0.35f else 0f

            val dst = floatArrayOf(
                dxTop, dyLeft,
                w - dxTop, dyRight,
                w - dxBottom, h - dyRight,
                dxBottom, h - dyLeft
            )
            val pMatrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
            val warped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, pMatrix, true)
            if (warped !== bitmap) bitmap.recycle()
            bitmap = warped
        }

        // 2. Rotation, Flip & Auto-Scaled Straighten
        val totalRotation = edit.rotationDegrees + edit.straightenDegrees
        if (totalRotation % 360f != 0f || edit.flipHorizontal || edit.flipVertical) {
            val scaleFactor = if (edit.straightenDegrees != 0f) {
                calculateStraightenScale(edit.straightenDegrees, bitmap.width.toFloat() / bitmap.height)
            } else 1f

            val matrix = Matrix().apply {
                postScale(if (edit.flipHorizontal) -1f else 1f, if (edit.flipVertical) -1f else 1f)
                postRotate(totalRotation)
                if (scaleFactor > 1f) {
                    postScale(scaleFactor, scaleFactor)
                }
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }

        // 3. Crop Window & Panning
        val crop = if (edit.crop.left > 0.001f || edit.crop.top > 0.001f || edit.crop.right < 0.999f || edit.crop.bottom < 0.999f) {
            edit.crop.sanitized().let { normalized ->
                IntCrop(
                    (normalized.left * bitmap.width).toInt(),
                    (normalized.top * bitmap.height).toInt(),
                    (normalized.right * bitmap.width).toInt(),
                    (normalized.bottom * bitmap.height).toInt(),
                )
            }
        } else centeredCrop(bitmap.width, bitmap.height, edit.cropAspect)

        if (crop.width != bitmap.width || crop.height != bitmap.height) {
            val safeLeft = crop.left.coerceIn(0, bitmap.width - 1)
            val safeTop = crop.top.coerceIn(0, bitmap.height - 1)
            val safeWidth = crop.width.coerceIn(1, bitmap.width - safeLeft)
            val safeHeight = crop.height.coerceIn(1, bitmap.height - safeTop)
            val cropped = Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
            if (cropped !== bitmap) bitmap.recycle()
            bitmap = cropped
        }

        // 4. Crop Shape Mask (Oval / Rounded Rect)
        if (edit.cropShape == CropShape.OVAL || edit.cropShape == CropShape.ROUNDED_RECT) {
            val shaped = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val shapeCanvas = Canvas(shaped)
            val path = Path().apply {
                val rectF = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                if (edit.cropShape == CropShape.OVAL) {
                    addOval(rectF, Path.Direction.CW)
                } else {
                    addRoundRect(rectF, 48f, 48f, Path.Direction.CW)
                }
            }
            shapeCanvas.clipPath(path)
            shapeCanvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
            bitmap.recycle()
            bitmap = shaped
        }

        // 5. Tone, Filter & HSL Matrix Processing
        if (edit.hasToneChanges() || edit.filter != ImageFilter.NONE) {
            val filtered = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val matrix = ColorMatrix(toneColorMatrixArray(edit))
            Canvas(filtered).drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            })
            bitmap.recycle()
            bitmap = filtered
        }

        // 6. Spline Curves LUT
        if (edit.curvePoints[CurveChannel.MASTER]?.isNotEmpty() == true) {
            val lut = generateCurveSplineLut(edit.curvePoints[CurveChannel.MASTER]!!)
            bitmap = applyCurveLut(bitmap, lut)
        } else {
            curveLut(edit.curvePreset)?.let { lut -> bitmap = applyCurveLut(bitmap, lut) }
        }

        if (edit.remasterDetailLevel > 0f) bitmap = applyRemaster(bitmap, edit.remasterDetailLevel, recycleSource = true)
        if (edit.eraseStrokes.isNotEmpty()) {
            val inpainted = ObjectInpainter.inpaint(bitmap, edit.eraseStrokes)
            if (inpainted !== bitmap) bitmap.recycle()
            bitmap = inpainted
        }
        if (edit.eraseShadows) {
            val shadowFree = ObjectInpainter.eraseShadows(bitmap)
            if (shadowFree !== bitmap) bitmap.recycle()
            bitmap = shadowFree
        }
        if (edit.eraseReflections) {
            val reflectionFree = ObjectInpainter.eraseReflections(bitmap)
            if (reflectionFree !== bitmap) bitmap.recycle()
            bitmap = reflectionFree
        }

        // 7. Decorations, Markups, Text & Stickers
        if (edit.markup.isNotEmpty() || edit.textOverlays.isNotEmpty() || edit.stickers.isNotEmpty() || edit.areaMosaics.isNotEmpty() || edit.vignette > 0f) {
            if (!bitmap.isMutable) {
                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                bitmap.recycle()
                bitmap = mutable
            }
            renderDecorations(bitmap, edit)
        }

        // 8. Resolution Downscaling (100%, 80%, 60%, 40%)
        if (edit.exportResolutionPercent in 10..99) {
            val factor = edit.exportResolutionPercent / 100f
            val targetW = (bitmap.width * factor).toInt().coerceAtLeast(100)
            val targetH = (bitmap.height * factor).toInt().coerceAtLeast(100)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val base = item.displayName.substringBeforeLast('.', item.displayName)
        val suffix = if (saveAsCopy) "_edited_${System.currentTimeMillis() % 10000}" else ""
        val ext = edit.exportFormat.extension
        val mime = edit.exportFormat.mimeType

        val destination = if (saveAsCopy) {
            context.contentResolver.insert(collection, ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${base}${suffix}.${ext}")
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PandaGallery Edited/")
                item.dateTaken?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }) ?: throw IOException("Unable to create edited image")
        } else {
            item.uri
        }

        val compressFormat = if (edit.exportFormat == ExportFormat.WEBP) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        } else {
            Bitmap.CompressFormat.JPEG
        }

        try {
            context.contentResolver.openOutputStream(destination, if (saveAsCopy) "w" else "rwt")?.use { output ->
                if (!bitmap.compress(compressFormat, 95, output)) throw IOException("Unable to encode edited image")
            } ?: throw IOException("Unable to write edited image")

            if (saveAsCopy) {
                context.contentResolver.update(destination, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }

            // 9. Strip GPS/Location metadata if requested
            if (edit.stripLocationData) {
                try {
                    context.contentResolver.openFileDescriptor(destination, "rw")?.use { pfd ->
                        val exif = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE, null)
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE, null)
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE, null)
                        exif.saveAttributes()
                    }
                } catch (_: Exception) {}
            }

            destination
        } catch (error: IOException) {
            if (saveAsCopy) context.contentResolver.delete(destination, null, null)
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    private fun ImageEditState.hasToneChanges() =
        brightness != 0f || exposure != 0f || contrast != 0f || saturation != 0f ||
        warmth != 0f || tint != 0f || highlights != 0f || shadows != 0f ||
        sharpness != 0f || definition != 0f ||
        whiteBalanceTemperature != 0f || whiteBalanceTint != 0f || hueShift != 0f ||
        faceTone != 0f || faceSmoothness != 0f || faceEyeBrighten != 0f || autoEnhanced

    private fun applyCurveLut(source: Bitmap, lut: IntArray): Bitmap {
        val target = if (source.isMutable) source else {
            val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
            source.recycle()
            mutable
        }
        val pixels = IntArray(target.width * target.height)
        target.getPixels(pixels, 0, target.width, 0, 0, target.width, target.height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = pixel ushr 24 and 0xff
            val r = lut[pixel shr 16 and 0xff]
            val g = lut[pixel shr 8 and 0xff]
            val b = lut[pixel and 0xff]
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        target.setPixels(pixels, 0, target.width, 0, 0, target.width, target.height)
        return target
    }

    private fun renderDecorations(bitmap: Bitmap, edit: ImageEditState) {
        val canvas = Canvas(bitmap)

        // Pre-compute blurred/pixelated version for Mosaic pen if needed
        var mosaicShader: BitmapShader? = null
        if (edit.markup.any { it.penType == PenType.MOSAIC_PIXEL || it.penType == PenType.MOSAIC_BLUR }) {
            val pixelated = pixelatedCopy(bitmap, edit.mosaicBlockSize.toInt().coerceIn(4, 64))
            mosaicShader = BitmapShader(pixelated, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        edit.markup.forEach { stroke ->
            val strokePath = stroke.path(bitmap)
            val strokeAlpha = (stroke.opacity.coerceIn(0.05f, 1f) * 255).toInt()
            when (stroke.penType) {
                PenType.REGULAR -> {
                    val paint = stroke.paint(bitmap).apply { alpha = strokeAlpha }
                    canvas.drawPath(strokePath, paint)
                }
                PenType.HIGHLIGHTER -> {
                    val paint = stroke.paint(bitmap).apply {
                        alpha = (115 * stroke.opacity).toInt().coerceIn(10, 255)
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
                        strokeCap = Paint.Cap.SQUARE
                        strokeWidth *= 2.2f
                    }
                    canvas.drawPath(strokePath, paint)
                }
                PenType.NEON -> {
                    // Outer glow
                    val glowPaint = stroke.paint(bitmap).apply {
                        strokeWidth *= 2.5f
                        maskFilter = BlurMaskFilter(strokeWidth * 0.8f, BlurMaskFilter.Blur.NORMAL)
                        alpha = (180 * stroke.opacity).toInt().coerceIn(10, 255)
                    }
                    canvas.drawPath(strokePath, glowPaint)
                    // Inner bright core
                    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        strokeWidth = bitmap.width.coerceAtMost(bitmap.height) * stroke.widthFraction * 0.8f
                        alpha = strokeAlpha
                    }
                    canvas.drawPath(strokePath, corePaint)
                }
                PenType.CALLIGRAPHY -> {
                    val paint = stroke.paint(bitmap).apply {
                        strokeCap = Paint.Cap.BUTT
                        strokeWidth *= 1.4f
                        alpha = strokeAlpha
                    }
                    canvas.drawPath(strokePath, paint)
                }
                PenType.PENCIL -> {
                    val paint = stroke.paint(bitmap).apply {
                        alpha = (210 * stroke.opacity).toInt().coerceIn(10, 255)
                        strokeWidth *= 0.8f
                    }
                    canvas.drawPath(strokePath, paint)
                }
                PenType.MOSAIC_PIXEL, PenType.MOSAIC_BLUR -> {
                    if (mosaicShader != null) {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            shader = mosaicShader
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            strokeWidth = bitmap.width.coerceAtMost(bitmap.height) * stroke.widthFraction * 3.5f
                            alpha = strokeAlpha
                        }
                        canvas.drawPath(strokePath, paint)
                    }
                }
            }
        }

        // Render Area Mosaics (Rectangle, Oval, or Rounded Rect Privacy Blurring / Pixelation)
        edit.areaMosaics.forEach { mosaic ->
            val rectF = RectF(
                mosaic.rect.left * bitmap.width,
                mosaic.rect.top * bitmap.height,
                mosaic.rect.right * bitmap.width,
                mosaic.rect.bottom * bitmap.height,
            )
            if (rectF.width() > 4 && rectF.height() > 4) {
                canvas.save()
                val clipPath = Path().apply {
                    when (mosaic.shape) {
                        CropShape.RECTANGLE -> addRect(rectF, Path.Direction.CW)
                        CropShape.OVAL -> addOval(rectF, Path.Direction.CW)
                        CropShape.ROUNDED_RECT -> addRoundRect(rectF, 24f, 24f, Path.Direction.CW)
                    }
                }
                canvas.clipPath(clipPath)
                if (mosaic.isBlur) {
                    val blurred = blurredCopy(bitmap, mosaic.intensity.toInt().coerceIn(6, 40))
                    canvas.drawBitmap(blurred, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
                    blurred.recycle()
                } else {
                    val pixelated = pixelatedCopy(bitmap, mosaic.intensity.toInt().coerceIn(6, 64))
                    canvas.drawBitmap(pixelated, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
                    pixelated.recycle()
                }
                canvas.restore()
            }
        }

        edit.textOverlays.forEach { overlay ->
            val textSize = bitmap.width.coerceAtMost(bitmap.height) * overlay.sizeFraction
            val textTypeface = when (overlay.fontStyle) {
                TextStyleFont.SERIF -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
                TextStyleFont.MONO -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                TextStyleFont.CURSIVE -> Typeface.create("sans-serif-medium", Typeface.ITALIC)
                TextStyleFont.DEFAULT -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val align = when (overlay.textAlign.lowercase()) {
                "left" -> Paint.Align.LEFT
                "right" -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = overlay.color.toInt()
                this.textAlign = align
                this.textSize = textSize
                typeface = textTypeface
                if (overlay.backgroundStyle == TextBackgroundStyle.NONE) {
                    setShadowLayer(textSize * .08f, 0f, textSize * .03f, android.graphics.Color.BLACK)
                }
            }

            val x = overlay.position.x * bitmap.width
            val y = overlay.position.y * bitmap.height

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(overlay.rotationDegrees)
            canvas.scale(overlay.scale, overlay.scale)

            if (overlay.backgroundStyle != TextBackgroundStyle.NONE) {
                val textBounds = android.graphics.Rect()
                textPaint.getTextBounds(overlay.text, 0, overlay.text.length, textBounds)
                val paddingH = textSize * 0.4f
                val paddingV = textSize * 0.25f
                val bgLeft = when (align) {
                    Paint.Align.LEFT -> -paddingH
                    Paint.Align.RIGHT -> -textBounds.width().toFloat() - paddingH
                    else -> -textBounds.width() / 2f - paddingH
                }
                val bgRect = RectF(
                    bgLeft,
                    -textBounds.height() - paddingV,
                    bgLeft + textBounds.width() + paddingH * 2f,
                    paddingV,
                )
                val radius = if (overlay.backgroundStyle == TextBackgroundStyle.CAPSULE) bgRect.height() / 2f else 8f
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (overlay.backgroundStyle == TextBackgroundStyle.OUTLINE) android.graphics.Color.TRANSPARENT else android.graphics.Color.argb(180, 20, 20, 20)
                    if (overlay.backgroundStyle == TextBackgroundStyle.OUTLINE) {
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                        color = overlay.color.toInt()
                    }
                }
                canvas.drawRoundRect(bgRect, radius, radius, bgPaint)
            }

            canvas.drawText(overlay.text, 0f, 0f, textPaint)
            canvas.restore()
        }

        edit.stickers.forEach { overlay ->
            val x = overlay.position.x * bitmap.width
            val y = overlay.position.y * bitmap.height

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(overlay.rotationDegrees)
            val scaleX = if (overlay.flipHorizontal) -overlay.scale else overlay.scale
            canvas.scale(scaleX, overlay.scale)

            canvas.drawText(
                overlay.symbol,
                0f,
                0f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = bitmap.width.coerceAtMost(bitmap.height) * overlay.sizeFraction
                },
            )
            canvas.restore()
        }

        if (edit.vignette > 0f) {
            val radius = bitmap.width.coerceAtLeast(bitmap.height) * .72f
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), Paint().apply {
                shader = RadialGradient(
                    bitmap.width / 2f,
                    bitmap.height / 2f,
                    radius,
                    intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb((220 * edit.vignette).toInt(), 0, 0, 0)),
                    floatArrayOf(.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            })
        }
    }

    private fun pixelatedCopy(source: Bitmap, pixelSize: Int): Bitmap {
        val w = (source.width / pixelSize).coerceAtLeast(2)
        val h = (source.height / pixelSize).coerceAtLeast(2)
        val tiny = Bitmap.createScaledBitmap(source, w, h, false)
        val pixelated = Bitmap.createScaledBitmap(tiny, source.width, source.height, false)
        if (tiny !== pixelated) tiny.recycle()
        return pixelated
    }

    private fun applyObjectBlur(source: Bitmap, strokes: List<MarkupStroke>): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val w = source.width
        val h = source.height
        // Multi-stage diffusion inpaint: deep blur background synthesis
        val diffuseBackdrop = blurredCopy(source, 24)
        val diffuseShader = BitmapShader(diffuseBackdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        val canvas = Canvas(result)
        val inpaintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = diffuseShader
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        }

        strokes.forEach { stroke ->
            val strokeWidthPx = w.coerceAtMost(h) * stroke.widthFraction * 2.8f
            inpaintPaint.strokeWidth = strokeWidthPx
            canvas.drawPath(stroke.path(source), inpaintPaint)
        }

        diffuseBackdrop.recycle()
        source.recycle()
        return result
    }

    private fun applyPortraitBlur(source: Bitmap, strength: Float): Bitmap {
        val w = source.width
        val h = source.height
        val blurRadius = (8 + 24 * strength.coerceIn(0f, 1f)).toInt()
        val blurredBackground = blurredCopy(source, blurRadius)

        // Generate feathered depth alpha mask for natural optical bokeh roll-off
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val maskCanvas = Canvas(mask)
        val cx = w * 0.5f
        val cy = h * 0.45f
        val radius = (w.coerceAtMost(h) * 0.46f)

        val depthShader = RadialGradient(
            cx, cy, radius,
            intArrayOf(
                android.graphics.Color.WHITE,
                android.graphics.Color.WHITE,
                android.graphics.Color.argb(160, 255, 255, 255),
                android.graphics.Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )

        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = depthShader
        }
        maskCanvas.drawCircle(cx, cy, radius, gradientPaint)

        // Composite: Sharp subject masked smoothly over creamy blurred bokeh background
        val sharpSubject = source.copy(Bitmap.Config.ARGB_8888, true)
        val sharpCanvas = Canvas(sharpSubject)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        sharpCanvas.drawBitmap(mask, 0f, 0f, maskPaint)
        mask.recycle()

        val output = blurredBackground.copy(Bitmap.Config.ARGB_8888, true)
        blurredBackground.recycle()
        val outputCanvas = Canvas(output)
        outputCanvas.drawBitmap(sharpSubject, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        sharpSubject.recycle()
        source.recycle()

        return output
    }

    /**
     * Works through the image in bands of [REMASTER_BAND_ROWS] rows. Three whole-frame IntArrays
     * (source, blur, output) put ~324 MB on the Java heap for a 6000x4500 decode — past the
     * usual 256-512 MB heap limit, so remastering any high-megapixel photo threw
     * OutOfMemoryError. Per band the arrays are a few MB whatever the resolution; the bitmaps
     * themselves are native memory. The per-pixel maths is unchanged.
     */
    fun applyRemaster(source: Bitmap, level: Float, recycleSource: Boolean = false): Bitmap {
        if (level <= 0f) return source
        val blurStrength = (source.width / 350).coerceIn(4, 16)
        val blurred = blurredCopy(source, blurStrength)

        val width = source.width
        val height = source.height
        val bandRows = REMASTER_BAND_ROWS.coerceAtMost(height)
        val srcPixels = IntArray(width * bandRows)
        val blurPixels = IntArray(width * bandRows)
        val outPixels = IntArray(width * bandRows)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val detailGain = level.coerceIn(0f, 1f) * 1.6f
        val clarityContrast = 1f + (level * 0.14f)
        val shadowLift = (level * 18f).toInt()

        for (top in 0 until height step bandRows) {
            val rows = minOf(bandRows, height - top)
            source.getPixels(srcPixels, 0, width, 0, top, width, rows)
            blurred.getPixels(blurPixels, 0, width, 0, top, width, rows)
            for (i in 0 until width * rows) {
                val sp = srcPixels[i]
                val bp = blurPixels[i]

                val sa = (sp shr 24) and 0xFF
                var sr = (sp shr 16) and 0xFF
                var sg = (sp shr 8) and 0xFF
                var sb = sp and 0xFF

                val br = (bp shr 16) and 0xFF
                val bg = (bp shr 8) and 0xFF
                val bb = bp and 0xFF

                // 1. Samsung AI Shadow Recovery (gently lift crushed darks while preserving contrast)
                val lum = (sr * 299 + sg * 587 + sb * 114) / 1000
                if (lum < 96) {
                    val shadowRatio = (96 - lum) / 96f
                    val lift = (shadowLift * shadowRatio).toInt()
                    sr = (sr + lift).coerceAtMost(255)
                    sg = (sg + lift).coerceAtMost(255)
                    sb = (sb + lift).coerceAtMost(255)
                }

                // 2. High-Frequency Detail Pop via Unsharp Masking
                val r = (sr + (sr - br) * detailGain).toInt().coerceIn(0, 255)
                val g = (sg + (sg - bg) * detailGain).toInt().coerceIn(0, 255)
                val b = (sb + (sb - bb) * detailGain).toInt().coerceIn(0, 255)

                // 3. Local Dynamic Clarity Pop
                val cr = (((r - 128) * clarityContrast) + 128).toInt().coerceIn(0, 255)
                val cg = (((g - 128) * clarityContrast) + 128).toInt().coerceIn(0, 255)
                val cb = (((b - 128) * clarityContrast) + 128).toInt().coerceIn(0, 255)

                // 4. Subtle Vibrancy boost for natural tones
                val maxC = maxOf(cr, cg, cb)
                val minC = minOf(cr, cg, cb)
                val sat = maxC - minC
                val finalR: Int
                val finalG: Int
                val finalB: Int
                if (sat in 15..140) {
                    val vibBoost = (level * 0.08f * (1f - sat / 150f))
                    finalR = (cr + (cr - lum) * vibBoost).toInt().coerceIn(0, 255)
                    finalG = (cg + (cg - lum) * vibBoost).toInt().coerceIn(0, 255)
                    finalB = (cb + (cb - lum) * vibBoost).toInt().coerceIn(0, 255)
                } else {
                    finalR = cr
                    finalG = cg
                    finalB = cb
                }

                outPixels[i] = (sa shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
            result.setPixels(outPixels, 0, width, 0, top, width, rows)
        }
        blurred.recycle()

        if (recycleSource) {
            source.recycle()
        }
        return result
    }

    suspend fun saveRemaster(
        item: MediaItem,
        remasteredBitmap: Bitmap,
        saveAsCopy: Boolean = true,
        useNearLosslessCompression: Boolean = true,
    ): Uri = withContext(Dispatchers.IO) {
        if (!saveAsCopy) {
            backupManager.ensureBackup(item)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val base = item.displayName.substringBeforeLast('.', item.displayName)
        val suffix = if (saveAsCopy) "_remastered_${System.currentTimeMillis() % 10000}" else ""
        val ext = if (useNearLosslessCompression) "webp" else "jpg"
        val mime = if (useNearLosslessCompression) "image/webp" else "image/jpeg"

        val destination = if (saveAsCopy) {
            context.contentResolver.insert(collection, ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${base}${suffix}.${ext}")
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PandaGallery Remastered/")
                item.dateTaken?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }) ?: throw IOException("Unable to create remastered image")
        } else {
            item.uri
        }

        val compressFormat = if (useNearLosslessCompression) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        } else {
            Bitmap.CompressFormat.JPEG
        }
        val quality = if (useNearLosslessCompression) 92 else 95

        try {
            context.contentResolver.openOutputStream(destination, if (saveAsCopy) "w" else "rwt")?.use { output ->
                if (!remasteredBitmap.compress(compressFormat, quality, output)) {
                    throw IOException("Unable to encode remastered image")
                }
            } ?: throw IOException("Unable to write remastered image")

            if (saveAsCopy) {
                context.contentResolver.update(destination, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }

            // Invalidate Coil image loader cache
            runCatching {
                val imageLoader = coil3.SingletonImageLoader.get(context)
                imageLoader.diskCache?.remove(destination.toString())
                imageLoader.memoryCache?.remove(coil3.memory.MemoryCache.Key(destination.toString()))
            }

            destination
        } catch (error: IOException) {
            if (saveAsCopy) context.contentResolver.delete(destination, null, null)
            throw error
        }
    }

    /**
     * A new bitmap the caller owns and must recycle, never [source] itself. Callers only read its
     * pixels, so it is not copied unless needed: getPixels refuses a HARDWARE bitmap, and an
     * unconditional copy held a second full-size frame alive at the peak of every remaster.
     */
    private fun blurredCopy(source: Bitmap, strength: Int): Bitmap {
        val divisor = strength.coerceIn(4, 36)
        val tiny = Bitmap.createScaledBitmap(source, (source.width / divisor).coerceAtLeast(2), (source.height / divisor).coerceAtLeast(2), true)
        val blurred = Bitmap.createScaledBitmap(tiny, source.width, source.height, true)
        if (tiny !== blurred) tiny.recycle()
        if (blurred.config == Bitmap.Config.ARGB_8888 && blurred !== source) return blurred
        return blurred.copy(Bitmap.Config.ARGB_8888, false).also { if (blurred !== source) blurred.recycle() }
    }

    private fun MarkupStroke.path(bitmap: Bitmap) = Path().apply {
        points.firstOrNull()?.let { first ->
            moveTo(first.x * bitmap.width, first.y * bitmap.height)
            points.drop(1).forEach { point -> lineTo(point.x * bitmap.width, point.y * bitmap.height) }
        }
    }

    private fun MarkupStroke.paint(bitmap: Bitmap): Paint {
        val stroke = this
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = bitmap.width.coerceAtMost(bitmap.height) * widthFraction
        }
    }

    suspend fun saveGenerativeCopy(
        item: MediaItem,
        generativeBitmap: Bitmap,
        saveAsCopy: Boolean = true,
        useNearLosslessCompression: Boolean = true,
    ): Uri = withContext(Dispatchers.IO) {
        if (!saveAsCopy) {
            backupManager.ensureBackup(item)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val base = item.displayName.substringBeforeLast('.', item.displayName)
        val suffix = if (saveAsCopy) "_generative_${System.currentTimeMillis() % 10000}" else ""
        val ext = if (useNearLosslessCompression) "webp" else "jpg"
        val mime = if (useNearLosslessCompression) "image/webp" else "image/jpeg"

        val destination = if (saveAsCopy) {
            context.contentResolver.insert(collection, ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${base}${suffix}.${ext}")
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PandaGallery Generative/")
                item.dateTaken?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }) ?: throw IOException("Unable to create generative image")
        } else {
            item.uri
        }

        val compressFormat = if (useNearLosslessCompression) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        } else {
            Bitmap.CompressFormat.JPEG
        }
        val quality = if (useNearLosslessCompression) 90 else 95

        try {
            context.contentResolver.openOutputStream(destination, if (saveAsCopy) "w" else "rwt")?.use { output ->
                if (!generativeBitmap.compress(compressFormat, quality, output)) {
                    throw IOException("Unable to encode generative image")
                }
            } ?: throw IOException("Unable to write generative image")

            if (saveAsCopy) {
                context.contentResolver.update(destination, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
        } catch (e: Exception) {
            if (saveAsCopy) {
                context.contentResolver.delete(destination, null, null)
            }
            throw e
        }

        destination
    }
}

/** Rows per remaster band: ~3 x 6000 x 256 x 4 bytes = 18 MB of heap at 6K width. */
private const val REMASTER_BAND_ROWS = 256
