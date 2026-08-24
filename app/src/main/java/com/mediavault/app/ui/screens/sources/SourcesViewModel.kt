package com.mediavault.app.ui.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.core.domain.source.SourceCatalogIndex
import com.mediavault.core.domain.source.SourceCatalogRepository
import com.mediavault.core.model.SourceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val repository: SourceCatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourcesUiState())
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    private var index: SourceCatalogIndex? = null

    init {
        viewModelScope.launch {
            val catalog = repository.getCatalog()
            index = SourceCatalogIndex(catalog.sources)
            _uiState.update { it.copy(engineVersion = catalog.metadata.engineVersion) }
            recompute()
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        recompute()
    }

    fun onCategorySelected(category: SourceCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
        recompute()
    }

    private fun recompute() {
        val idx = index ?: return
        val state = _uiState.value
        val matched = idx.search(state.query)
        val filtered = state.selectedCategory?.let { category -> matched.filter { category in it.categories } } ?: matched

        _uiState.update {
            it.copy(
                isLoading = false,
                totalCount = idx.all.size,
                visibleCount = filtered.size,
                groups = idx.alphabeticalGroups(filtered),
            )
        }
    }
}
