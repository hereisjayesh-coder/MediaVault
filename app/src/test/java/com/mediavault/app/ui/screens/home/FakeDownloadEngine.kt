package com.mediavault.app.ui.screens.home

import com.mediavault.core.domain.download.DownloadEngine
import com.mediavault.core.domain.download.DownloadProgress
import com.mediavault.core.domain.download.DownloadRequest
import com.mediavault.core.domain.download.PlaylistDownloadRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull

/** Test double for [DownloadEngine] — records calls instead of touching Room/SAF/network. */
class FakeDownloadEngine : DownloadEngine {

    val enqueued = mutableListOf<DownloadRequest>()
    val enqueuedPlaylists = mutableListOf<PlaylistDownloadRequest>()
    val paused = mutableListOf<String>()
    val resumed = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    val retried = mutableListOf<String>()
    val pausedPlaylists = mutableListOf<String>()
    val cancelledPlaylists = mutableListOf<String>()
    val retriedFailedInPlaylists = mutableListOf<String>()
    val removed = mutableListOf<String>()
    var alreadyDownloadedSourceMediaIds: Set<String> = emptySet()

    private val tasks = MutableStateFlow<List<DownloadProgress>>(emptyList())

    override fun enqueue(request: DownloadRequest) {
        enqueued.add(request)
    }

    override fun enqueuePlaylist(request: PlaylistDownloadRequest) {
        enqueuedPlaylists.add(request)
    }

    override fun pause(taskId: String) {
        paused.add(taskId)
    }

    override fun resume(taskId: String) {
        resumed.add(taskId)
    }

    override fun cancel(taskId: String) {
        cancelled.add(taskId)
    }

    override fun retry(taskId: String) {
        retried.add(taskId)
    }

    override fun pausePlaylist(playlistId: String) {
        pausedPlaylists.add(playlistId)
    }

    override fun cancelPlaylist(playlistId: String) {
        cancelledPlaylists.add(playlistId)
    }

    override fun retryFailedInPlaylist(playlistId: String) {
        retriedFailedInPlaylists.add(playlistId)
    }

    override fun remove(taskId: String) {
        removed.add(taskId)
    }

    override suspend fun isAlreadyDownloaded(sourceMediaId: String): Boolean =
        sourceMediaId in alreadyDownloadedSourceMediaIds

    override fun observeProgress(taskId: String) =
        tasks.mapNotNull { list -> list.firstOrNull { it.taskId == taskId } }

    override fun observeAll() = tasks
}
