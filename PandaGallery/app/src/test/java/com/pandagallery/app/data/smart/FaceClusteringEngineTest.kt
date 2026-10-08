package com.pandagallery.app.data.smart

import com.pandagallery.app.data.local.dao.FaceGroupDao
import com.pandagallery.app.data.local.entity.FaceEmbeddingEntity
import com.pandagallery.app.data.local.entity.FaceGroupEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceClusteringEngineTest {

    private lateinit var fakeDao: FakeFaceGroupDao
    private lateinit var engine: FaceClusteringEngine

    @Before
    fun setUp() {
        fakeDao = FakeFaceGroupDao()
        engine = FaceClusteringEngine(fakeDao)
    }

    private fun packEmbedding(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @Test
    fun cosineDistance_identicalVectors_returnsZero() {
        val v1 = floatArrayOf(1f, 0f, 0f)
        val v2 = floatArrayOf(1f, 0f, 0f)
        val dist = engine.cosineDistance(v1, v2)
        assertEquals(0f, dist, 0.001f)
    }

    @Test
    fun cosineDistance_orthogonalVectors_returnsOne() {
        val v1 = floatArrayOf(1f, 0f, 0f)
        val v2 = floatArrayOf(0f, 1f, 0f)
        val dist = engine.cosineDistance(v1, v2)
        assertEquals(1f, dist, 0.001f)
    }

    @Test
    fun unpackEmbedding_restoresFloatsCorrectly() {
        val original = floatArrayOf(0.12f, -0.45f, 0.99f)
        val packed = packEmbedding(original)
        val unpacked = engine.unpackEmbedding(packed, original.size)
        assertArrayEquals(original, unpacked, 0.0001f)
    }

    @Test
    fun clusterFaces_groupsCloseEmbeddingsTogether() = runBlocking {
        // Two close vectors for person A
        val faceA1 = FaceEmbeddingEntity(
            mediaId = 1L, faceIndex = 0, embedding = packEmbedding(floatArrayOf(1.0f, 0.1f, 0.0f)),
            dimensions = 3, quality = 0.9f, personKey = "alice",
            boxLeft = 0, boxTop = 0, boxRight = 10, boxBottom = 10
        )
        val faceA2 = FaceEmbeddingEntity(
            mediaId = 2L, faceIndex = 0, embedding = packEmbedding(floatArrayOf(0.99f, 0.12f, 0.0f)),
            dimensions = 3, quality = 0.85f, personKey = "alice",
            boxLeft = 0, boxTop = 0, boxRight = 10, boxBottom = 10
        )

        // One far vector (outlier/noise)
        val faceOutlier = FaceEmbeddingEntity(
            mediaId = 3L, faceIndex = 0, embedding = packEmbedding(floatArrayOf(0.0f, 0.0f, 1.0f)),
            dimensions = 3, quality = 0.7f, personKey = "stranger",
            boxLeft = 0, boxTop = 0, boxRight = 10, boxBottom = 10
        )

        val groups = engine.clusterFaces(listOf(faceA1, faceA2, faceOutlier), eps = 0.2f, minPts = 2)
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].faceCount)
        assertEquals("alice", groups[0].personKey)
    }

    private class FakeFaceGroupDao : FaceGroupDao {
        val savedGroups = mutableListOf<FaceGroupEntity>()

        override fun observeActiveGroups(): Flow<List<FaceGroupEntity>> = flowOf(savedGroups)

        override suspend fun getGroupByPersonKey(personKey: String): FaceGroupEntity? =
            savedGroups.find { it.personKey == personKey }

        override suspend fun insertOrUpdate(group: FaceGroupEntity): Long {
            savedGroups.add(group)
            return savedGroups.size.toLong()
        }

        override suspend fun insertAll(groups: List<FaceGroupEntity>) {
            savedGroups.addAll(groups)
        }

        override suspend fun updateName(personKey: String, name: String?, updatedAt: Long) {
            val idx = savedGroups.indexOfFirst { it.personKey == personKey }
            if (idx >= 0) savedGroups[idx] = savedGroups[idx].copy(name = name, updatedAt = updatedAt)
        }

        override suspend fun updatePinned(personKey: String, isPinned: Boolean) {
            val idx = savedGroups.indexOfFirst { it.personKey == personKey }
            if (idx >= 0) savedGroups[idx] = savedGroups[idx].copy(isPinned = isPinned)
        }

        override suspend fun dismissGroup(personKey: String) {
            val idx = savedGroups.indexOfFirst { it.personKey == personKey }
            if (idx >= 0) savedGroups[idx] = savedGroups[idx].copy(isDismissed = true)
        }

        override suspend fun deleteGroup(personKey: String) {
            savedGroups.removeAll { it.personKey == personKey }
        }
    }
}
