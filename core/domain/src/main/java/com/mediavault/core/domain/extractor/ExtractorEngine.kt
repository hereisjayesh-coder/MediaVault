package com.mediavault.core.domain.extractor

import com.mediavault.core.common.AppResult
import com.mediavault.core.model.MediaFormat
import com.mediavault.core.model.MediaTrackInfo
import com.mediavault.core.model.SubtitleTrackInfo
import kotlinx.coroutines.flow.Flow

/**
 * Application-facing contract for turning a source URL into downloadable media.
 *
 * The UI and the rest of the app depend only on this interface, never on a specific
 * extraction backend (e.g. yt-dlp). Swapping or upgrading the backend must not require
 * UI or use-case changes.
 */
interface ExtractorEngine {

    /** Stable identifier for this engine implementation, e.g. "ytdlp". */
    val engineId: String

    /** Version of the underlying extraction backend, tracked independently of the app version. */
    val engineVersion: String

    /** Whether this engine believes it can analyze the given URL, without performing a full analysis. */
    suspend fun canHandle(url: String): Boolean

    /**
     * Fetches metadata for [url] — a single item's formats and tracks, or a playlist's
     * ordered items, depending on what the URL points to. See [ExtractionResult].
     *
     * [taskId] identifies this specific call so it can be stopped with [cancel] while it is
     * still in flight — callers that care about cancellation must generate and retain their
     * own id (a random UUID is fine) before calling this.
     */
    suspend fun analyze(url: String, taskId: String): AppResult<ExtractionResult>

    /** Starts a download for a previously analyzed [ExtractionRequest], emitting progress events. */
    fun download(request: ExtractionRequest): Flow<ExtractionEvent>

    /** Cancels an in-flight [analyze] or [download] call identified by [taskId]. Safe to call for an unknown or already-finished id. */
    suspend fun cancel(taskId: String)
}

/**
 * What analyzing a URL turned out to be: one playable item, or a playlist/channel of them.
 * Single-item analysis is unaffected by the existence of this wrapper — [Single] carries
 * exactly the [MediaAnalysisResult] the engine has always produced.
 */
sealed class ExtractionResult {
    data class Single(val media: MediaAnalysisResult) : ExtractionResult()
    data class Playlist(val playlist: PlaylistAnalysisResult) : ExtractionResult()
}

data class MediaAnalysisResult(
    /** Extractor-assigned id, stable across analyses — the basis for future dedup/"already downloaded" checks. */
    val id: String,
    val sourceName: String,
    val title: String,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    val webpageUrl: String?,
    val formats: List<MediaFormat>,
    val audioTracks: List<MediaTrackInfo>,
    val subtitleTracks: List<SubtitleTrackInfo>,
)

data class ExtractionRequest(
    val taskId: String,
    /** Webpage URL to (re-)extract from — the direct media URL may be short-lived/signed. */
    val sourceUrl: String,
    val formatId: String,
    /**
     * A real, writable filesystem path (not a `content://` SAF URI — extractor backends write
     * with plain file I/O). Callers are responsible for copying the finished file to the user's
     * chosen SAF destination once [ExtractionEvent.Completed] arrives.
     */
    val destinationPath: String,
)

sealed class ExtractionEvent {
    data class Progress(
        val taskId: String,
        val bytesTransferred: Long,
        val totalBytes: Long?,
        val stage: ExtractionStage,
        val speedBytesPerSecond: Long? = null,
        val etaSeconds: Long? = null,
    ) : ExtractionEvent()

    data class Completed(val taskId: String, val outputPath: String) : ExtractionEvent()
    data class Failed(val taskId: String, val message: String, val cause: Throwable? = null) : ExtractionEvent()
}

enum class ExtractionStage {
    ANALYZING,
    DOWNLOADING,
    PROCESSING,
    MERGING,
}
