package com.pandagallery.app.data.smart.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FaceClusteringTest {

    @Test
    fun `faces of the same person land in one group`() {
        val clusters = clusterFaces(
            listOf(
                observation(1, identity = 0f, jitter = 0f),
                observation(2, identity = 0f, jitter = .05f),
                observation(3, identity = 0f, jitter = -.04f),
            )
        )

        assertEquals(1, clusters.size)
        assertEquals(setOf(1L, 2L, 3L), clusters.single().mediaIds)
    }

    @Test
    fun `different people stay apart`() {
        val clusters = clusterFaces(
            listOf(
                observation(1, identity = 0f),
                observation(2, identity = 0f, jitter = .03f),
                observation(3, identity = 1.4f),
                observation(4, identity = 1.4f, jitter = .03f),
            )
        )

        assertEquals(2, clusters.size)
        assertEquals(
            listOf(setOf(1L, 2L), setOf(3L, 4L)),
            clusters.map { it.mediaIds }.sortedBy { it.min() },
        )
    }

    @Test
    fun `a person seen once is not a group`() {
        val clusters = clusterFaces(listOf(observation(1, identity = 0f)))

        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `merge pass reunites a person split by an unrepresentative seed`() {
        // Two sub-groups close enough that their centroids collapse, but whose individual
        // members were assigned before the centroids had settled.
        val clusters = clusterFaces(
            listOf(
                observation(1, identity = 0f, quality = .9f),
                observation(2, identity = .05f, quality = .8f),
                observation(3, identity = .60f, quality = .7f),
                observation(4, identity = .62f, quality = .6f),
                observation(5, identity = .30f, quality = .5f),
            ),
            sameThreshold = .95f,
            mergeThreshold = .80f,
        )

        assertEquals(1, clusters.size)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), clusters.single().mediaIds)
    }

    @Test
    fun `a named group keeps its key when new photos of that person arrive`() {
        val existing = listOf(
            observation(1, identity = 0f, previousKey = "p:1:0"),
            observation(2, identity = 0f, jitter = .04f, previousKey = "p:1:0"),
        )
        val withNewPhoto = existing + observation(3, identity = 0f, jitter = .06f, previousKey = null)

        val clusters = clusterFaces(withNewPhoto)

        assertEquals(1, clusters.size)
        // Same key means the user's chosen name still points at this group.
        assertEquals("p:1:0", clusters.single().key)
        assertEquals(setOf(1L, 2L, 3L), clusters.single().mediaIds)
    }

    @Test
    fun `two groups inheriting the same key do not collide`() {
        val clusters = clusterFaces(
            listOf(
                observation(1, identity = 0f, previousKey = "p:shared"),
                observation(2, identity = 0f, jitter = .03f, previousKey = "p:shared"),
                observation(3, identity = 1.4f, previousKey = "p:shared"),
                observation(4, identity = 1.4f, jitter = .03f, previousKey = "p:shared"),
            )
        )

        assertEquals(2, clusters.size)
        assertNotEquals(clusters[0].key, clusters[1].key)
    }

    @Test
    fun `clustering is deterministic regardless of input order`() {
        val faces = listOf(
            observation(1, identity = 0f, quality = .5f),
            observation(2, identity = 0f, jitter = .04f, quality = .9f),
            observation(3, identity = 1.4f, quality = .7f),
            observation(4, identity = 1.4f, jitter = .02f, quality = .3f),
        )

        val forwards = clusterFaces(faces).map { it.key to it.mediaIds }
        val backwards = clusterFaces(faces.reversed()).map { it.key to it.mediaIds }

        assertEquals(forwards, backwards)
    }

    /**
     * Synthetic embeddings on the unit circle of a 2-D plane inside a higher-dimensional
     * space. [identity] is the angle in radians, so two observations that differ only by a
     * small [jitter] are highly similar, and a large angular gap is a different person.
     */
    private fun observation(
        mediaId: Long,
        identity: Float,
        jitter: Float = 0f,
        quality: Float = .8f,
        previousKey: String? = null,
        faceIndex: Int = 0,
    ): FaceObservation {
        val angle = identity + jitter
        val embedding = FloatArray(8)
        embedding[0] = cos(angle)
        embedding[1] = sin(angle)
        return FaceObservation(
            ref = FaceRef(mediaId, faceIndex),
            embedding = l2Normalize(embedding),
            quality = quality,
            previousKey = previousKey,
        )
    }
}
