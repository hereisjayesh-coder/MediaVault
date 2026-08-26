package com.mediavault.app.library

import android.webkit.MimeTypeMap
import com.mediavault.app.util.sanitizeFileName
import com.mediavault.core.database.entity.MediaItemEntity

enum class LibrarySortOrder {
    RECENT,
    NAME,
    SIZE,
}

/**
 * Pure search + sort over the library, mirroring the same "precompute nothing fancy, just
 * filter/sort a small in-memory list" approach `SourceCatalogIndex` already uses for the
 * Supported Sources catalog — a library is realistically a few hundred items at most, so a
 * linear scan is more than fast enough and avoids dynamic-SQL query proliferation in the DAO.
 */
fun List<MediaItemEntity>.filterAndSort(query: String, sortOrder: LibrarySortOrder): List<MediaItemEntity> {
    val filtered = if (query.isBlank()) this else filter { it.title.contains(query, ignoreCase = true) }
    return when (sortOrder) {
        LibrarySortOrder.RECENT -> filtered.sortedByDescending { it.addedAtEpochMs }
        LibrarySortOrder.NAME -> filtered.sortedBy { it.title.lowercase() }
        LibrarySortOrder.SIZE -> filtered.sortedByDescending { it.sizeBytes ?: 0L }
    }
}

/** True when the file this item points to is no longer where it should be (moved/deleted outside MediaVault). */
fun MediaItemEntity.isMissing(fileExists: (String) -> Boolean): Boolean = !fileExists(mediaUri)

/**
 * Whether removing this Library row should also delete the underlying file/document — true only
 * for a MediaVault-managed download (its file lives in storage MediaVault itself owns), never for
 * an imported item (its file belongs to the user, outside MediaVault's storage, and must survive
 * a "remove from Library"). The one predicate [AndroidLibraryRepository.delete] defers to, so this
 * safety rule can't drift out of sync with itself.
 */
fun MediaItemEntity.canDeleteUnderlyingFile(): Boolean = !isImported

/** Where this Library row's bytes actually live — the single source of truth every "distinguish MediaVault downloads from imported media" UI (the Library badge, the details dialog) derives from, so they can never disagree. */
enum class MediaOrigin { DOWNLOADED, IMPORTED, SAVED_TO_GALLERY }

/**
 * [MediaOrigin.SAVED_TO_GALLERY] is a MediaVault download whose private copy was later moved to
 * the device Gallery (still has its [MediaItemEntity.sourceDownloadTaskId]) — see
 * [LibraryRepository.saveToGallery]. [MediaOrigin.IMPORTED] came from outside MediaVault entirely.
 */
fun MediaItemEntity.origin(): MediaOrigin = when {
    !isImported -> MediaOrigin.DOWNLOADED
    sourceDownloadTaskId != null -> MediaOrigin.SAVED_TO_GALLERY
    else -> MediaOrigin.IMPORTED
}

/** The one place a Library item's container maps to a MIME type — used for Share, Save to Files, and Save to Gallery alike, so they can never silently disagree. */
fun mimeTypeFor(item: MediaItemEntity): String =
    item.container?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) } ?: "*/*"

/** File name (with extension) to offer when saving this item outside MediaVault, whichever destination the user picks. */
fun exportFileName(item: MediaItemEntity): String {
    val extension = item.container ?: "bin"
    return "${sanitizeFileName(item.title)}.$extension"
}
