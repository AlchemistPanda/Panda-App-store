package com.pandagallery.app.data.media

internal fun newAlbumRelativePath(name: String): String {
    val cleanName = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
    require(cleanName.isNotBlank()) { "Album name is required" }
    return "Pictures/$cleanName/"
}

internal fun normalizedAlbumRelativePath(path: String): String {
    val normalized = path.trim().trim('/')
    require(normalized.isNotBlank()) { "Album path is required" }
    return "$normalized/"
}

internal fun renamedMediaDisplayName(originalName: String, requestedName: String): String {
    val extension = originalName.substringAfterLast('.', "")
    val cleanRequested = requestedName.trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    require(cleanRequested.isNotBlank()) { "File name is required" }
    if (extension.isBlank()) return cleanRequested
    val suffix = ".$extension"
    val baseName = if (cleanRequested.endsWith(suffix, ignoreCase = true)) {
        cleanRequested.dropLast(suffix.length).trimEnd()
    } else {
        cleanRequested
    }
    require(baseName.isNotBlank()) { "File name is required" }
    return "$baseName$suffix"
}
