package com.mediavault.app.library

import com.mediavault.core.model.MediaType

/**
 * Which on-device files count as importable media, and what type they are — decided purely
 * from the file extension, the same "container is just the literal extension" convention the
 * download side already uses ([com.mediavault.core.model.MediaFormat.container]). Extension-based
 * rather than provider-reported MIME type deliberately: some SAF providers (USB drives, some
 * cloud-backed providers) report a generic `application/octet-stream` for perfectly normal media
 * files, which would make MIME-based filtering silently skip real media.
 */
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "3gpp", "m4v", "ts", "m2ts")
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma")

/** Null means "not a media file MediaVault imports" — the caller should skip it, not error out. */
fun mediaTypeForExtension(extension: String): MediaType? = when (extension.lowercase()) {
    in VIDEO_EXTENSIONS -> MediaType.VIDEO
    in AUDIO_EXTENSIONS -> MediaType.AUDIO
    else -> null
}

/** The file extension implied by [fileName], lowercased, without the leading dot — empty if there isn't one. */
fun extensionOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

/** [fileName] with its extension (if any) stripped, for use as a default title — never blank; falls back to the full name. */
fun titleFromFileName(fileName: String): String {
    val withoutExtension = fileName.substringBeforeLast('.', fileName)
    return withoutExtension.trim().ifBlank { fileName }
}
