package com.mediavault.app.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.core.domain.download.DownloadEngine
import com.mediavault.core.domain.download.DownloadProgress
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
        .map { tasks -> DownloadsUiState(tasks = tasks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun pause(taskId: String) = downloadEngine.pause(taskId)
    fun resume(taskId: String) = downloadEngine.resume(taskId)
    fun cancel(taskId: String) = downloadEngine.cancel(taskId)
    fun retry(taskId: String) = downloadEngine.retry(taskId)
}

data class DownloadsUiState(
    val tasks: List<DownloadProgress> = emptyList(),
)
