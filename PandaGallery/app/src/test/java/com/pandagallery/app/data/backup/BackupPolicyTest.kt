package com.pandagallery.app.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPolicyTest {
    @Test
    fun `empty album selection includes every album`() {
        assertTrue(shouldIncludeInBackup("DCIM/Camera/", isVideo = false, emptySet(), includeVideos = true))
        assertTrue(shouldIncludeInBackup("Movies/", isVideo = true, emptySet(), includeVideos = true))
    }

    @Test
    fun `selected albums exclude media outside those paths`() {
        assertTrue(shouldIncludeInBackup("DCIM/Camera/", false, setOf("DCIM/Camera"), includeVideos = true))
        assertFalse(shouldIncludeInBackup("Movies/", true, setOf("DCIM/Camera"), includeVideos = true))
    }

    @Test
    fun `video preference excludes only videos`() {
        assertTrue(shouldIncludeInBackup("DCIM/Camera/", false, emptySet(), includeVideos = false))
        assertFalse(shouldIncludeInBackup("Movies/", true, emptySet(), includeVideos = false))
    }

    @Test
    fun `backup path components remove unsafe names`() {
        assertTrue(backupFolderComponents("Pictures/WhatsApp Images/") == listOf("Pictures", "WhatsApp Images"))
        assertTrue(backupFolderComponents("../Pictures//Camera") == listOf("Pictures", "Camera"))
    }
}
