package com.mediavault.app.ui.screens.imageviewer

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.app.library.LibraryRepository
import com.mediavault.core.database.entity.MediaItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImageViewerUiState(
    val item: MediaItemEntity? = null,
    val isLoading: Boolean = true,
    /** True once loading finished and no matching Library row exists — distinct from [isLoading] so the "not found" state is never shown for the first frame while the real lookup is still in flight. */
    val notFound: Boolean = false,
)

/**
 * A deliberately small, read-focused ViewModel for the image viewer — full-bleed preview,
 * title, and Share only (see `ImageViewerScreen`). Rename/delete/save-to-device already exist
 * on the Library row's own three-dot menu; duplicating them here would be a second place those
 * actions could drift out of sync, not a genuinely separate feature this screen needs to own.
 */
@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LibraryRepository,
) : ViewModel() {

    private val mediaItemId: String? = savedStateHandle[MEDIA_ITEM_ID_ARG]

    private val _uiState = MutableStateFlow(ImageViewerUiState())
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val item = mediaItemId?.let { repository.getById(it) }
            _uiState.update { it.copy(item = item, isLoading = false, notFound = item == null) }
        }
    }

    fun shareUriFor(item: MediaItemEntity): Uri? = repository.shareUriFor(item)

    companion object {
        const val MEDIA_ITEM_ID_ARG = "mediaItemId"
    }
}
