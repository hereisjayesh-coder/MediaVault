package com.mediavault.app.ui.screens.library

import com.mediavault.app.library.LibrarySortOrder
import com.mediavault.core.database.entity.MediaItemEntity

data class LibraryUiState(
    val items: List<LibraryItemUi> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENT,
    /** Non-null while the rename dialog is open for this item. */
    val renameTarget: MediaItemEntity? = null,
    /** Non-null while the delete-confirmation dialog is open for this item. */
    val deleteTarget: MediaItemEntity? = null,
    /** Non-null while the details sheet is open for this item. */
    val detailsTarget: MediaItemEntity? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

/** A library row plus the one thing the DB alone can't tell you: whether the file is still there. */
data class LibraryItemUi(
    val entity: MediaItemEntity,
    val isMissing: Boolean,
)
