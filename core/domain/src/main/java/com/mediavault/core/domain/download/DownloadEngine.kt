package com.mediavault.core.domain.download

import com.mediavault.core.model.DownloadStatus
import com.mediavault.core.model.MediaType
import kotlinx.coroutines.flow.Flow

/**
 * Owns the mechanics of transferring bytes to disk for a queued download task.
 *
 * This is intentionally separate from [com.mediavault.core.domain.extractor.ExtractorEngine]:
 * extraction figures out *what* to download (and, for extractor-originated media, performs the
 * actual transfer via [com.mediavault.core.domain.extractor.ExtractorEngine.download] — only the
 * extractor backend truly knows how to fetch a given format/protocol). This engine owns the
 * *queue*: persistence, network policy, state transitions, and background execution. A plain
 * HTTP downloader and a torrent-backed downloader can both sit behind this without the UI
 * knowing which one is in use.
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
    /** Webpage URL to (re-)extract from — not a raw CDN URL, which may be short-lived/signed. */
    val sourceUrl: String,
    /** Which of the analyzed formats to fetch. */
    val formatId: String,
    val title: String,
    val sourceName: String?,
    val thumbnailUrl: String?,
    /** File extension/container of the selected format, e.g. "mp4" — used to name the saved file. */
    val container: String,
    /** SAF tree URI of the user-selected destination folder. */
    val destinationTreeUri: String,
    val mediaType: MediaType,
    val expectedSizeBytes: Long?,
    /** Carried from the selected [com.mediavault.core.model.MediaFormat.supportsResume]. */
    val canResume: Boolean,
    /** The extractor-assigned id of the media being downloaded, for future dedup/"already downloaded" checks. */
    val sourceMediaId: String? = null,
    /** Set when this request came from a playlist item, so the queue can preserve order and group by playlist. */
    val playlistContext: PlaylistDownloadContext? = null,
)

data class PlaylistDownloadContext(
    val playlistId: String,
    /** 1-based position within the playlist — preserves download order. */
    val itemIndex: Int,
)

data class DownloadProgress(
    val taskId: String,
    val title: String?,
    val sourceName: String?,
    val thumbnailUrl: String?,
    val status: DownloadStatus,
    val bytesTransferred: Long,
    val totalBytes: Long?,
    val throughputBytesPerSecond: Long?,
    val etaSeconds: Long?,
    val canResume: Boolean,
    val errorMessage: String?,
    val destinationUri: String?,
    val createdAtEpochMs: Long,
)
