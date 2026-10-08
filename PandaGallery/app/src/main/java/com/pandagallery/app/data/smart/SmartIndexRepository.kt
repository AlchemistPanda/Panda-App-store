package com.pandagallery.app.data.smart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pandagallery.app.data.local.dao.PersonAssignmentRow
import com.pandagallery.app.data.local.dao.SmartMediaDao
import com.pandagallery.app.data.local.db.SmartSearchIndex
import com.pandagallery.app.data.local.entity.FaceEmbeddingEntity
import com.pandagallery.app.data.local.entity.SmartMediaIndexEntity
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.data.smart.face.FaceCrops
import com.pandagallery.app.data.smart.face.FaceEmbedder
import com.pandagallery.app.data.smart.face.FaceGeometry
import com.pandagallery.app.data.smart.face.FaceModelState
import com.pandagallery.app.data.smart.face.FaceObservation
import com.pandagallery.app.data.smart.face.FacePoint
import com.pandagallery.app.data.smart.face.FaceRef
import com.pandagallery.app.data.smart.face.clusterFaces
import com.pandagallery.app.data.smart.face.faceQuality
import com.pandagallery.app.data.smart.face.isUsableFace
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.search.SearchText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Builds and maintains the on-device index that powers search and people grouping.
 *
 * Both features share a single decode of each photo — decoding is far and away the most
 * expensive step, so OCR, labelling and face detection all run against the same bitmap.
 */
@Singleton
class SmartIndexRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val dao: SmartMediaDao,
    private val searchIndex: SmartSearchIndex,
    private val faceEmbedder: FaceEmbedder,
) {
    fun observeIndex(): Flow<List<SmartMediaIndexEntity>> = dao.observeAll()

    /** Cheap change token; search re-runs on this rather than on the index contents. */
    fun observeIndexVersion(): Flow<String> = dao.observeIndexVersion()

    /** Whether the face recognition model is present and loadable on this build. */
    suspend fun faceModelState(): FaceModelState = faceEmbedder.prepare()

    /**
     * Only one pass may run at a time.
     *
     * The manual "Build smart index" action and the background worker can both fire at
     * once - enabling a setting schedules the worker, and tapping build immediately after
     * starts a second pass over the same photos. Both then decode every file and race on
     * the same rows. Serialising costs nothing when uncontended.
     */
    private val indexMutex = Mutex()

    /**
     * Runs a full incremental pass.
     *
     * @param includeFaces when false, existing embeddings are left untouched but no new
     *   ones are produced. Turning it on later re-scans faces without redoing OCR.
     */
    suspend fun indexAll(
        includeFaces: Boolean,
        maxFullPassesPerRun: Int = DEFAULT_MAX_FULL_PASSES_PER_RUN,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): SmartIndexOutcome = withContext(Dispatchers.IO) {
        indexMutex.withLock { indexAllLocked(includeFaces, maxFullPassesPerRun, onProgress) }
    }

    private suspend fun indexAllLocked(
        includeFaces: Boolean,
        maxFullPassesPerRun: Int,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): SmartIndexOutcome {
        // Videos are indexed from a single representative frame. GIFs stay out: they are
        // animation, not content worth OCR-ing, and decoding them is disproportionately slow.
        val items = mediaRepository.getAllMediaSnapshot()
            .filter { !it.isTrashed && it.mimeType != "image/gif" }
        val prunedFaces = pruneMissing(items.mapTo(hashSetOf()) { it.id })

        // Photos whose text is indexed but which have no full-text row — the case after an
        // upgrade, where re-running ML Kit would be pure waste.
        val searchable = dao.searchDocumentIds().toHashSet()

        val faceModel = if (includeFaces) faceEmbedder.prepare() else null
        val facesUsable = faceModel is FaceModelState.Ready

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(.65f).build())
        val faceDetector = if (facesUsable) FaceDetection.getClient(faceDetectorOptions()) else null

        var facesChanged = prunedFaces
        var failures = 0
        var fullPassBudget = maxFullPassesPerRun
        var hasMoreWork = false
        try {
            items.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                val existing = dao.get(item.id)
                val needsFullPass = shouldReindex(
                    indexedDateModified = existing?.sourceDateModified,
                    currentDateModified = item.dateModified,
                    includeFaces = facesUsable,
                    facesIndexed = existing?.facesIndexed == true,
                )

                if (!needsFullPass) {
                    // Text is current. Only repair a missing full-text row.
                    if (existing != null && item.id !in searchable) {
                        writeSearchDocument(item, existing.recognizedText, existing.labels)
                    }
                    onProgress(index + 1, items.size)
                    return@forEachIndexed
                }

                // A first-time index of a large library can need a full ML pass on every item;
                // running all of them in one Worker execution risks WorkManager's execution time
                // limit. Once the budget for this run is spent, the rest are left for the next
                // run rather than racing that limit — cheap items above still get processed.
                if (fullPassBudget <= 0) {
                    hasMoreWork = true
                    onProgress(index + 1, items.size)
                    return@forEachIndexed
                }
                fullPassBudget--

                val bitmap = if (item.isVideo) decodeVideoFrame(item) else decode(item.uri)
                if (bitmap == null) {
                    onProgress(index + 1, items.size)
                    return@forEachIndexed
                }
                // ML Kit rejects anything under 32px on either edge. Icons, thumbnails and
                // videos whose dimensions MediaStore does not report all land here, and one
                // of them used to abort the entire pass. Record them as indexed-with-nothing
                // so they are skipped next run rather than re-decoded forever.
                if (bitmap.width < MIN_ML_DIMENSION || bitmap.height < MIN_ML_DIMENSION) {
                    bitmap.recycle()
                    dao.upsert(
                        SmartMediaIndexEntity(
                            mediaId = item.id,
                            sourceDateModified = item.dateModified,
                            recognizedText = "",
                            labels = "",
                            faceCount = 0,
                            indexedAt = System.currentTimeMillis(),
                            facesIndexed = facesUsable || existing?.facesIndexed == true,
                        )
                    )
                    writeSearchDocument(item, "", "")
                    onProgress(index + 1, items.size)
                    return@forEachIndexed
                }
                try {
                    val input = InputImage.fromBitmap(bitmap, 0)
                    val text = recognizer.process(input).await().text.take(MAX_TEXT_LENGTH)
                    val labels = labeler.process(input).await().joinToString(LABEL_SEPARATOR) { it.text }

                    // Faces are read from photos only. One frame is a weak sample of who is
                    // in a video, and feeding those embeddings into clustering costs more in
                    // cluster purity than it gains in coverage.
                    val faces = if (faceDetector != null && !item.isVideo) {
                        val detected = faceDetector.process(input).await()
                        val embeddings = embedFaces(item.id, bitmap, detected)
                        dao.deleteFacesFor(listOf(item.id))
                        if (embeddings.isNotEmpty()) dao.upsertFaces(embeddings)
                        facesChanged = true
                        embeddings.size
                    } else {
                        existing?.faceCount ?: 0
                    }

                    dao.upsert(
                        SmartMediaIndexEntity(
                            mediaId = item.id,
                            sourceDateModified = item.dateModified,
                            recognizedText = text,
                            labels = labels,
                            faceCount = faces,
                            indexedAt = System.currentTimeMillis(),
                            facesIndexed = facesUsable || existing?.facesIndexed == true,
                        )
                    )
                    writeSearchDocument(item, text, labels)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // A single malformed file should cost one photo, not the whole index.
                    failures++
                } finally {
                    bitmap.recycle()
                }
                onProgress(index + 1, items.size)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            recognizer.close()
            labeler.close()
            faceDetector?.close()
        }

        if (facesChanged) recluster()
        return SmartIndexOutcome(indexed = items.size - failures, skipped = failures, hasMoreWork = hasMoreWork)
    }

    /**
     * Recomputes people groups from the stored embeddings and persists the assignment.
     *
     * Clustering is a batch step rather than something the UI derives on the fly: the input
     * is every face in the library, and recomputing that on each database emission would
     * make scrolling the organizer screen quadratic in library size.
     */
    suspend fun recluster() = withContext(Dispatchers.Default) {
        val stored = dao.getAllFaces()
        if (stored.isEmpty()) {
            dao.replacePersonAssignments(emptyList())
            return@withContext
        }
        val observations = stored.map { entity ->
            FaceObservation(
                ref = FaceRef(entity.mediaId, entity.faceIndex),
                embedding = entity.embedding.toFloatArray(entity.dimensions),
                quality = entity.quality,
                previousKey = entity.personKey,
            )
        }
        val assignments = clusterFaces(observations).flatMap { cluster ->
            val qualityByRef = observations.associate { it.ref to it.quality }
            cluster.refs.map { ref ->
                PersonAssignmentRow(
                    mediaId = ref.mediaId,
                    faceIndex = ref.faceIndex,
                    personKey = cluster.key,
                    quality = qualityByRef[ref] ?: 0f,
                )
            }
        }
        dao.replacePersonAssignments(assignments)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clearAllSmartData()
        searchIndex.clear()
    }

    suspend fun clearFaces() = withContext(Dispatchers.IO) {
        dao.clearAllPeopleData()
    }

    // ------------------------------------------------------------------------ internals

    /**
     * Detects, filters, aligns and embeds every face in one photo.
     *
     * Faces that fail the quality gate are dropped rather than embedded at low confidence —
     * a bad embedding does not stay isolated, it pulls a cluster's centroid toward whatever
     * it happened to look like and drags strangers in behind it.
     */
    private suspend fun embedFaces(
        mediaId: Long,
        bitmap: Bitmap,
        faces: List<Face>,
    ): List<FaceEmbeddingEntity> = faces.mapIndexedNotNull { faceIndex, face ->
        val geometry = face.toGeometry(bitmap.width, bitmap.height)
        if (!isUsableFace(geometry)) return@mapIndexedNotNull null
        val crop = FaceCrops.alignedCrop(bitmap, geometry) ?: return@mapIndexedNotNull null
        val embedding = try {
            faceEmbedder.embed(crop)
        } finally {
            crop.recycle()
        } ?: return@mapIndexedNotNull null

        FaceEmbeddingEntity(
            mediaId = mediaId,
            faceIndex = faceIndex,
            embedding = embedding.toByteArray(),
            dimensions = embedding.size,
            quality = faceQuality(geometry),
            personKey = null,
            boxLeft = geometry.left,
            boxTop = geometry.top,
            boxRight = geometry.right,
            boxBottom = geometry.bottom,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
        )
    }

    /** Normalizes once, at write time, so the query side never has to guess the format. */
    private fun writeSearchDocument(item: MediaItem, text: String, labels: String) {
        searchIndex.upsert(
            mediaId = item.id,
            labels = SearchText.normalizeForIndex(labels.replace(LABEL_SEPARATOR, " ")),
            body = SearchText.normalizeForIndex(text),
            name = SearchText.normalizeForIndex(
                listOfNotNull(item.displayName, item.bucketName, item.relativePath).joinToString(" ")
            ),
        )
    }

    /**
     * Drops index rows for media that no longer exists.
     *
     * @return true when face rows were removed, so the caller knows to re-cluster - stale
     *   centroids otherwise keep describing people using photos that are already gone.
     */
    private suspend fun pruneMissing(liveIds: Set<Long>): Boolean {
        val staleIds = dao.getAllMediaIds().toSet() - liveIds
        if (staleIds.isEmpty()) return false
        staleIds.chunked(SQLITE_BIND_CHUNK).forEach { chunk ->
            dao.deleteMediaIds(chunk)
            dao.deleteFacesFor(chunk)
        }
        searchIndex.delete(staleIds)
        return true
    }

    /**
     * Pulls one frame out of a video to index.
     *
     * Sampled a third of the way in rather than at the start: openings are frequently black,
     * a title card, or a hand over the lens, none of which describe the video.
     */
    private fun decodeVideoFrame(item: MediaItem): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, item.uri)
            val durationMillis = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: item.duration
                ?: 0L
            val frameWidth = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: item.width
            val frameHeight = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: item.height
            val timestampMicros = (durationMillis * 1_000L) / SAMPLE_AT_FRACTION
            if (frameWidth <= 0 || frameHeight <= 0) {
                // MediaStore does not always report video dimensions. Asking for a scaled
                // frame here produced a 1x1 bitmap, which ML Kit then rejected; take the
                // native frame instead and shrink it afterwards if it is large.
                val native = retriever.getFrameAtTime(
                    timestampMicros,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: return null
                return native.scaledForIndexing()
            }
            val scale = minOf(1f, MAX_DIMENSION.toFloat() / maxOf(frameWidth, frameHeight))
            retriever.getScaledFrameAtTime(
                timestampMicros,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                (frameWidth * scale).toInt().coerceAtLeast(MIN_ML_DIMENSION),
                (frameHeight * scale).toInt().coerceAtLeast(MIN_ML_DIMENSION),
            )
        } catch (_: RuntimeException) {
            // setDataSource throws for DRM-protected and malformed files. One unreadable
            // video must not abort indexing for the whole library.
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decode(uri: Uri): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1f, MAX_DIMENSION.toFloat() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }
    } catch (_: IOException) {
        null
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }

    private companion object {
        /**
         * Decode ceiling. Faces are detected in this space, so it also sets the floor on how
         * small a face can be in frame and still clear [MIN_FACE_PIXELS].
         */
        const val MAX_DIMENSION = 1280
        const val MAX_TEXT_LENGTH = 20_000
        /** ASCII unit separator - cannot occur in an ML Kit label, so labels stay splittable. */
        const val LABEL_SEPARATOR = "\u001F"
        const val SQLITE_BIND_CHUNK = 900

        /** Sample point within a video, as a divisor of its duration. */
        const val SAMPLE_AT_FRACTION = 3L

        /** ML Kit's documented minimum input edge; anything smaller throws. */
        const val MIN_ML_DIMENSION = 32

        /**
         * How many items needing a full OCR/label/face pass one run will process before
         * yielding, so a large backlog spreads across several worker runs instead of risking
         * WorkManager's execution time limit in one.
         */
        const val DEFAULT_MAX_FULL_PASSES_PER_RUN = 400
    }
}

/**
 * ACCURATE mode plus landmarks: alignment needs eye positions, and the accuracy difference
 * between modes is exactly the difference between grouping a person correctly and not.
 * This runs in a constrained background worker, so the extra cost is acceptable.
 */
private fun faceDetectorOptions(): FaceDetectorOptions = FaceDetectorOptions.Builder()
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
    .setMinFaceSize(.08f)
    .build()

private fun Face.toGeometry(imageWidth: Int, imageHeight: Int) = FaceGeometry(
    left = boundingBox.left,
    top = boundingBox.top,
    right = boundingBox.right,
    bottom = boundingBox.bottom,
    imageWidth = imageWidth,
    imageHeight = imageHeight,
    headEulerY = headEulerAngleY,
    headEulerZ = headEulerAngleZ,
    leftEye = getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { FacePoint(it.x, it.y) },
    rightEye = getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { FacePoint(it.x, it.y) },
)

/** What one indexing pass managed to do. */
data class SmartIndexOutcome(
    val indexed: Int,
    val skipped: Int,
    /** True when the per-run budget was spent before the whole backlog was processed. */
    val hasMoreWork: Boolean = false,
)

/** Shrinks a natively-decoded video frame to the indexing ceiling. */
private fun Bitmap.scaledForIndexing(): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= 1280) return this
    val scale = 1280f / longest
    val scaled = Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    if (scaled !== this) recycle()
    return scaled
}

/** Little-endian float32, matching [toFloatArray]. */
internal fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    forEach(buffer::putFloat)
    return buffer.array()
}

internal fun ByteArray.toFloatArray(dimensions: Int): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(dimensions) { if (buffer.remaining() >= Float.SIZE_BYTES) buffer.float else 0f }
}

internal fun shouldReindex(
    indexedDateModified: Long?,
    currentDateModified: Long,
    includeFaces: Boolean,
    facesIndexed: Boolean,
): Boolean = indexedDateModified != currentDateModified || (includeFaces && !facesIndexed)
