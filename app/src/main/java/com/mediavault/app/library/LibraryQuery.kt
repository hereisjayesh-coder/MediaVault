package com.mediavault.app.library

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
