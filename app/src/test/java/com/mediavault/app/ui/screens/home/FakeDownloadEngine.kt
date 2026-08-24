package com.mediavault.app.ui.screens.home

import com.mediavault.core.domain.download.DownloadEngine
import com.mediavault.core.domain.download.DownloadProgress
import com.mediavault.core.domain.download.DownloadRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull

/** Test double for [DownloadEngine] — records calls instead of touching Room/SAF/network. */
class FakeDownloadEngine : DownloadEngine {

    val enqueued = mutableListOf<DownloadRequest>()
    val paused = mutableListOf<String>()
    val resumed = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    val retried = mutableListOf<String>()

    private val tasks = MutableStateFlow<List<DownloadProgress>>(emptyList())

    override fun enqueue(request: DownloadRequest) {
        enqueued.add(request)
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

    override fun observeProgress(taskId: String) =
        tasks.mapNotNull { list -> list.firstOrNull { it.taskId == taskId } }

    override fun observeAll() = tasks
}
