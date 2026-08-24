package com.mediavault.app.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.core.domain.download.DownloadEngine
import com.mediavault.core.domain.download.DownloadProgress
import com.mediavault.core.domain.download.PlaylistProgress
import com.mediavault.core.domain.download.toPlaylistProgressGroups
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadEngine: DownloadEngine,
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

    fun pause(taskId: String) = downloadEngine.pause(taskId)
    fun resume(taskId: String) = downloadEngine.resume(taskId)
    fun cancel(taskId: String) = downloadEngine.cancel(taskId)
    fun retry(taskId: String) = downloadEngine.retry(taskId)

    fun pausePlaylist(playlistId: String) = downloadEngine.pausePlaylist(playlistId)
    fun cancelPlaylist(playlistId: String) = downloadEngine.cancelPlaylist(playlistId)
    fun retryFailedInPlaylist(playlistId: String) = downloadEngine.retryFailedInPlaylist(playlistId)
}

data class DownloadsUiState(
    /** Ordinary, non-playlist downloads — rendered exactly as before. */
    val tasks: List<DownloadProgress> = emptyList(),
    val playlists: List<PlaylistProgress> = emptyList(),
    /** Per-item rows for each playlist, in playlist order, keyed by playlistId. */
    val playlistTasksById: Map<String, List<DownloadProgress>> = emptyMap(),
)
