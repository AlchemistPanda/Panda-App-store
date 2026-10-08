package com.pandagallery.app.data.smart

import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleGroupOrderTest {
    private val groups = listOf(
        SmartGroup(SmartGroupType.RECAP, "2026-08", "August 2026", "452 photo recap", setOf(1L)),
        SmartGroup(SmartGroupType.STACK, "stack", "Photo stack", "12 shots", setOf(2L)),
        SmartGroup(SmartGroupType.PEOPLE, "p:1", "Person 1", "4 photos", setOf(3L)),
    )

    /**
     * Guards the bug this fixed: people were appended last, so a person sat behind six monthly
     * recaps and twelve stacks — nineteen cards down, which read as "face grouping doesn't work".
     */
    @Test
    fun `people come before recaps and stacks`() {
        val ordered = applyPeopleGroupPreferences(groups, emptyList())
        assertEquals(SmartGroupType.PEOPLE, ordered.first().type)
    }

    @Test
    fun `a named group shows the name instead of a generated title`() {
        val ordered = applyPeopleGroupPreferences(
            groups,
            listOf(PersonGroupPreference("p:1", "Mum", null)),
        )
        assertEquals("Mum", ordered.first().title)
    }

    @Test
    fun `a dismissed group disappears from the list`() {
        val ordered = applyPeopleGroupPreferences(
            groups,
            listOf(PersonGroupPreference("p:1", null, null, isDismissed = true)),
        )
        assertEquals(emptyList<SmartGroup>(), ordered.filter { it.type == SmartGroupType.PEOPLE })
    }
}
