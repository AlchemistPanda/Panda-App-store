package com.pandaapps.appstore.work

import com.pandaapps.appstore.work.AutoUpdatePolicy.INSTALL_BUDGET_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdatePolicyTest {

    private val key = "com.example.app:5"

    @Test
    fun skipReason_newVersion_isInstalled() {
        assertNull(AutoUpdatePolicy.skipReason(key, silent = false, notified = emptySet(), deferred = emptySet()))
        assertNull(AutoUpdatePolicy.skipReason(key, silent = true, notified = emptySet(), deferred = emptySet()))
    }

    @Test
    fun skipReason_alreadyOfferedTapUpdate_isNotPromptedAgain() {
        assertEquals(
            AutoUpdateSkip.AlreadyOffered,
            AutoUpdatePolicy.skipReason(key, silent = false, notified = setOf(key), deferred = emptySet()),
        )
    }

    @Test
    fun skipReason_silentUpdate_isRetriedEvenWhenAnnounced() {
        assertNull(AutoUpdatePolicy.skipReason(key, silent = true, notified = setOf(key), deferred = emptySet()))
    }

    @Test
    fun skipReason_deferredVersion_isNeverDownloadedInBackground() {
        assertEquals(
            AutoUpdateSkip.Deferred,
            AutoUpdatePolicy.skipReason(key, silent = true, notified = emptySet(), deferred = setOf(key)),
        )
        assertEquals(
            AutoUpdateSkip.Deferred,
            AutoUpdatePolicy.skipReason(key, silent = false, notified = setOf(key), deferred = setOf(key)),
        )
    }

    @Test
    fun skipReason_otherVersionsKeysDoNotMatch() {
        val older = setOf("com.example.app:4")
        assertNull(AutoUpdatePolicy.skipReason(key, silent = false, notified = older, deferred = older))
    }

    @Test
    fun throughput_needsAMeaningfulSample() {
        assertNull(AutoUpdatePolicy.throughput(bytes = 0, elapsedMillis = 10_000))
        assertNull(AutoUpdatePolicy.throughput(bytes = 1_000_000, elapsedMillis = 500)) // cached / too short
        assertEquals(250_000.0, AutoUpdatePolicy.throughput(bytes = 1_000_000, elapsedMillis = 4_000)!!, 0.001)
    }

    @Test
    fun canFinishInTime_unknownSizeOrSpeed_getsTheBenefitOfTheDoubt() {
        assertTrue(AutoUpdatePolicy.canFinishInTime(sizeBytes = null, bytesPerSecond = 1.0, remainingMillis = 1))
        assertTrue(AutoUpdatePolicy.canFinishInTime(sizeBytes = 180_000_000, bytesPerSecond = null, remainingMillis = 1))
    }

    @Test
    fun canFinishInTime_comparesProjectedTimeWithWhatIsLeft() {
        // 180 MB at 1 MB/s = 180 s.
        assertTrue(AutoUpdatePolicy.canFinishInTime(180_000_000, 1_000_000.0, remainingMillis = 180_000))
        assertFalse(AutoUpdatePolicy.canFinishInTime(180_000_000, 1_000_000.0, remainingMillis = 179_000))
    }

    @Test
    fun tooSlow_whenEvenAWholeRunWouldNotFinish() {
        // ~180 MB at 2 Mbit/s (250 kB/s): 90 MB after 6 minutes → 12 minutes projected > 8 minute budget.
        assertTrue(
            AutoUpdatePolicy.isTooSlowForBackground(
                bytes = 90_000_000, total = 180_000_000, elapsedMillis = 360_000, budgetMillis = INSTALL_BUDGET_MILLIS,
            )
        )
    }

    @Test
    fun notTooSlow_whenItMerelyStartedLate() {
        // Fast link, cut off after 30 s at 40 %: 75 s projected, fits easily in a fresh run.
        assertFalse(
            AutoUpdatePolicy.isTooSlowForBackground(
                bytes = 72_000_000, total = 180_000_000, elapsedMillis = 30_000, budgetMillis = INSTALL_BUDGET_MILLIS,
            )
        )
    }

    @Test
    fun unknownProgress_isTooSlowOnlyAfterAFairChance() {
        val half = INSTALL_BUDGET_MILLIS / 2
        assertFalse(AutoUpdatePolicy.isTooSlowForBackground(0, 180_000_000, half - 1, INSTALL_BUDGET_MILLIS))
        assertTrue(AutoUpdatePolicy.isTooSlowForBackground(0, 180_000_000, half, INSTALL_BUDGET_MILLIS))
        assertFalse(AutoUpdatePolicy.isTooSlowForBackground(5_000_000, -1, half - 1, INSTALL_BUDGET_MILLIS))
        assertTrue(AutoUpdatePolicy.isTooSlowForBackground(5_000_000, -1, half, INSTALL_BUDGET_MILLIS))
    }

    @Test
    fun budget_leavesRoomWithinWorkManagersTenMinutes() {
        assertTrue(INSTALL_BUDGET_MILLIS + AutoUpdatePolicy.RESULT_WAIT_MILLIS < 10 * 60_000L)
    }
}
