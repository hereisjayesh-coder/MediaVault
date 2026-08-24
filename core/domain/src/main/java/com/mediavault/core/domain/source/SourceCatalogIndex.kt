package com.mediavault.core.domain.source

import com.mediavault.core.model.Source
import com.mediavault.core.model.SourceCategory

/**
 * In-memory search/filter/index over an already-loaded [SourceCatalog]. Built once per
 * catalog load; each source's searchable text (name + domain + aliases) is lowercased up
 * front so repeated searches (e.g. on every keystroke) only ever do a plain substring scan
 * — for a few thousand short strings that is comfortably fast without needing SQLite FTS
 * or any indexing structure heavier than this.
 */
class SourceCatalogIndex(sources: List<Source>) {

    val all: List<Source> = sources

    private val searchTextBySourceId: Map<String, String> =
        sources.associate { it.id to it.buildSearchText() }

    /** Case-insensitive match against display name, domain, and aliases. Blank query returns everything. */
    fun search(query: String): List<Source> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return all
        return all.filter { source -> searchTextBySourceId[source.id]?.contains(needle) == true }
    }

    fun byCategory(category: SourceCategory?): List<Source> =
        if (category == null) all else all.filter { category in it.categories }

    /** [sources] sorted A→Z by display name and bucketed by first letter; non-letter names land in '#'. */
    fun alphabeticalGroups(sources: List<Source> = all): Map<Char, List<Source>> =
        sources
            .sortedBy { it.displayName.lowercase() }
            .groupBy { source -> source.displayName.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#' }
}

private fun Source.buildSearchText(): String =
    (listOf(displayName, domain.orEmpty()) + aliases).joinToString(" ").lowercase()
