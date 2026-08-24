package com.mediavault.core.domain.download

import com.mediavault.core.model.DownloadStatus
import com.mediavault.core.model.MediaType
import kotlinx.coroutines.flow.Flow

/**
 * Owns the mechanics of transferring bytes to disk for a queued download task.
 *
 * This is intentionally separate from [com.mediavault.core.domain.extractor.ExtractorEngine]:
 * extraction figures out *what* to download, this engine performs the transfer. A plain HTTP
 * downloader and a torrent-backed downloader can both implement this without the queue/UI
 * layer knowing which one is in use.
 */
interface DownloadEngine {

    /** Adds a task to the queue and returns immediately; use [observeProgress] to follow it. */
    fun enqueue(request: DownloadRequest)

    fun pause(taskId: String)
    fun resume(taskId: String)
    fun cancel(taskId: String)
    fun retry(taskId: String)

    fun observeProgress(taskId: String): Flow<DownloadProgress>
    fun observeAll(): Flow<List<DownloadProgress>>
}

data class DownloadRequest(
    val taskId: String,
    val sourceUrl: String,
    val destinationUri: String,
    val mediaType: MediaType,
    val expectedSizeBytes: Long?,
)

data class DownloadProgress(
    val taskId: String,
    val status: DownloadStatus,
    val bytesTransferred: Long,
    val totalBytes: Long?,
    val throughputBytesPerSecond: Long?,
    val errorMessage: String?,
)
