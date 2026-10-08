package com.pandagallery.app.data.cleanup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.pandagallery.app.data.compression.CompressionQueueRepository
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.local.dao.BackupDao
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.domain.model.DuplicateKeepRule
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class CleanupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val compressionQueueRepository: CompressionQueueRepository,
    private val backupDao: BackupDao,
    private val preferencesDataSource: PreferencesDataSource,
) {
    /**
     * The exact-duplicate groups from the most recent scan. Held separately from the report
     * because auto-resolve needs the full group membership, not just the flattened ids.
     */
    @Volatile
    private var lastExactGroups: List<List<CleanupCandidate>> = emptyList()

    private val _state = MutableStateFlow<CleanupState>(CleanupState.NotScanned)
    val state: StateFlow<CleanupState> = _state.asStateFlow()

    suspend fun scan() = withContext(Dispatchers.IO) {
        _state.value = CleanupState.Scanning
        try {
            val media = mediaRepository.getAllMediaSnapshot()
            val repeatedSizes = media.groupingBy { it.size }.eachCount().filterValues { it > 1 }.keys
            val candidates = ArrayList<CleanupCandidate>(media.size)
            for (item in media) {
                coroutineContext.ensureActive()
                val exactHash = if (item.size in repeatedSizes) contentHash(item) else null
                val bitmap = if (item.isImage && item.mimeType != "image/gif") decodePreview(item) else null
                candidates += CleanupCandidate(
                    id = item.id,
                    size = item.size,
                    exactHash = exactHash,
                    perceptualHash = bitmap?.let(::averageHash),
                    blurScore = bitmap?.let(::laplacianVariance),
                    takenAt = item.sortDate,
                )
                bitmap?.recycle()
            }
            val exactGroups = exactDuplicateGroups(candidates)
            lastExactGroups = exactGroups
            val exactIds = exactGroups.flatten().mapTo(mutableSetOf()) { it.id }

            // Originals with a known relationship — a compressed copy or a backup record — are
            // an exact fact, not a guess, so they're pulled out before the perceptual-hash pass
            // rather than left to also turn up (redundantly, and less confidently) as "similar".
            val compressedLinks = compressionQueueRepository.compressedCopyMediaIds(media)
            val backupRootUri = preferencesDataSource.userPreferencesFlow.first().backupFolderUri
            val backedUpOriginalIds = backupRootUri?.let { rootUri ->
                val recordsByMediaId = backupDao.getAll(rootUri).associateBy { it.mediaId }
                media.filter { item ->
                    val record = recordsByMediaId[item.id]
                    record != null && record.sourceDateModified == item.dateModified && record.sourceSize == item.size
                }.mapTo(mutableSetOf()) { it.id }
            }.orEmpty()
            val compressedBackedUpIds = compressedLinks.keys + backedUpOriginalIds
            val knownRelationshipIds = compressedBackedUpIds + compressedLinks.values

            val remainingCandidates = candidates.filterNot { it.id in exactIds || it.id in knownRelationshipIds }
            val similarGroups = similarMediaGroups(remainingCandidates)
            // Bursts are matched before general similarity claims their frames, so a
            // sequence is offered as one burst rather than scattered across both lists.
            val burstCandidates = burstGroups(remainingCandidates)
            val burstIds = burstCandidates.flatten().mapTo(mutableSetOf()) { it.id }
            val sizesById = media.associate { it.id to it.size }
            val report = CleanupReport(
                exactDuplicateIds = exactIds,
                duplicateGroupCount = exactGroups.size,
                similarGroupCount = similarGroups.size,
                redundantDuplicateIds = redundantIds(exactGroups),
                redundantSimilarIds = redundantIds(similarGroups),
                similarIds = similarGroups.flatten().mapTo(mutableSetOf()) { it.id },
                burstIds = burstIds,
                burstGroupCount = burstCandidates.size,
                redundantBurstIds = redundantBurstIds(burstCandidates),
                burstSavingsBytes = estimatedBurstSavings(burstCandidates),
                blurryIds = candidates.filter { it.blurScore != null && it.blurScore < BLUR_THRESHOLD }.mapTo(mutableSetOf()) { it.id },
                screenshotIds = media.filter { it.isScreenshot() }.mapTo(mutableSetOf()) { it.id },
                documentIds = media.filter { it.isDocumentLike() }.mapTo(mutableSetOf()) { it.id },
                largeFileIds = media.filter { it.size >= LARGE_FILE_BYTES }.mapTo(mutableSetOf()) { it.id },
                compressedBackedUpIds = compressedBackedUpIds,
                duplicateSavingsBytes = estimatedDuplicateSavings(exactGroups),
                similarSavingsBytes = estimatedDuplicateSavings(similarGroups),
                compressedBackedUpSavingsBytes = compressedBackedUpIds.sumOf { id -> sizesById[id] ?: 0L },
                sizesById = sizesById,
            )
            _state.value = CleanupState.Ready(report)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            _state.value = CleanupState.Failed(error.message ?: "Unable to read part of the media library")
        } catch (error: SecurityException) {
            _state.value = CleanupState.Failed("Media access was lost")
        }
    }

    /**
     * Moves every redundant byte-identical copy to Trash, keeping one per group by [rule].
     *
     * @return how many items were trashed. Requires a completed [scan].
     */
    suspend fun autoResolveExactDuplicates(
        rule: DuplicateKeepRule,
        retentionDays: Int,
    ): Int = withContext(Dispatchers.IO) {
        val doomed = duplicatesToTrash(lastExactGroups, rule)
        if (doomed.isEmpty()) return@withContext 0
        val media = mediaRepository.getAllMediaSnapshot()
            .filter { it.id in doomed && !it.isTrashed }
        media.forEach { mediaRepository.trashItem(it, retentionDays) }
        media.size
    }

    fun idsFor(category: CleanupCategory): Set<Long> =
        (state.value as? CleanupState.Ready)?.report?.idsFor(category).orEmpty()

    /** Ids safe to pre-select: duplicates minus the best copy in each group. */
    fun redundantIdsFor(category: CleanupCategory): Set<Long> =
        (state.value as? CleanupState.Ready)?.report?.redundantIdsFor(category).orEmpty()

    fun groupCountFor(category: CleanupCategory): Int =
        (state.value as? CleanupState.Ready)?.report?.groupCountFor(category) ?: 0

    private fun contentHash(item: MediaItem): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(item.uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: return null
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun decodePreview(item: MediaItem): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(HASH_SIZE, HASH_SIZE)
        }
    } catch (_: IOException) {
        null
    } catch (_: ImageDecoder.DecodeException) {
        null
    }

    private fun averageHash(bitmap: Bitmap): Long {
        val values = IntArray(HASH_SIZE * HASH_SIZE)
        var total = 0L
        var index = 0
        for (y in 0 until HASH_SIZE) for (x in 0 until HASH_SIZE) {
            val pixel = bitmap.getPixel(x, y)
            val luminance = ((pixel shr 16 and 0xff) * 299 + (pixel shr 8 and 0xff) * 587 + (pixel and 0xff) * 114) / 1000
            values[index++] = luminance
            total += luminance
        }
        val average = total / values.size
        var hash = 0L
        values.forEachIndexed { bit, value -> if (value >= average) hash = hash or (1L shl bit) }
        return hash
    }

    private fun laplacianVariance(bitmap: Bitmap): Double {
        val samples = Array(HASH_SIZE) { IntArray(HASH_SIZE) }
        for (y in 0 until HASH_SIZE) for (x in 0 until HASH_SIZE) {
            val pixel = bitmap.getPixel(x, y)
            samples[y][x] = ((pixel shr 16 and 0xff) + (pixel shr 8 and 0xff) + (pixel and 0xff)) / 3
        }
        val edges = mutableListOf<Int>()
        for (y in 1 until HASH_SIZE - 1) for (x in 1 until HASH_SIZE - 1) {
            edges += 4 * samples[y][x] - samples[y - 1][x] - samples[y + 1][x] - samples[y][x - 1] - samples[y][x + 1]
        }
        val mean = edges.average()
        return edges.sumOf { value -> (value - mean) * (value - mean) } / edges.size
    }

    private fun MediaItem.isScreenshot() =
        bucketName?.contains("screenshot", true) == true || relativePath?.contains("screenshot", true) == true

    private fun MediaItem.isDocumentLike(): Boolean {
        val searchable = "$displayName ${bucketName.orEmpty()} ${relativePath.orEmpty()}"
        return listOf("document", "receipt", "invoice", "scan").any { searchable.contains(it, true) }
    }

    private companion object {
        const val HASH_SIZE = 8
        const val BLUR_THRESHOLD = 120.0
        const val LARGE_FILE_BYTES = 50L * 1024L * 1024L
    }
}

sealed interface CleanupState {
    data object NotScanned : CleanupState
    data object Scanning : CleanupState
    data class Ready(val report: CleanupReport) : CleanupState
    data class Failed(val message: String) : CleanupState
}

data class CleanupReport(
    val exactDuplicateIds: Set<Long>,
    val duplicateGroupCount: Int = 0,
    val similarGroupCount: Int = 0,
    val redundantDuplicateIds: Set<Long> = emptySet(),
    val redundantSimilarIds: Set<Long> = emptySet(),
    val similarIds: Set<Long>,
    val burstIds: Set<Long> = emptySet(),
    val burstGroupCount: Int = 0,
    val redundantBurstIds: Set<Long> = emptySet(),
    val burstSavingsBytes: Long = 0,
    val blurryIds: Set<Long>,
    val screenshotIds: Set<Long>,
    val documentIds: Set<Long>,
    val largeFileIds: Set<Long>,
    /**
     * Originals that already have a live compressed copy, a matching backup record, or both —
     * an exact fact read from the compression/backup records, not a perceptual-hash guess. Safe
     * to pre-select for removal, unlike [blurryIds]/[screenshotIds]/[documentIds].
     */
    val compressedBackedUpIds: Set<Long> = emptySet(),
    val duplicateSavingsBytes: Long,
    val similarSavingsBytes: Long,
    val compressedBackedUpSavingsBytes: Long = 0,
    val sizesById: Map<Long, Long>,
) {
    fun idsFor(category: CleanupCategory): Set<Long> = when (category) {
        CleanupCategory.EXACT_DUPLICATES -> exactDuplicateIds
        CleanupCategory.SIMILAR -> similarIds
        CleanupCategory.BURSTS -> burstIds
        CleanupCategory.BLURRY -> blurryIds
        CleanupCategory.SCREENSHOTS -> screenshotIds
        CleanupCategory.DOCUMENTS -> documentIds
        CleanupCategory.LARGE_FILES -> largeFileIds
        CleanupCategory.COMPRESSED_BACKED_UP -> compressedBackedUpIds
    }

    /** Duplicates and near-duplicates can be pre-selected; the rest need human review. */
    fun redundantIdsFor(category: CleanupCategory): Set<Long> = when (category) {
        CleanupCategory.EXACT_DUPLICATES -> redundantDuplicateIds
        CleanupCategory.SIMILAR -> redundantSimilarIds
        CleanupCategory.BURSTS -> redundantBurstIds
        // Every id here is confirmed (not guessed) to already exist elsewhere as a compressed
        // copy or a backup, so the whole set is safe to pre-select — same confidence level as
        // an exact duplicate.
        CleanupCategory.COMPRESSED_BACKED_UP -> compressedBackedUpIds
        else -> emptySet()
    }

    fun groupCountFor(category: CleanupCategory): Int = when (category) {
        CleanupCategory.EXACT_DUPLICATES -> duplicateGroupCount
        CleanupCategory.SIMILAR -> similarGroupCount
        CleanupCategory.BURSTS -> burstGroupCount
        else -> 0
    }

    fun potentialBytes(category: CleanupCategory): Long = when (category) {
        CleanupCategory.EXACT_DUPLICATES -> duplicateSavingsBytes
        CleanupCategory.SIMILAR -> similarSavingsBytes
        CleanupCategory.BURSTS -> burstSavingsBytes
        CleanupCategory.COMPRESSED_BACKED_UP -> compressedBackedUpSavingsBytes
        else -> idsFor(category).sumOf { sizesById[it] ?: 0L }
    }
}

@kotlinx.serialization.Serializable
enum class CleanupCategory {
    EXACT_DUPLICATES, SIMILAR, BURSTS, BLURRY, SCREENSHOTS, DOCUMENTS, LARGE_FILES,
    COMPRESSED_BACKED_UP,
}
