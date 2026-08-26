package com.mediavault.app.ui.screens.player

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

data class PlayerHubUiState(
    val isLoading: Boolean = true,
    val continueWatching: List<MediaItemEntity> = emptyList(),
    val recentlyWatched: List<MediaItemEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = continueWatching.isEmpty() && recentlyWatched.isEmpty()
}

/**
 * Backs the Player *tab* — a real multi-item "what have I been watching" experience in the
 * normal five-tab layout, deliberately not a real playback session (no
 * [com.mediavault.core.domain.player.PlayerEngine] is created here). Tapping any row opens the
 * dedicated, immersive `player/{id}` route, same as a Library item — see the player redesign's
 * architecture note in PROJECT_MASTER.md for why the tab and the actual player are no longer the
 * same screen.
 */
@HiltViewModel
class PlayerHubViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerHubUiState())
    val uiState: StateFlow<PlayerHubUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Called from the screen's own `LaunchedEffect(Unit)` too, so returning to this tab after playing something new picks it up even though the ViewModel instance survives on the back stack. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val history = libraryRepository.getWatchHistory().filter { libraryRepository.fileExists(it) }
            val sections = history.toWatchHistorySections()
            _uiState.update {
                it.copy(isLoading = false, continueWatching = sections.continueWatching, recentlyWatched = sections.recentlyWatched)
            }
        }
    }
}
