package com.pandagallery.app.data.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The corrections a user makes to a face group: removing photos that are not them, choosing the
 * cover, pinning, and getting a group back after "not a person".
 */
class PersonGroupEditsTest {
    private val people = listOf(
        SmartGroup(SmartGroupType.PEOPLE, "p:1", "Person 1", "3 photos", setOf(1L, 2L, 3L)),
        SmartGroup(SmartGroupType.PEOPLE, "p:2", "Person 2", "2 photos", setOf(4L, 5L)),
    )

    @Test
    fun `a removed photo leaves the group`() {
        val groups = applyPeopleGroupPreferences(people, emptyList(), mapOf("p:1" to setOf(2L)))

        assertEquals(setOf(1L, 3L), groups.first { it.key == "p:1" }.mediaIds)
    }

    @Test
    fun `removing every photo removes the group itself`() {
        val groups = applyPeopleGroupPreferences(people, emptyList(), mapOf("p:2" to setOf(4L, 5L)))

        assertTrue(groups.none { it.key == "p:2" })
    }

    /**
     * The removal is recorded against the group the user was looking at, which after a merge is
     * the destination — so it has to be applied once the merge has been resolved, not before.
     */
    @Test
    fun `a removal applies to a merged group`() {
        val groups = applyPeopleGroupPreferences(
            people,
            listOf(PersonGroupPreference("p:2", null, mergedIntoKey = "p:1")),
            mapOf("p:1" to setOf(4L)),
        )

        assertEquals(setOf(1L, 2L, 3L, 5L), groups.single { it.type == SmartGroupType.PEOPLE }.mediaIds)
    }

    @Test
    fun `a pinned person comes before a larger group`() {
        val groups = applyPeopleGroupPreferences(
            people,
            listOf(PersonGroupPreference("p:2", "Mum", null, isPinned = true)),
        )

        assertEquals("p:2", groups.first().key)
    }

    @Test
    fun `visible groups are numbered without gaps left by hidden ones`() {
        val groups = applyPeopleGroupPreferences(
            people,
            listOf(PersonGroupPreference("p:1", null, null, isDismissed = true)),
        )

        assertEquals(listOf("Person 1"), groups.filter { it.type == SmartGroupType.PEOPLE }.map(SmartGroup::title))
    }

    @Test
    fun `a dismissed group is offered back by name, or as an unnamed one`() {
        val preferences = listOf(
            PersonGroupPreference("p:1", "Dad", null, isDismissed = true),
            PersonGroupPreference("p:2", null, null, isDismissed = true),
        )

        val hidden = hiddenPeopleGroups(people, preferences)

        assertEquals(listOf("Dad", UNNAMED_PERSON_TITLE), hidden.map(SmartGroup::title))
        // And they stay out of the grid, which is the whole point of dismissing one.
        assertTrue(applyPeopleGroupPreferences(people, preferences).none { it.type == SmartGroupType.PEOPLE })
    }

    @Test
    fun `merges are followed to the group that is actually displayed`() {
        val byKey = listOf(
            PersonGroupPreference("p:1", null, mergedIntoKey = "p:2"),
            PersonGroupPreference("p:2", null, mergedIntoKey = "p:3"),
        ).associateBy(PersonGroupPreference::groupKey)

        assertEquals("p:3", canonicalPersonKey("p:1", byKey))
    }

    /** A merge chain that loops back on itself must not spin; the data comes from user edits. */
    @Test
    fun `a merge loop stops at the key that would close it`() {
        val byKey = listOf(
            PersonGroupPreference("a", null, mergedIntoKey = "b"),
            PersonGroupPreference("b", null, mergedIntoKey = "a"),
        ).associateBy(PersonGroupPreference::groupKey)

        assertEquals("b", canonicalPersonKey("a", byKey))
        assertEquals("a", canonicalPersonKey("b", byKey))
    }

    @Test
    fun `the chosen cover wins over the best-scoring face`() {
        val faces = bestFacePerPerson(candidates, covers = mapOf("p:1" to 2L))

        assertEquals(2L, faces.getValue("p:1").mediaId)
    }

    @Test
    fun `without a choice the best-scoring face represents the person`() {
        assertEquals(1L, bestFacePerPerson(candidates).getValue("p:1").mediaId)
    }

    /** A group cannot be shown by a photo the user just told us is not them. */
    @Test
    fun `a removed photo is never the cover`() {
        val faces = bestFacePerPerson(
            candidates,
            covers = mapOf("p:1" to 2L),
            exclusions = mapOf("p:1" to setOf(1L, 2L)),
        )

        assertEquals(3L, faces.getValue("p:1").mediaId)
    }

    @Test
    fun `a group whose every photo was removed has no face left to show`() {
        val faces = bestFacePerPerson(candidates, exclusions = mapOf("p:1" to setOf(1L, 2L, 3L)))

        assertNull(faces["p:1"])
    }

    private val candidates = listOf(
        candidate(mediaId = 1L, quality = 0.9f),
        candidate(mediaId = 2L, quality = 0.5f),
        candidate(mediaId = 3L, quality = 0.1f),
    )

    private fun candidate(mediaId: Long, quality: Float) = PersonFaceCandidate(
        personKey = "p:1",
        mediaId = mediaId,
        quality = quality,
        boxLeft = 10,
        boxTop = 20,
        boxRight = 60,
        boxBottom = 90,
        sourceWidth = 100,
        sourceHeight = 200,
    )
}
