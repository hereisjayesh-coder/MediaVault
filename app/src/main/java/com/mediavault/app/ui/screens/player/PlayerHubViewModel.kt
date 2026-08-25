package com.mediavault.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.app.library.LibraryRepository
import com.mediavault.app.player.LastPlayedProvider
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
    val item: MediaItemEntity? = null,
)

/**
 * Backs the Player *tab* — a lightweight "what was last playing" card in the normal five-tab
 * layout, deliberately not a real playback session (no [com.mediavault.core.domain.player.PlayerEngine]
 * is created here). Tapping it opens the dedicated, immersive `player/{id}` route, same as a
 * Library item — see the player redesign's architecture note in PROJECT_MASTER.md for why the
 * tab and the actual player are no longer the same screen.
 */
@HiltViewModel
class PlayerHubViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val lastPlayedProvider: LastPlayedProvider,
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
            val id = lastPlayedProvider.currentId()
            val item = id?.let { libraryRepository.getById(it) }?.takeIf { libraryRepository.fileExists(it) }
            _uiState.update { it.copy(isLoading = false, item = item) }
        }
    }
}
