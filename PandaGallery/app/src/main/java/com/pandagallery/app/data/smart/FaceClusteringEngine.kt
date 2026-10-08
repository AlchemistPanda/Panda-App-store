package com.pandagallery.app.data.smart

import com.pandagallery.app.data.local.dao.FaceGroupDao
import com.pandagallery.app.data.local.entity.FaceEmbeddingEntity
import com.pandagallery.app.data.local.entity.FaceGroupEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * DBSCAN-based clustering engine for facial embeddings to power Samsung Gallery-style People Album.
 */
@Singleton
class FaceClusteringEngine @Inject constructor(
    private val faceGroupDao: FaceGroupDao,
) {
    /**
     * Unpacks float array from little-endian byte array.
     */
    fun unpackEmbedding(bytes: ByteArray, dimensions: Int): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(dimensions)
        for (i in 0 until dimensions) {
            if (buffer.remaining() >= 4) {
                floats[i] = buffer.float
            }
        }
        return floats
    }

    /**
     * Computes cosine distance (1.0 - cosine_similarity) between two normalized vectors.
     */
    fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        if (denom <= 0.00001f) return 1f
        val similarity = (dot / denom).coerceIn(-1f, 1f)
        return (1f - similarity).coerceAtLeast(0f)
    }

    /**
     * Runs DBSCAN clustering over detected face embeddings.
     * @param eps Distance threshold (typically 0.40 - 0.48 for face embeddings).
     * @param minPts Minimum points to form a dense core cluster.
     */
    suspend fun clusterFaces(
        faces: List<FaceEmbeddingEntity>,
        eps: Float = 0.45f,
        minPts: Int = 2,
    ): List<FaceGroupEntity> = withContext(Dispatchers.Default) {
        if (faces.isEmpty()) return@withContext emptyList()

        val unpackedVectors = faces.map { unpackEmbedding(it.embedding, it.dimensions) }
        val n = faces.size
        val visited = BooleanArray(n)
        val clusterAssignments = IntArray(n) { -1 } // -1 = noise/unassigned
        var currentClusterId = 0

        for (i in 0 until n) {
            if (visited[i]) continue
            visited[i] = true

            val neighbors = findNeighbors(i, unpackedVectors, eps)
            if (neighbors.size < minPts) {
                clusterAssignments[i] = -1 // Noise
            } else {
                clusterAssignments[i] = currentClusterId
                val queue = ArrayDeque(neighbors)
                while (queue.isNotEmpty()) {
                    val qIndex = queue.removeFirst()
                    if (!visited[qIndex]) {
                        visited[qIndex] = true
                        val qNeighbors = findNeighbors(qIndex, unpackedVectors, eps)
                        if (qNeighbors.size >= minPts) {
                            queue.addAll(qNeighbors)
                        }
                    }
                    if (clusterAssignments[qIndex] == -1) {
                        clusterAssignments[qIndex] = currentClusterId
                    }
                }
                currentClusterId++
            }
        }

        // Group into FaceGroupEntity entries
        val groups = mutableListOf<FaceGroupEntity>()
        for (cId in 0 until currentClusterId) {
            val clusterFaces = faces.filterIndexed { index, _ -> clusterAssignments[index] == cId }
            if (clusterFaces.isNotEmpty()) {
                val representativeFace = clusterFaces.maxByOrNull { it.quality } ?: clusterFaces.first()
                val personKey = representativeFace.personKey ?: "person_cluster_$cId"
                groups.add(
                    FaceGroupEntity(
                        personKey = personKey,
                        name = null,
                        coverMediaId = representativeFace.mediaId,
                        faceCount = clusterFaces.size,
                        clusterId = cId,
                        isPinned = false,
                        isDismissed = false,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        faceGroupDao.insertAll(groups)
        groups
    }

    private fun findNeighbors(targetIdx: Int, vectors: List<FloatArray>, eps: Float): List<Int> {
        val target = vectors[targetIdx]
        val neighbors = mutableListOf<Int>()
        for (i in vectors.indices) {
            if (cosineDistance(target, vectors[i]) <= eps) {
                neighbors.add(i)
            }
        }
        return neighbors
    }
}
