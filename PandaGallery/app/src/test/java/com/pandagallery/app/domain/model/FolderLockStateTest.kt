package com.pandagallery.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that keeps a folder lock answerable.
 *
 * Guards a state a user actually reached: the folder PIN was removed while a folder was still
 * flagged locked, which left the folder impossible to open *and* impossible to unlock — every guess
 * was checked against a PIN that no longer existed, so it failed and counted toward a lockout.
 */
class FolderLockStateTest {
    @Test
    fun `a locked folder asks for a PIN while one exists`() {
        assertTrue(folderNeedsPin(isLocked = true, hasFolderLockPin = true, isUnlockedThisSession = false))
    }

    @Test
    fun `a locked folder with no PIN set asks for nothing`() {
        assertFalse(folderNeedsPin(isLocked = true, hasFolderLockPin = false, isUnlockedThisSession = false))
    }

    @Test
    fun `an unlocked session does not ask again`() {
        assertFalse(folderNeedsPin(isLocked = true, hasFolderLockPin = true, isUnlockedThisSession = true))
    }

    @Test
    fun `an unlocked folder never asks`() {
        assertFalse(folderNeedsPin(isLocked = false, hasFolderLockPin = true, isUnlockedThisSession = false))
    }
}
