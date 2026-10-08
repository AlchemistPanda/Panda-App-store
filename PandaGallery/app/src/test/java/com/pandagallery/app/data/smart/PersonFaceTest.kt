package com.pandagallery.app.data.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonFaceTest {

    private fun candidate(
        personKey: String,
        mediaId: Long,
        quality: Float,
        box: IntArray = intArrayOf(100, 200, 300, 440),
        sourceWidth: Int = 1000,
        sourceHeight: Int = 800,
    ) = PersonFaceCandidate(
        personKey = personKey,
        mediaId = mediaId,
        quality = quality,
        boxLeft = box[0],
        boxTop = box[1],
        boxRight = box[2],
        boxBottom = box[3],
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
    )

    @Test
    fun `the sharpest face represents the person`() {
        val faces = bestFacePerPerson(
            listOf(
                candidate("p:1", mediaId = 10, quality = .3f),
                candidate("p:1", mediaId = 20, quality = .9f),
                candidate("p:1", mediaId = 30, quality = .5f),
            )
        )

        assertEquals(20L, faces.getValue("p:1").mediaId)
    }

    @Test
    fun `each person gets their own face`() {
        val faces = bestFacePerPerson(
            listOf(
                candidate("p:1", mediaId = 10, quality = .9f),
                candidate("p:2", mediaId = 20, quality = .8f),
            )
        )

        assertEquals(setOf("p:1", "p:2"), faces.keys)
        assertEquals(10L, faces.getValue("p:1").mediaId)
        assertEquals(20L, faces.getValue("p:2").mediaId)
    }

    @Test
    fun `the box is converted to fractions of the image`() {
        val face = bestFacePerPerson(listOf(candidate("p:1", 10, .9f))).getValue("p:1")

        assertEquals(0.10f, face.left, 0.0001f)
        assertEquals(0.25f, face.top, 0.0001f)
        assertEquals(0.30f, face.right, 0.0001f)
        assertEquals(0.55f, face.bottom, 0.0001f)
    }

    /**
     * Rows written before the source frame was recorded cannot be mapped onto a
     * differently-sized decode. Skipping is right: a wrongly scaled box crops to an ear,
     * which reads as a broken feature rather than a missing one.
     */
    @Test
    fun `faces with no recorded frame are skipped rather than guessed`() {
        val faces = bestFacePerPerson(
            listOf(candidate("p:1", 10, .9f, sourceWidth = 0, sourceHeight = 0))
        )

        assertNull(faces["p:1"])
    }

    @Test
    fun `a legacy row does not hide a usable one for the same person`() {
        val faces = bestFacePerPerson(
            listOf(
                candidate("p:1", mediaId = 10, quality = .9f, sourceWidth = 0, sourceHeight = 0),
                candidate("p:1", mediaId = 20, quality = .4f),
            )
        )

        assertEquals(20L, faces.getValue("p:1").mediaId)
    }

    @Test
    fun `a degenerate box is rejected`() {
        val faces = bestFacePerPerson(
            listOf(candidate("p:1", 10, .9f, box = intArrayOf(300, 200, 300, 200)))
        )

        assertNull(faces["p:1"])
    }

    @Test
    fun `selection is deterministic when quality ties`() {
        val tied = listOf(
            candidate("p:1", mediaId = 30, quality = .7f),
            candidate("p:1", mediaId = 10, quality = .7f),
            candidate("p:1", mediaId = 20, quality = .7f),
        )

        assertEquals(10L, bestFacePerPerson(tied).getValue("p:1").mediaId)
        assertEquals(10L, bestFacePerPerson(tied.reversed()).getValue("p:1").mediaId)
    }

    @Test
    fun `no faces means no chips`() {
        assertTrue(bestFacePerPerson(emptyList()).isEmpty())
    }
}
