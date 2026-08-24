package com.mediavault.app.util

/**
 * Strips path separators and other filesystem-unsafe characters so a title coming from an
 * untrusted source (a web page's title, a user-typed rename) can never be used to escape the
 * intended directory (no `/` or `\`) or collide with reserved names. Shared by the download
 * engine (naming a freshly-downloaded file) and the library (renaming an existing one).
 */
fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(150).ifBlank { "file" }

/**
 * Returns [desiredName] unchanged if it's not already in [existingNames], otherwise appends
 * " (1)", " (2)", ... before the extension until a free name is found. Pure — no filesystem
 * access — so callers own the actual existence check (real files, a DB column, ...).
 */
fun nextAvailableFileName(desiredName: String, existingNames: Set<String>): String {
    if (desiredName !in existingNames) return desiredName

    val dotIndex = desiredName.lastIndexOf('.')
    val base = if (dotIndex > 0) desiredName.substring(0, dotIndex) else desiredName
    val extension = if (dotIndex > 0) desiredName.substring(dotIndex) else ""

    var attempt = 1
    while (true) {
        val candidate = "$base ($attempt)$extension"
        if (candidate !in existingNames) return candidate
        attempt++
    }
}
