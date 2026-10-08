package com.pandagallery.app.data.backup

internal fun shouldIncludeInBackup(
    relativePath: String?,
    isVideo: Boolean,
    selectedAlbumPaths: Set<String>,
    includeVideos: Boolean,
): Boolean {
    if (!includeVideos && isVideo) return false
    if (selectedAlbumPaths.isEmpty()) return true
    val mediaPath = relativePath?.trim('/') ?: return false
    return mediaPath in selectedAlbumPaths.mapTo(mutableSetOf()) { it.trim('/') }
}

internal fun backupFolderComponents(relativePath: String?): List<String> = relativePath
    ?.split('/')
    ?.map(String::trim)
    ?.filter { it.isNotEmpty() && it != "." && it != ".." }
    .orEmpty()
