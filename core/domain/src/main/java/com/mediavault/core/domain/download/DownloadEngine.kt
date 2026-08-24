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

    /**
     * Adds every item in [request] as its own task, in order. Each item's own format list is
     * unknown up front (playlist items are lightweight — see `PlaylistAnalysisResult`), so
     * each one is resolved against [PlaylistDownloadRequest.qualityDescriptor] independently
     * once its own analysis completes; a task appears as [com.mediavault.core.model.DownloadStatus.ANALYZING]
     * until then. Returns immediately — use [observeAll] to follow every item's progress.
     */
    fun enqueuePlaylist(request: PlaylistDownloadRequest)

    fun pause(taskId: String)
    fun resume(taskId: String)
    fun cancel(taskId: String)
    fun retry(taskId: String)

    /** Pauses every currently-active/queued task belonging to [playlistId]; leaves finished ones alone. */
    fun pausePlaylist(playlistId: String)

    /** Cancels every non-terminal task belonging to [playlistId]; leaves finished ones alone. */
    fun cancelPlaylist(playlistId: String)

    /** Retries every [com.mediavault.core.model.DownloadStatus.FAILED] task belonging to [playlistId]. */
    fun retryFailedInPlaylist(playlistId: String)

    /** True if a task with this [sourceMediaId] has already completed successfully. */
    suspend fun isAlreadyDownloaded(sourceMediaId: String): Boolean

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
    val mediaType: MediaType,
    val expectedSizeBytes: Long?,
    /** From `MediaAnalysisResult.durationSeconds`, for Library display — null when unknown. */
    val durationSeconds: Long? = null,
    /** From the selected `MediaFormat.resolutionLabel`, for Library display — null for audio. */
    val resolutionLabel: String? = null,
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

/** One playlist-wide "download these" operation — see [DownloadEngine.enqueuePlaylist]. */
data class PlaylistDownloadRequest(
    /** Stable within this queueing operation; used to group tasks and for playlist-level pause/cancel/retry. */
    val playlistId: String,
    val playlistTitle: String,
    val playlistThumbnailUrl: String?,
    val sourceName: String?,
    /** The single quality every item is downloaded at; see [QualityDescriptor]. */
    val qualityDescriptor: QualityDescriptor,
    /** When true, an item already completed successfully (by [DownloadEngine.isAlreadyDownloaded]) is skipped, not re-downloaded. */
    val skipAlreadyDownloaded: Boolean,
    /** In playlist order. */
    val items: List<PlaylistDownloadItem>,
)

data class PlaylistDownloadItem(
    /** Webpage URL to (re-)analyze for this single item's own format list. */
    val sourceUrl: String,
    /** Stable extractor id for this item — the basis for duplicate detection. */
    val sourceMediaId: String,
    /** 1-based position within the playlist — preserved through to the created task. */
    val itemIndex: Int,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long? = null,
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
    val playlistId: String? = null,
    val playlistItemIndex: Int? = null,
    val playlistTitle: String? = null,
    val playlistThumbnailUrl: String? = null,
)
