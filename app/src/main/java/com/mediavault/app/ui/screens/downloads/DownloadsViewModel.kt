package com.mediavault.app.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.app.library.LibraryRepository
import com.mediavault.core.domain.download.DownloadEngine
import com.mediavault.core.domain.download.DownloadProgress
import com.mediavault.core.domain.download.PlaylistProgress
import com.mediavault.core.domain.download.toPlaylistProgressGroups
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadEngine: DownloadEngine,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> = downloadEngine.observeAll()
        .map { tasks ->
            DownloadsUiState(
                tasks = tasks.filter { it.playlistId == null },
                playlists = tasks.toPlaylistProgressGroups(),
                playlistTasksById = tasks.filter { it.playlistId != null }
                    .groupBy { it.playlistId!! }
                    .mapValues { (_, items) -> items.sortedBy { it.playlistItemIndex ?: 0 } },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    // Kept separate from `uiState` (which is entirely derived from `downloadEngine.observeAll()`)
    // rather than folding into `DownloadsUiState` — a one-shot navigation signal isn't part of
    // "what the download list looks like."
    private val _openMediaItemId = MutableStateFlow<String?>(null)
    val openMediaItemId: StateFlow<String?> = _openMediaItemId.asStateFlow()

    // Same one-shot pattern as `_openMediaItemId`, for the failure case — a task whose
    // Library row can no longer be resolved (e.g. deleted/renamed out from under it).
    private val _openInPlayerError = MutableStateFlow<String?>(null)
    val openInPlayerError: StateFlow<String?> = _openInPlayerError.asStateFlow()

    fun pause(taskId: String) = downloadEngine.pause(taskId)
    fun resume(taskId: String) = downloadEngine.resume(taskId)
    fun cancel(taskId: String) = downloadEngine.cancel(taskId)
    fun retry(taskId: String) = downloadEngine.retry(taskId)

    /** Removes a finished/failed/cancelled task's row from the Downloads list only — see [DownloadEngine.remove] for why a COMPLETED task's Library media is never touched by this. */
    fun remove(taskId: String) = downloadEngine.remove(taskId)

    fun pausePlaylist(playlistId: String) = downloadEngine.pausePlaylist(playlistId)
    fun cancelPlaylist(playlistId: String) = downloadEngine.cancelPlaylist(playlistId)
    fun retryFailedInPlaylist(playlistId: String) = downloadEngine.retryFailedInPlaylist(playlistId)

    /**
     * "Open" on a completed download must reach the same Player a Library item does — not
     * launch an external viewer Activity on a private `file://` URI, which is what silently
     * failed before (rejected by the platform's `FileUriExposedException` before it ever
     * reached an app chooser). [taskId] and the Library row it produced are different rows in
     * different tables, linked only via `MediaItemEntity.sourceDownloadTaskId` — resolving that
     * here is the one extra step needed to hand off to the exact same `player/{id}` route
     * Library already uses, with no second playback implementation.
     */
    fun openInPlayer(taskId: String) {
        viewModelScope.launch {
            val mediaItemId = libraryRepository.getBySourceDownloadTaskId(taskId)?.id
            if (mediaItemId == null) {
                _openInPlayerError.value = "Couldn't find this item in your Library. It may have been removed or renamed."
                return@launch
            }
            _openMediaItemId.value = mediaItemId
        }
    }

    fun consumeOpenInPlayer() {
        _openMediaItemId.update { null }
    }

    fun consumeOpenInPlayerError() {
        _openInPlayerError.update { null }
    }
}

data class DownloadsUiState(
    /** Ordinary, non-playlist downloads — rendered exactly as before. */
    val tasks: List<DownloadProgress> = emptyList(),
    val playlists: List<PlaylistProgress> = emptyList(),
    /** Per-item rows for each playlist, in playlist order, keyed by playlistId. */
    val playlistTasksById: Map<String, List<DownloadProgress>> = emptyMap(),
)
