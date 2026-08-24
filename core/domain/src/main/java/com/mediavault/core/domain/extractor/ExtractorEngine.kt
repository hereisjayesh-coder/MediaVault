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

    /** Fetches metadata, available formats, and available tracks for [url]. */
    suspend fun analyze(url: String): AppResult<MediaAnalysisResult>

    /** Starts a download for a previously analyzed [ExtractionRequest], emitting progress events. */
    fun download(request: ExtractionRequest): Flow<ExtractionEvent>

    /** Cancels an in-flight extraction/download identified by [taskId]. */
    suspend fun cancel(taskId: String)
}

data class MediaAnalysisResult(
    val sourceName: String,
    val title: String,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    val formats: List<MediaFormat>,
    val audioTracks: List<MediaTrackInfo>,
    val subtitleTracks: List<SubtitleTrackInfo>,
)

data class ExtractionRequest(
    val taskId: String,
    val sourceUrl: String,
    val formatId: String,
    val destinationUri: String,
)

sealed class ExtractionEvent {
    data class Progress(
        val taskId: String,
        val bytesTransferred: Long,
        val totalBytes: Long?,
        val stage: ExtractionStage,
    ) : ExtractionEvent()

    data class Completed(val taskId: String, val outputUri: String) : ExtractionEvent()
    data class Failed(val taskId: String, val message: String, val cause: Throwable? = null) : ExtractionEvent()
}

enum class ExtractionStage {
    ANALYZING,
    DOWNLOADING,
    PROCESSING,
    MERGING,
}
