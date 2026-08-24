package com.mediavault.app.ui.screens.sources

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.core.domain.source.SourceCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SourceDetailViewModel @Inject constructor(
    private val repository: SourceCatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sourceId: String = savedStateHandle.get<String>(SOURCE_ID_ARG).orEmpty()

    private val _uiState = MutableStateFlow(SourceDetailUiState())
    val uiState: StateFlow<SourceDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val catalog = repository.getCatalog()
            val source = catalog.sources.firstOrNull { it.id == sourceId }
            _uiState.update {
                it.copy(isLoading = false, source = source, engineVersion = catalog.metadata.engineVersion)
            }
        }
    }

    companion object {
        const val SOURCE_ID_ARG = "sourceId"
    }
}
