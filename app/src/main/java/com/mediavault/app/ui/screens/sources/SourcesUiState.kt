package com.mediavault.app.ui.screens.sources

import com.mediavault.core.model.Source
import com.mediavault.core.model.SourceCategory

data class SourcesUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val selectedCategory: SourceCategory? = null,
    /** Total sources in the catalog, unfiltered — used for the "N supported sources" count. */
    val totalCount: Int = 0,
    /** How many sources match the current search/category filter. */
    val visibleCount: Int = 0,
    /** Filtered sources, sorted A→Z and bucketed by first letter (non-letters under '#'). */
    val groups: Map<Char, List<Source>> = emptyMap(),
    val engineVersion: String = "",
)

data class SourceDetailUiState(
    val isLoading: Boolean = true,
    val source: Source? = null,
    val engineVersion: String = "",
)
