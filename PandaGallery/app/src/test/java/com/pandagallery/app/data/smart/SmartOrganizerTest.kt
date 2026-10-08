package com.pandagallery.app.data.smart

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartOrganizerTest {
    @Test
    fun `nearby photos form a stack`() {
        val base = 1_700_000_000_000L
        val groups = buildSmartGroupsFromCandidates(
            media = listOf(media(1, base), media(2, base + 5_000), media(3, base + 40_000)),
            people = emptyList(),
            nowMillis = base + 100_000,
        )

        assertEquals(setOf(1L, 2L), groups.single { it.type == SmartGroupType.STACK }.mediaIds)
    }

    @Test
    fun `faces sharing a person key become one group`() {
        val base = 1_700_000_000_000L
        val groups = buildSmartGroupsFromCandidates(
            media = listOf(media(1, base), media(2, base + 30_000), media(3, base + 60_000)),
            people = listOf(
                PersonAssignment(1, "p:1:0", .9f),
                PersonAssignment(2, "p:1:0", .8f),
                PersonAssignment(3, "p:9:0", .7f),
            ),
            nowMillis = base,
        )

        assertEquals(setOf(1L, 2L), groups.single { it.type == SmartGroupType.PEOPLE }.mediaIds)
    }

    @Test
    fun `a person seen in only one photo is not a group`() {
        val base = 1_700_000_000_000L
        val groups = buildSmartGroupsFromCandidates(
            media = listOf(media(1, base)),
            people = listOf(PersonAssignment(1, "p:1:0", .9f)),
            nowMillis = base,
        )

        assertEquals(0, groups.count { it.type == SmartGroupType.PEOPLE })
    }

    @Test
    fun `group members are ordered best face first`() {
        val base = 1_700_000_000_000L
        val groups = buildSmartGroupsFromCandidates(
            media = listOf(media(1, base), media(2, base + 1_000), media(3, base + 2_000)),
            people = listOf(
                PersonAssignment(1, "p:1:0", .2f),
                PersonAssignment(2, "p:1:0", .9f),
                PersonAssignment(3, "p:1:0", .5f),
            ),
            nowMillis = base,
        )

        assertEquals(
            listOf(2L, 3L, 1L),
            groups.single { it.type == SmartGroupType.PEOPLE }.mediaIds.toList(),
        )
    }

    @Test
    fun `trashed photos drop out of their person group`() {
        val base = 1_700_000_000_000L
        val groups = buildSmartGroupsFromCandidates(
            media = listOf(media(1, base), media(2, base + 1_000, isTrashed = true), media(3, base + 2_000)),
            people = listOf(
                PersonAssignment(1, "p:1:0", .9f),
                PersonAssignment(2, "p:1:0", .8f),
                PersonAssignment(3, "p:1:0", .7f),
            ),
            nowMillis = base,
        )

        assertEquals(setOf(1L, 3L), groups.single { it.type == SmartGroupType.PEOPLE }.mediaIds)
    }

    @Test
    fun `saved name replaces generated people title`() {
        val groups = listOf(personGroup("p:10:0", setOf(1, 2)))

        val customized = applyPeopleGroupPreferences(
            groups,
            listOf(PersonGroupPreference("p:10:0", "Asha", null)),
        )

        assertEquals("Asha", customized.single().title)
    }

    @Test
    fun `merged people groups combine media and keep destination name`() {
        val groups = listOf(personGroup("p:10:0", setOf(1, 2)), personGroup("p:20:0", setOf(3, 4)))

        val customized = applyPeopleGroupPreferences(
            groups,
            listOf(
                PersonGroupPreference("p:10:0", null, "p:20:0"),
                PersonGroupPreference("p:20:0", "Family", null),
            ),
        )

        assertEquals(1, customized.size)
        assertEquals("Family", customized.single().title)
        assertEquals(setOf(1L, 2L, 3L, 4L), customized.single().mediaIds)
    }

    @Test
    fun `a dismissed group stops being shown`() {
        val groups = listOf(personGroup("p:10:0", setOf(1, 2)), personGroup("p:20:0", setOf(3, 4)))

        val visible = applyPeopleGroupPreferences(
            groups,
            listOf(PersonGroupPreference("p:10:0", null, null, isDismissed = true)),
        )

        assertEquals(listOf("p:20:0"), visible.map(SmartGroup::key))
    }

    @Test
    fun `dismissing one group does not renumber the others oddly`() {
        val groups = listOf(
            personGroup("p:10:0", setOf(1, 2)),
            personGroup("p:20:0", setOf(3, 4, 5)),
        )

        val visible = applyPeopleGroupPreferences(
            groups,
            listOf(PersonGroupPreference("p:20:0", null, null, isDismissed = true)),
        )

        assertEquals(1, visible.size)
        assertEquals("Person 1", visible.single().title)
    }

    private fun media(id: Long, taken: Long, isTrashed: Boolean = false) =
        SmartMediaCandidate(id, taken, isImage = true, isTrashed = isTrashed)

    private fun personGroup(key: String, ids: Set<Int>) = SmartGroup(
        SmartGroupType.PEOPLE,
        key,
        "Person",
        "${ids.size} photos",
        ids.mapTo(linkedSetOf(), Int::toLong),
    )
}
