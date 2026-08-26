package com.mediavault.app.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.app.library.LibraryRepository
import com.mediavault.app.library.LibrarySortOrder
import com.mediavault.app.library.MediaImportRepository
import com.mediavault.app.library.filterAndSort
import com.mediavault.app.library.summaryMessage
import com.mediavault.core.common.AppResult
import com.mediavault.core.database.entity.MediaItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val importRepository: MediaImportRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(LibrarySortOrder.RECENT)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeAll(), searchQuery, sortOrder) { items, query, sort ->
                items.filterAndSort(query, sort).map { LibraryItemUi(it, !repository.fileExists(it)) }
            }.flowOn(Dispatchers.IO).collect { visible ->
                _uiState.update { it.copy(items = visible, isLoading = false) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSortOrderChanged(sort: LibrarySortOrder) {
        sortOrder.value = sort
        _uiState.update { it.copy(sortOrder = sort) }
    }

    fun onRenameRequested(item: MediaItemEntity) {
        _uiState.update { it.copy(renameTarget = item) }
    }

    fun onRenameDismissed() {
        _uiState.update { it.copy(renameTarget = null) }
    }

    fun onRenameConfirmed(newTitle: String) {
        val target = _uiState.value.renameTarget ?: return
        viewModelScope.launch {
            when (val result = repository.rename(target.id, newTitle)) {
                is AppResult.Success -> _uiState.update { it.copy(renameTarget = null) }
                is AppResult.Failure -> _uiState.update { it.copy(renameTarget = null, errorMessage = result.error.message) }
            }
        }
    }

    fun onDeleteRequested(item: MediaItemEntity) {
        _uiState.update { it.copy(deleteTarget = item) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    fun onDeleteConfirmed() {
        val target = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            repository.delete(target.id)
            _uiState.update { it.copy(deleteTarget = null) }
        }
    }

    fun onDetailsRequested(item: MediaItemEntity) {
        _uiState.update { it.copy(detailsTarget = item) }
    }

    fun onDetailsDismissed() {
        _uiState.update { it.copy(detailsTarget = null) }
    }

    fun shareUriFor(item: MediaItemEntity): Uri? = repository.shareUriFor(item)

    // --- Save to device (Gallery / Files) ----------------------------------------------

    fun onSaveToDeviceRequested(item: MediaItemEntity) {
        _uiState.update { it.copy(saveToDeviceTarget = item) }
    }

    fun onSaveToDeviceDismissed() {
        _uiState.update { it.copy(saveToDeviceTarget = null) }
    }

    fun exportTo(item: MediaItemEntity, targetUri: Uri) {
        viewModelScope.launch {
            when (val result = repository.exportTo(item.id, targetUri)) {
                is AppResult.Success -> _uiState.update { it.copy(infoMessage = "Saved to Files.") }
                is AppResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    fun saveToGallery(item: MediaItemEntity) {
        viewModelScope.launch {
            when (val result = repository.saveToGallery(item.id)) {
                is AppResult.Success -> _uiState.update { it.copy(infoMessage = "Saved to Gallery.") }
                is AppResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    // --- Import (explicit user action only — see MediaImportRepository) ---------------

    fun onImportFileSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            when (val result = importRepository.importFile(uri)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isImporting = false, infoMessage = "Imported \"${result.data.title}\".")
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isImporting = false, errorMessage = result.error.message)
                }
            }
        }
    }

    fun onImportFolderSelected(treeUri: Uri?) {
        if (treeUri == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            when (val result = importRepository.importFolder(treeUri)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isImporting = false, infoMessage = result.data.summaryMessage())
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isImporting = false, errorMessage = result.error.message)
                }
            }
        }
    }

    fun consumeInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
