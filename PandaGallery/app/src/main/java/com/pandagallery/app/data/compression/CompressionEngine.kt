package com.pandagallery.app.data.compression

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.exifinterface.media.ExifInterface
import androidx.heifwriter.AvifWriter
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.EncoderSelector
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.pandagallery.app.data.editing.ImageEditorEngine
import com.pandagallery.app.data.media.JpegSegments
import com.pandagallery.app.data.media.MotionPhotoHelper
import com.pandagallery.app.data.metadata.MediaMetadataRepository.Companion.EXIF_TAGS_TO_PRESERVE
import com.pandagallery.app.domain.compression.MAX_DECODE_EDGE
import com.pandagallery.app.domain.compression.decodeSampleSize
import com.pandagallery.app.domain.compression.encodeWithinBudget
import com.pandagallery.app.domain.compression.previewByteScale
import com.pandagallery.app.domain.compression.videoScaledDimensions
import com.pandagallery.app.domain.model.ImageFormat
import com.pandagallery.app.domain.model.MotionPhotoCompressionMode
import com.pandagallery.app.domain.model.UserPreferences
import com.pandagallery.app.domain.model.VideoCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

private data class ActiveVideoJob(
    val transformer: Transformer,
    val continuation: CancellableContinuation<File>,
    val output: File,
) {
    /**
     * `Transformer.cancel()` fires neither listener callback, and an exceptional resume does not
     * run `invokeOnCancellation`, so the partial output has to be deleted here — nothing else
     * would ever reach it.
     */
    fun stop() {
        transformer.cancel()
        output.delete()
        if (continuation.isActive) continuation.resumeWithException(CompressionStoppedException())
    }
}

@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class CompressionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageFormatSupport: ImageFormatSupport,
    private val imageEditorEngine: ImageEditorEngine,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    /**
     * Running exports only. A task still waiting on [videoTranscodeGate] or between codec attempts
     * has no entry: the processor cancels its encode coroutine on pause or delete, which aborts the
     * gate wait and the next attempt, and [exportVideo]'s invokeOnCancellation stops a running one.
     */
    private val activeVideoJobs = ConcurrentHashMap<String, ActiveVideoJob>()

    /** Anything in [vaultDir] older than this was left by an earlier process. */
    private val createdAtMillis = System.currentTimeMillis()

    /**
     * Only one video transcode at a time, regardless of how many queue workers are running.
     *
     * Each [Transformer] export holds a hardware decoder, a hardware encoder and a GL pipeline.
     * Running the queue's full concurrency against video multiplied graphics memory (measured at
     * ~190 MB of Gfx/EGL on an 8-core device) and the number of live codec instances, without
     * buying throughput — the hardware encoder is the bottleneck, so four exports finish in
     * roughly the time one does. The cost of that was a process fat enough for the OEM's low
     * memory killer to reclaim it mid-batch. Images are unaffected and still run in parallel.
     */
    private val videoTranscodeGate = Semaphore(1)

    /**
     * Caps how many full-resolution images are decoded at once. Each decoded photo is a native
     * bitmap of up to ~113 MB ([MAX_DECODE_EDGE] on the long edge), and every queue worker used
     * to hold one simultaneously — 2 to 10 of them, plus the encoder's working copies. That
     * pushed the process past a gigabyte during batch compression, where the low-memory killer
     * took it down mid-batch. One decode at a time on ordinary phones, two on devices with lots
     * of RAM.
     */
    private val imageDecodeGate = Semaphore(if (totalDeviceRamBytes() >= 10L * 1024 * 1024 * 1024) 2 else 1)

    /** Work directory for encode outputs until the queue publishes them. Holds nothing durable. */
    private val vaultDir: File by lazy {
        File(context.filesDir, "MemoriesVault").apply {
            if (!exists()) mkdirs()
        }
    }

    data class CompressionPreviewResult(
        val originalBitmap: Bitmap,
        val compressedBitmap: Bitmap,
        val originalSizeBytes: Long,
        val compressedSizeBytes: Long,
        val qualityScore: PerceptualQualityEstimator.QualityScore,
        val savingsPercentage: Int,
        val contentComplexity: PerceptualQualityEstimator.ContentComplexity? = null,
    )

    /**
     * Fast real-time compression preview generation for the interactive Before/After split slider.
     * Generates an in-memory comparison buffer and calculates live SSIM without writing to disk.
     *
     * Checks for cancellation between stages: the Studio cancels a preview whenever a setting
     * changes, and without these checks the abandoned decode/encode/SSIM ran to completion
     * alongside the new one.
     */
    suspend fun generateCompressionPreview(
        imageUri: Uri,
        format: ImageFormat,
        quality: Int,
        maxPreviewDimension: Int = 1920,
        remasterDetailLevel: Float = 0f,
    ): CompressionPreviewResult = withContext(Dispatchers.IO) {
        val originalSize = statSize(imageUri)
        val preview = decodePreviewBitmap(imageUri, maxPreviewDimension)
        val originalBitmap = preview.bitmap
        var bitmapToCompress: Bitmap? = null
        var compressedBitmap: Bitmap? = null
        try {
            ensureActive()
            val working = prepareForEncode(originalBitmap, format, remasterDetailLevel, recycleSource = false)
            bitmapToCompress = working
            ensureActive()

            val byteArrayOutputStream = ByteArrayOutputStream()
            val contentComplexity = PerceptualQualityEstimator.classifyContent(working)
            val effectiveQuality = if (quality < 0) {
                PerceptualQualityEstimator.recommendAdaptiveQuality(contentComplexity)
            } else {
                quality
            }

            when (format) {
                ImageFormat.AVIF -> {
                    if (imageFormatSupport.isAvifEncodingSupported) {
                        byteArrayOutputStream.write(encodeAvifToBytes(working, effectiveQuality))
                    } else {
                        working.compress(Bitmap.CompressFormat.WEBP_LOSSY, effectiveQuality, byteArrayOutputStream)
                    }
                }
                ImageFormat.WEBP -> {
                    val isLossless = effectiveQuality >= 100 || (quality < 0 && contentComplexity == PerceptualQualityEstimator.ContentComplexity.FLAT_GRAPHIC)
                    val compressFormat = if (isLossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
                    working.compress(compressFormat, effectiveQuality, byteArrayOutputStream)
                }
                ImageFormat.JPEG -> {
                    working.compress(Bitmap.CompressFormat.JPEG, effectiveQuality, byteArrayOutputStream)
                }
            }
            ensureActive()

            val compressedBytes = byteArrayOutputStream.toByteArray()
            val compressed = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                ?: working.copy(working.config ?: Bitmap.Config.ARGB_8888, false)
            compressedBitmap = compressed
            if (working !== originalBitmap) working.recycle()
            bitmapToCompress = null
            ensureActive()

            val qualityScore = PerceptualQualityEstimator.estimateQuality(originalBitmap, compressed)
            val byteScale = previewByteScale(preview.sourceWidth, preview.sourceHeight, originalBitmap.width, originalBitmap.height)
            val estimatedCompressedSize = (compressedBytes.size * byteScale).toLong()
                .coerceAtLeast(compressedBytes.size.toLong())

            val effectiveOrigSize = if (originalSize > 0L) originalSize else (estimatedCompressedSize * 1.5).toLong()
            val savings = if (effectiveOrigSize > 0L) {
                (((effectiveOrigSize - estimatedCompressedSize).toDouble() / effectiveOrigSize) * 100).toInt().coerceIn(0, 99)
            } else 0

            CompressionPreviewResult(
                originalBitmap = originalBitmap,
                compressedBitmap = compressed,
                originalSizeBytes = effectiveOrigSize,
                compressedSizeBytes = estimatedCompressedSize,
                qualityScore = qualityScore,
                savingsPercentage = savings,
                contentComplexity = contentComplexity,
            )
        } catch (error: Throwable) {
            // Cancelled or failed: none of these reach the UI, so free them now rather than
            // leaving several preview-sized bitmaps per abandoned run to the GC.
            bitmapToCompress?.takeIf { it !== originalBitmap }?.recycle()
            compressedBitmap?.recycle()
            originalBitmap.recycle()
            throw error
        }
    }

    /**
     * Real-time preview generation for Target File Size mode.
     * Iteratively determines optimal compression quality to target the specified byte budget.
     */
    suspend fun generateTargetSizePreview(
        imageUri: Uri,
        targetSizeBytes: Long,
        format: ImageFormat = ImageFormat.JPEG,
        maxPreviewDimension: Int = 1920,
        remasterDetailLevel: Float = 0f,
    ): CompressionPreviewResult = withContext(Dispatchers.IO) {
        val originalSize = statSize(imageUri)
        val preview = decodePreviewBitmap(imageUri, maxPreviewDimension)
        val originalBitmap = preview.bitmap
        var bitmapToCompress: Bitmap? = null
        var compressedBitmap: Bitmap? = null
        try {
            ensureActive()
            val working = prepareForEncode(originalBitmap, format, remasterDetailLevel, recycleSource = false)
            bitmapToCompress = working

            val byteScale = previewByteScale(preview.sourceWidth, preview.sourceHeight, originalBitmap.width, originalBitmap.height)
            val previewTargetBytes = (targetSizeBytes / byteScale).toLong().coerceAtLeast(10_000L)

            // AVIF is searched against WebP because seven AvifWriter passes would cost up to seven
            // 30-second encodes on a control that re-runs whenever the slider moves. The chosen
            // quality is then encoded as real AVIF once below, so the size and the pixels the user
            // is shown come from the encoder that will actually write the file — reporting the WebP
            // figures, as this did before, described a different file from the one being saved.
            val useAvif = format == ImageFormat.AVIF && imageFormatSupport.isAvifEncodingSupported
            val searchFormat = searchCompressFormat(format)
            val chosen = encodeWithinBudget(previewTargetBytes) { quality ->
                ensureActive()
                ByteArrayOutputStream().also { working.compress(searchFormat, quality, it) }.toByteArray()
            }
            var bestBytes = chosen.bytes

            if (useAvif) {
                ensureActive()
                val avifBytes = runCatching { encodeAvifToBytes(working, chosen.quality) }.getOrNull()
                if (avifBytes != null && avifBytes.isNotEmpty()) bestBytes = avifBytes
            }
            ensureActive()

            val compressed = BitmapFactory.decodeByteArray(bestBytes, 0, bestBytes.size)
                ?: working.copy(working.config ?: Bitmap.Config.ARGB_8888, false)
            compressedBitmap = compressed
            if (working !== originalBitmap) working.recycle()
            bitmapToCompress = null
            ensureActive()

            val qualityScore = PerceptualQualityEstimator.estimateQuality(originalBitmap, compressed)
            val contentComplexity = PerceptualQualityEstimator.classifyContent(originalBitmap)

            val estimatedCompressedSize = (bestBytes.size * byteScale).toLong()
                .coerceAtLeast(bestBytes.size.toLong())

            val effectiveOrigSize = if (originalSize > 0L) originalSize else (estimatedCompressedSize * 1.5).toLong()
            val savings = if (effectiveOrigSize > 0L) {
                (((effectiveOrigSize - estimatedCompressedSize).toDouble() / effectiveOrigSize) * 100).toInt().coerceIn(0, 99)
            } else 0

            CompressionPreviewResult(
                originalBitmap = originalBitmap,
                compressedBitmap = compressed,
                originalSizeBytes = effectiveOrigSize,
                compressedSizeBytes = estimatedCompressedSize,
                qualityScore = qualityScore,
                savingsPercentage = savings,
                contentComplexity = contentComplexity,
            )
        } catch (error: Throwable) {
            bitmapToCompress?.takeIf { it !== originalBitmap }?.recycle()
            compressedBitmap?.recycle()
            originalBitmap.recycle()
            throw error
        }
    }

    /** Encodes [bitmap] as AVIF in a cache temp file and returns the bytes. */
    private fun encodeAvifToBytes(bitmap: Bitmap, quality: Int): ByteArray {
        val tempFile = File.createTempFile("avif_prev_", ".avif", context.cacheDir)
        return try {
            writeAvif(bitmap, quality, tempFile)
            tempFile.readBytes()
        } finally {
            tempFile.delete()
        }
    }

    private fun writeAvif(bitmap: Bitmap, quality: Int, destFile: File, exif: ByteArray? = null) {
        AvifWriter.Builder(
            destFile.absolutePath,
            bitmap.width,
            bitmap.height,
            AvifWriter.INPUT_MODE_BITMAP,
        )
            .setQuality(quality)
            .setMaxImages(1)
            .build()
            .use { writer ->
                writer.start()
                writer.addBitmap(bitmap)
                if (exif != null) writer.addExifData(0, exif, 0, exif.size)
                writer.stop(AVIF_ENCODING_TIMEOUT_MS)
            }
    }

    private fun statSize(uri: Uri): Long = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    }.getOrDefault(0L)

    private class PreviewDecode(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int)

    /**
     * ImageDecoder applies EXIF orientation (all eight cases) and keeps gainmaps itself, which
     * the hand-written BitmapFactory + matrix version this replaces did only partly.
     */
    private fun decodePreviewBitmap(imageUri: Uri, maxDim: Int): PreviewDecode {
        var sourceWidth = 0
        var sourceHeight = 0
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, imageUri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            sourceWidth = info.size.width
            sourceHeight = info.size.height
            val sampleSize = decodeSampleSize(sourceWidth, sourceHeight, maxDim)
            if (sampleSize > 1) decoder.setTargetSampleSize(sampleSize)
        }
        return PreviewDecode(bitmap, sourceWidth, sourceHeight)
    }

    /**
     * The bitmap that actually gets encoded: remastered if asked, and flattened onto white when
     * the target format has no alpha channel. JPEG and AvifWriter's bitmap input drop alpha, and
     * transparent pixels are stored premultiplied as (0,0,0,0) — so a transparent PNG logo came
     * out on solid black.
     */
    private fun prepareForEncode(
        source: Bitmap,
        format: ImageFormat,
        remasterDetailLevel: Float,
        recycleSource: Boolean,
    ): Bitmap {
        var bitmap = source
        if (remasterDetailLevel > 0f) {
            bitmap = imageEditorEngine.applyRemaster(bitmap, remasterDetailLevel, recycleSource)
        }
        if (format != ImageFormat.WEBP && bitmap.hasAlpha()) {
            val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(opaque).apply {
                drawColor(Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            opaque.setHasAlpha(false)
            if (bitmap !== source || recycleSource) bitmap.recycle()
            bitmap = opaque
        }
        return bitmap
    }

    /** Search stand-in per format. AVIF is searched as WebP and re-encoded for real once. */
    private fun searchCompressFormat(format: ImageFormat): Bitmap.CompressFormat = when (format) {
        ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ImageFormat.WEBP, ImageFormat.AVIF -> Bitmap.CompressFormat.WEBP_LOSSY
    }

    private fun newWorkFile(extension: String) = File(vaultDir, "${UUID.randomUUID()}.$extension")

    private fun ImageFormat.fileExtension() = when (this) {
        ImageFormat.WEBP -> "webp"
        ImageFormat.JPEG -> "jpg"
        ImageFormat.AVIF -> "avif"
    }

    /**
     * The motion photo clip to carry into the output, or null when there is none or the user
     * strips clips. A clip can only be kept in a JPEG (the Motion Photo container is a JPEG with
     * the MP4 appended), so any other output format is refused rather than silently dropping
     * the video — with "Replace original" the original, and the clip with it, would otherwise
     * end up deleted.
     */
    private suspend fun motionClipToKeep(imageUri: Uri, preferences: UserPreferences, replacesOriginal: Boolean): ByteArray? {
        if (preferences.motionPhotoCompressionMode != MotionPhotoCompressionMode.PRESERVE_CLIP) return null
        val clip = MotionPhotoHelper.extractMotionVideoBytes(context, imageUri)?.takeIf { it.isNotEmpty() } ?: return null
        if (preferences.imageFormat == ImageFormat.JPEG) return clip
        // A copy leaves the original, clip included, untouched, so only the copy loses its clip.
        if (!replacesOriginal) return null
        error(
            "This is a motion photo, and its video clip can only be kept in JPEG. " +
                "Choose JPEG, or set motion photos to Auto-Strip, to compress it."
        )
    }

    /**
     * Compress an image to the Memories Vault using user preferences.
     * Returns the compressed File.
     */
    suspend fun compressImage(
        imageUri: Uri,
        preferences: UserPreferences,
        remasterDetailLevel: Float = 0f,
        onProgress: (Int) -> Unit = {},
        replacesOriginal: Boolean = true,
    ): File = imageDecodeGate.withPermit {
        compressImageUngated(imageUri, preferences, remasterDetailLevel, onProgress, replacesOriginal)
    }

    private suspend fun compressImageUngated(
        imageUri: Uri,
        preferences: UserPreferences,
        remasterDetailLevel: Float,
        onProgress: (Int) -> Unit,
        replacesOriginal: Boolean,
    ): File = withContext(Dispatchers.IO) {
        val motionVideoBytes = motionClipToKeep(imageUri, preferences, replacesOriginal)

        // Lossless promises every pixel, so it may not be downsampled to fit the decode cap.
        val isLosslessTask = preferences.imageFormat == ImageFormat.WEBP && preferences.imageQuality >= 100
        val decoded = decodeImageBitmap(imageUri, allowDownscale = !isLosslessTask)
        onProgress(25)

        val bitmap = try {
            prepareForEncode(decoded, preferences.imageFormat, remasterDetailLevel, recycleSource = true)
        } catch (error: Throwable) {
            decoded.recycle()
            throw error
        }
        onProgress(45)

        val destFile = newWorkFile(preferences.imageFormat.fileExtension())
        try {
            val complexity = PerceptualQualityEstimator.classifyContent(bitmap)
            val effectiveQuality = if (preferences.imageQuality < 0) {
                PerceptualQualityEstimator.recommendAdaptiveQuality(complexity)
            } else {
                preferences.imageQuality
            }
            when (preferences.imageFormat) {
                ImageFormat.AVIF -> {
                    require(imageFormatSupport.isAvifEncodingSupported) {
                        "AVIF encoding is not supported on this device"
                    }
                    writeAvif(bitmap, effectiveQuality, destFile, exif = buildExifBlock(imageUri))
                }
                ImageFormat.WEBP, ImageFormat.JPEG -> {
                    val isLossless = effectiveQuality >= 100 || (preferences.imageQuality < 0 && complexity == PerceptualQualityEstimator.ContentComplexity.FLAT_GRAPHIC)
                    val format = when (preferences.imageFormat) {
                        ImageFormat.WEBP ->
                            if (isLossless) {
                                Bitmap.CompressFormat.WEBP_LOSSLESS
                            } else {
                                Bitmap.CompressFormat.WEBP_LOSSY
                            }
                        ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                        ImageFormat.AVIF -> error("Handled above")
                    }
                    BufferedOutputStream(FileOutputStream(destFile), 64 * 1024).use { outputStream ->
                        check(bitmap.compress(format, effectiveQuality, outputStream)) {
                            "Image encoder could not write the compressed file"
                        }
                        outputStream.flush()
                    }
                    copyExifMetadata(imageUri, destFile)
                }
            }
            keepMotionClip(destFile, motionVideoBytes)
            // A pause or delete that landed during the encode must not hand back a file the
            // processor will never receive; throwing here sends it through the delete below.
            ensureActive()
            onProgress(100)
            destFile
        } catch (error: Throwable) {
            destFile.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun keepMotionClip(destFile: File, motionVideoBytes: ByteArray?) {
        if (motionVideoBytes == null) return
        check(MotionPhotoHelper.appendMotionVideo(destFile, motionVideoBytes)) {
            "Could not keep the motion photo's video clip"
        }
    }

    /**
     * Compress an image to fit within a target byte budget.
     *
     * Binary-searches encoder quality so the output is as good as the budget allows; when even
     * the lowest quality overshoots, the smallest attempt is kept (the queue still declines to
     * replace anything that does not save space). AVIF is searched against WebP and then
     * written for real with [AvifWriter]: running seven AvifWriter passes would cost up to seven
     * 30-second encodes, and AVIF lands comfortably below WebP at the same quality, so the
     * budget still holds.
     *
     * A motion photo's clip is appended after the search, so the budget covers the still.
     * ponytail: subtract the clip from the budget if users expect the whole file to fit.
     */
    suspend fun compressImageToTargetSize(
        imageUri: Uri,
        targetSizeBytes: Long,
        preferences: UserPreferences,
        remasterDetailLevel: Float = 0f,
        onProgress: (Int) -> Unit = {},
        replacesOriginal: Boolean = true,
    ): File = imageDecodeGate.withPermit {
        compressImageToTargetSizeUngated(imageUri, targetSizeBytes, preferences, remasterDetailLevel, onProgress, replacesOriginal)
    }

    private suspend fun compressImageToTargetSizeUngated(
        imageUri: Uri,
        targetSizeBytes: Long,
        preferences: UserPreferences,
        remasterDetailLevel: Float,
        onProgress: (Int) -> Unit,
        replacesOriginal: Boolean,
    ): File = withContext(Dispatchers.IO) {
        onProgress(5)
        // Writing WebP search bytes into a .avif file produced something no AVIF decoder opens.
        if (preferences.imageFormat == ImageFormat.AVIF) {
            require(imageFormatSupport.isAvifEncodingSupported) { "AVIF encoding is not supported on this device" }
        }
        val motionVideoBytes = motionClipToKeep(imageUri, preferences, replacesOriginal)
        val decoded = decodeImageBitmap(imageUri, allowDownscale = true)
        val bitmap = try {
            prepareForEncode(decoded, preferences.imageFormat, remasterDetailLevel, recycleSource = true)
        } catch (error: Throwable) {
            decoded.recycle()
            throw error
        }
        onProgress(25)

        val destFile = newWorkFile(preferences.imageFormat.fileExtension())
        try {
            val searchFormat = searchCompressFormat(preferences.imageFormat)
            val chosen = encodeWithinBudget(
                budgetBytes = targetSizeBytes,
                onAttempt = { attempt -> onProgress(25 + attempt * 10) },
            ) { quality ->
                ensureActive()
                ByteArrayOutputStream().also { bitmap.compress(searchFormat, quality, it) }.toByteArray()
            }

            if (preferences.imageFormat == ImageFormat.AVIF) {
                writeAvif(bitmap, chosen.quality, destFile, exif = buildExifBlock(imageUri))
            } else {
                destFile.writeBytes(chosen.bytes)
                copyExifMetadata(imageUri, destFile)
            }
            keepMotionClip(destFile, motionVideoBytes)
            // A pause or delete that landed during the encode must not hand back a file the
            // processor will never receive; throwing here sends it through the delete below.
            ensureActive()
            onProgress(100)
            destFile
        } catch (error: Throwable) {
            destFile.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Copies the preserved EXIF tags (date, offset, GPS, camera) from [sourceUri] onto the
     * JPEG/PNG/WebP [destFile].
     *
     * Failures are not swallowed: a copy that silently lost its capture date and location sorts
     * as "today", drops off the map, and with "Replace original" goes on to replace the photo
     * that still had them. Failing the task keeps the original instead. A source whose EXIF
     * can't be read still gets its date from MediaStore.
     */
    private fun copyExifMetadata(sourceUri: Uri, destFile: File) {
        val sourceExif = runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { stream -> ExifInterface(stream) }
        }.getOrNull()

        val destExif = ExifInterface(destFile.absolutePath)

        if (sourceExif != null) {
            for (tag in EXIF_TAGS_TO_PRESERVE) {
                val value = sourceExif.getAttribute(tag)
                if (value != null) {
                    destExif.setAttribute(tag, value)
                }
            }
        }

        // Ensure orientation on the compressed file is set to normal (1)
        // because the pixels were already decoded and stored in upright orientation
        destExif.setAttribute(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL.toString(),
        )

        val sourceDateMillis by lazy { resolveDateMillis(sourceUri) }

        // If capture date/time was missing in source EXIF, try resolving it from MediaStore or file
        if (destExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == null) {
            val dateMillis = sourceDateMillis
            if (dateMillis != null && dateMillis > 0L) {
                val formatter = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getDefault()
                }
                val formattedDate = formatter.format(java.util.Date(dateMillis))
                destExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formattedDate)
                destExif.setAttribute(ExifInterface.TAG_DATETIME, formattedDate)
                destExif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, formattedDate)
            }
        }

        // EXIF times are wall-clock with no zone. MediaProvider only trusts one that has no
        // OffsetTimeOriginal when it falls within a day of the file's mtime — and a freshly
        // written copy's mtime is "now", so an old photo's date was thrown away and the copy
        // sorted as new. Recording the offset makes the capture time stand on its own.
        if (destExif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) == null) {
            destExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { original ->
                exifUtcOffset(original, sourceDateMillis)?.let { offset ->
                    destExif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
                    if (destExif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED) == null) {
                        destExif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
                    }
                    if (destExif.getAttribute(ExifInterface.TAG_OFFSET_TIME) == null) {
                        destExif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
                    }
                }
            }
        }

        destExif.saveAttributes()
    }

    /**
     * The EXIF block for an AVIF output, in the "Exif\0\0"+TIFF form AvifWriter embeds.
     *
     * ExifInterface can only save JPEG, PNG and WebP: on an .avif it threw, the error was
     * swallowed, and every AVIF went out with no date, offset or GPS. So the tags are written by
     * the same [copyExifMetadata] into a throwaway 1x1 JPEG, and its APP1 payload is handed to
     * the writer.
     * ponytail: confirm on device that MediaProvider reads DATE_TAKEN/GPS from the AVIF Exif
     * item; if it does not, block "Replace original" for AVIF.
     */
    private fun buildExifBlock(sourceUri: Uri): ByteArray {
        val carrier = File.createTempFile("exif_", ".jpg", context.cacheDir)
        try {
            val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            try {
                FileOutputStream(carrier).use { check(pixel.compress(Bitmap.CompressFormat.JPEG, 50, it)) }
            } finally {
                pixel.recycle()
            }
            copyExifMetadata(sourceUri, carrier)
            return checkNotNull(JpegSegments.exifPayload(carrier.readBytes())) {
                "Could not prepare the photo's EXIF metadata for AVIF"
            }
        } finally {
            carrier.delete()
        }
    }

    /**
     * The UTC offset ("+05:30") that turns the wall-clock [exifDateTime] into the instant the
     * original is filed under in MediaStore, so the copy lands on exactly the same DATE_TAKEN.
     * Falls back to the device zone when there is no such instant or it is not the same moment.
     */
    private fun exifUtcOffset(exifDateTime: String, capturedAtMillis: Long?): String? {
        val wallClockAsUtc = runCatching {
            java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(exifDateTime.trim())?.time
        }.getOrNull() ?: return null
        val quarterHour = 15 * 60_000L
        val offsetMillis = capturedAtMillis
            ?.let { wallClockAsUtc - it }
            ?.takeIf { kotlin.math.abs(it) < 24 * 60 * 60_000L }
            ?.let { Math.round(it.toDouble() / quarterHour) * quarterHour }
            ?: java.util.TimeZone.getDefault().getOffset(capturedAtMillis ?: wallClockAsUtc).toLong()
        val totalMinutes = kotlin.math.abs(offsetMillis) / 60_000L
        val sign = if (offsetMillis < 0) "-" else "+"
        return String.format(java.util.Locale.US, "%s%02d:%02d", sign, totalMinutes / 60, totalMinutes % 60)
    }

    private fun resolveDateMillis(uri: Uri): Long? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    android.provider.MediaStore.MediaColumns.DATE_TAKEN,
                    android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
                    android.provider.MediaStore.MediaColumns.DATE_ADDED,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val takenIdx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_TAKEN)
                    if (takenIdx >= 0) {
                        val taken = cursor.getLong(takenIdx)
                        if (taken > 0L) return@use taken
                    }

                    val modifiedIdx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                    if (modifiedIdx >= 0) {
                        val modified = cursor.getLong(modifiedIdx)
                        if (modified > 0L) {
                            return@use if (modified < 10_000_000_000L) modified * 1000L else modified
                        }
                    }

                    val addedIdx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_ADDED)
                    if (addedIdx >= 0) {
                        val added = cursor.getLong(addedIdx)
                        if (added > 0L) {
                            return@use if (added < 10_000_000_000L) added * 1000L else added
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun totalDeviceRamBytes(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem
    }

    /** Sample size for a full decode, refusing any downscale when [allowDownscale] is false. */
    private fun fullDecodeSampleSize(width: Int, height: Int, allowDownscale: Boolean): Int {
        val sampleSize = decodeSampleSize(width, height)
        if (sampleSize > 1 && !allowDownscale) throw LosslessDownscaleException(width, height)
        return sampleSize
    }

    /**
     * Decodes at most [MAX_DECODE_EDGE] on the long edge — the same fixed cap the estimator
     * models (see [MAX_DECODE_EDGE] for why it no longer follows free RAM).
     *
     * Only decode failures fall back to BitmapFactory. An OutOfMemoryError propagates and fails
     * the task with the original kept: the fallback used to repeat the identical allocation,
     * which mostly just ran out of memory a second time and hid the real error.
     */
    private fun decodeImageBitmap(imageUri: Uri, allowDownscale: Boolean): Bitmap {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, imageUri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val sampleSize = fullDecodeSampleSize(info.size.width, info.size.height, allowDownscale)
                if (sampleSize > 1) {
                    decoder.setTargetSampleSize(sampleSize)
                }
            }
        } catch (_: IOException) {
            decodeWithBitmapFactoryAndExif(imageUri, allowDownscale)
        } catch (_: IllegalArgumentException) {
            decodeWithBitmapFactoryAndExif(imageUri, allowDownscale)
        }
    }

    private fun decodeWithBitmapFactoryAndExif(imageUri: Uri, allowDownscale: Boolean): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        withRequiredInputStream(
            open = { context.contentResolver.openInputStream(imageUri) },
            errorMessage = "Cannot open image input stream",
        ) {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot decode image bounds" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = fullDecodeSampleSize(bounds.outWidth, bounds.outHeight, allowDownscale)
        }
        val rawBitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BufferedInputStream(stream, 32 * 1024).use { buffered ->
                BitmapFactory.decodeStream(buffered, null, options)
            }
        } ?: throw IllegalArgumentException("Cannot decode image bitmap")

        val orientation = runCatching {
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }

        if (matrix.isIdentity) {
            return rawBitmap
        }

        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        if (rotated !== rawBitmap) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (rawBitmap.hasGainmap()) {
                    val gainmap = rawBitmap.gainmap
                    if (gainmap != null && !rotated.hasGainmap()) {
                        rotated.gainmap = gainmap
                    }
                }
            }
            rawBitmap.recycle()
        }
        return rotated
    }

    /**
     * Deletes encode outputs left in the work directory by a process that died mid-task (the
     * low-memory killer, a force-stop, a reboot). Their tasks are re-queued under new file
     * names, so nothing would ever reach these files again — they piled up, often hundreds of
     * MB of partial video, in storage the system never reclaims.
     *
     * Only this engine's own UUID-named outputs older than this process are touched, so a file
     * a running task is about to publish is never at risk.
     */
    suspend fun deleteStaleWorkFiles(): Int = withContext(Dispatchers.IO) {
        vaultDir.listFiles { file ->
            file.isFile && WORK_FILE_NAME.matches(file.name) && file.lastModified() < createdAtMillis
        }.orEmpty().count { it.delete() }
    }

    /**
     * Compress a video to the Memories Vault using user preferences and GPU acceleration.
     * Returns the compressed File.
     */
    suspend fun compressVideo(
        videoUri: Uri,
        preferences: UserPreferences,
        taskId: String = UUID.randomUUID().toString(),
        onProgress: (Float) -> Unit = {}
    ): File {
        val preferredMimeType = when (preferences.videoCodec) {
            VideoCodec.H264 -> MimeTypes.VIDEO_H264
            VideoCodec.H265 -> MimeTypes.VIDEO_H265
            VideoCodec.AV1 -> MimeTypes.VIDEO_AV1
        }
        // MediaMetadataRetriever parses the container through the content provider, which can
        // take hundreds of ms on a large or SD-card file; it used to run on the main thread.
        val scaledSize = withContext(Dispatchers.IO) { displaySizeOf(videoUri) }
            ?.let { (width, height) -> videoScaledDimensions(preferences.videoResolution, width, height) }
        return videoTranscodeGate.withPermit {
            compressVideoExclusively(videoUri, preferences, taskId, preferredMimeType, scaledSize, onProgress)
        }
    }

    /**
     * Exports with the preferred codec, falling back AV1 -> H.265 -> H.264 only when the failure
     * is the codec's: the encoder would not start, failed mid-stream, rejected the format, or
     * wrote an unplayable file. A source that can't be read or a full disk fails the same way on
     * every codec, and retrying used to re-run the whole transcode up to three times.
     */
    private suspend fun compressVideoExclusively(
        videoUri: Uri,
        preferences: UserPreferences,
        taskId: String,
        preferredMimeType: String,
        scaledSize: Pair<Int, Int>?,
        onProgress: (Float) -> Unit,
    ): File {
        val codecChain = when (preferredMimeType) {
            MimeTypes.VIDEO_AV1 -> listOf(MimeTypes.VIDEO_AV1, MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264)
            MimeTypes.VIDEO_H265 -> listOf(MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264)
            else -> listOf(MimeTypes.VIDEO_H264)
        }
        for ((index, mimeType) in codecChain.withIndex()) {
            try {
                return exportPlayableVideo(taskId, videoUri, scaledSize, mimeType, preferences.useGpu, onProgress)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (stopped: CompressionStoppedException) {
                throw stopped
            } catch (error: Throwable) {
                if (index == codecChain.lastIndex || !isCodecSpecificFailure(error)) throw error
            }
        }
        error("Codec chain is never empty")
    }

    private fun isCodecSpecificFailure(error: Throwable): Boolean {
        val codecFailure = error is InvalidVideoOutputException ||
            (error is ExportException && error.errorCode in CODEC_FALLBACK_ERROR_CODES)
        // A muxer failure on a nearly full disk would only fail again with the next codec.
        return codecFailure && vaultDir.usableSpace > LOW_STORAGE_BYTES
    }

    /** Exports, then checks the result is playable — off the main thread, since that parses the whole file. */
    private suspend fun exportPlayableVideo(
        taskId: String,
        videoUri: Uri,
        scaledSize: Pair<Int, Int>?,
        videoMimeType: String,
        useHardwareEncoder: Boolean,
        onProgress: (Float) -> Unit,
    ): File {
        val output = exportVideo(taskId, videoUri, scaledSize, videoMimeType, useHardwareEncoder, onProgress)
        val playable = try {
            withContext(Dispatchers.IO) { isPlayableVideo(output) }
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
        if (!playable) {
            output.delete()
            throw InvalidVideoOutputException()
        }
        onProgress(100f)
        return output
    }

    private suspend fun exportVideo(
        taskId: String,
        videoUri: Uri,
        scaledSize: Pair<Int, Int>?,
        videoMimeType: String,
        useHardwareEncoder: Boolean,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val output = newWorkFile("mp4")
            // The resolution setting is a ceiling on the short edge; anything already within it
            // has no scaling effect at all (see videoScaledDimensions). The size is exact and
            // even, so stretching to it neither letterboxes nor distorts.
            val videoEffects: List<Effect> = scaledSize
                ?.let { (width, height) -> listOf(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_STRETCH_TO_FIT)) }
                ?: emptyList()
            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
            // With hardware acceleration off only software encoders are offered, and they have
            // no 10-bit HDR profiles — so an HDR clip failed on every codec. Tone-map it to SDR
            // instead. With it on, Media3 already falls back to tone-mapping when the hardware
            // encoder can't keep HDR.
            val composition = Composition.Builder(EditedMediaItemSequence(editedMediaItem))
                .setHdrMode(
                    if (useHardwareEncoder) {
                        Composition.HDR_MODE_KEEP_HDR
                    } else {
                        Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
                    },
                )
                .build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(videoMimeType)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setVideoEncoderSelector(videoEncoderSelector(useHardwareEncoder))
                        .build()
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        activeVideoJobs.remove(taskId)
                        if (continuation.isActive) {
                            // If the caller is cancelled before it receives the file, delete it.
                            continuation.resume(output) { _, file, _ -> file.delete() }
                        } else {
                            output.delete()
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        activeVideoJobs.remove(taskId)
                        output.delete()
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                .build()
            activeVideoJobs[taskId] = ActiveVideoJob(transformer, continuation, output)
            val progressHolder = ProgressHolder()
            val progressPoller = object : Runnable {
                override fun run() {
                    if (!continuation.isActive || activeVideoJobs[taskId]?.transformer !== transformer) return
                    if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress.toFloat())
                    }
                    mainHandler.postDelayed(this, 400L)
                }
            }
            continuation.invokeOnCancellation {
                mainHandler.post {
                    if (activeVideoJobs[taskId]?.transformer === transformer) {
                        transformer.cancel()
                        activeVideoJobs.remove(taskId)
                    }
                    output.delete()
                }
            }
            transformer.start(composition, output.absolutePath)
            mainHandler.post(progressPoller)
        }
    }

    /**
     * Honours the "Hardware acceleration" setting.
     *
     * On (the default) this is Media3's own choice, which prefers the device's hardware encoder.
     * Off restricts the selection to software encoders — slower, but it sidesteps OEM hardware
     * encoders that produce artefacts or refuse particular resolutions, which is the only reason
     * to turn the setting off at all.
     *
     * The filter falls back to the unrestricted list when it would otherwise select nothing:
     * plenty of devices ship no software encoder for HEVC or AV1, and returning an empty list
     * there fails the export outright rather than degrading to the hardware one.
     */
    private fun videoEncoderSelector(useHardwareEncoder: Boolean): EncoderSelector {
        if (useHardwareEncoder) return EncoderSelector.DEFAULT
        return EncoderSelector { mimeType ->
            val all = EncoderSelector.DEFAULT.selectEncoderInfos(mimeType)
            val software = all.filterNot(MediaCodecInfo::isHardwareAccelerated)
            if (software.isEmpty()) all else ImmutableList.copyOf(software)
        }
    }

    /**
     * The video's width and height as *displayed*, i.e. with rotation metadata applied.
     *
     * A portrait clip recorded by a phone is usually stored landscape with a 90-degree rotation
     * flag. Media3 effects run on the presentation frame, so this is the size the scaling
     * effect's output has to be expressed in.
     *
     * Null when the source cannot be read, which means "don't scale" — the safe direction,
     * since it can only ever skip a downscale rather than introduce an upscale. Blocking I/O:
     * call off the main thread.
     */
    private fun displaySizeOf(videoUri: Uri): Pair<Int, Int>? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, videoUri)
            val codedWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val codedHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) codedHeight to codedWidth else codedWidth to codedHeight
        }
    }.getOrNull()

    fun cancelVideo(taskId: String) {
        mainHandler.post { activeVideoJobs.remove(taskId)?.stop() }
    }

    fun cancelCurrentVideo() {
        mainHandler.post {
            val jobs = activeVideoJobs.values.toList()
            activeVideoJobs.clear()
            jobs.forEach(ActiveVideoJob::stop)
        }
    }

    private fun isPlayableVideo(file: File): Boolean {
        if (!file.isFile || file.length() < 1_024L) return false
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                duration > 0L && hasVideo.equals("yes", ignoreCase = true)
            }
        }.getOrDefault(false)
    }

    private companion object {
        const val AVIF_ENCODING_TIMEOUT_MS = 30_000L

        /** Below this much free space a muxing failure is treated as a full disk, not a codec fault. */
        const val LOW_STORAGE_BYTES = 256L * 1024 * 1024

        val CODEC_FALLBACK_ERROR_CODES = setOf(
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
            ExportException.ERROR_CODE_ENCODING_FAILED,
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_MUXING_FAILED,
        )

        /** `<uuid>.<ext>`, as [newWorkFile] names them. */
        val WORK_FILE_NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|webp|avif|mp4)")
    }
}

internal inline fun withRequiredInputStream(
    open: () -> InputStream?,
    errorMessage: String,
    block: (InputStream) -> Unit,
) {
    val input = open() ?: throw IllegalArgumentException(errorMessage)
    input.use(block)
}

private class CompressionStoppedException : RuntimeException("Compression task paused or deleted")

/** A codec wrote a file that doesn't play; the next codec in the chain may do better. */
private class InvalidVideoOutputException : IllegalStateException("Video encoder produced an invalid or empty output")

private class LosslessDownscaleException(width: Int, height: Int) : IllegalStateException(
    "Lossless keeps every pixel, but this ${width}x$height photo is larger than the $MAX_DECODE_EDGE px " +
        "the compressor can hold in memory. The original was kept.",
)
