package com.mediavault.core.domain.processing

import kotlinx.coroutines.flow.Flow

/**
 * Application-facing contract for combining separately-downloaded video and audio streams
 * into one playable file. FFmpeg is the initial and only planned implementation, but nothing
 * above this layer should reference FFmpeg types directly — mirrors how [com.mediavault.core.domain.extractor.ExtractorEngine]
 * keeps yt-dlp out of the rest of the app. See PROJECT_MASTER.md's FFmpeg decision log entry
 * for why this exists as its own abstraction rather than being folded into `DownloadEngine`.
 */
interface MediaProcessor {

    /** Stable identifier for this implementation, e.g. "ffmpeg". */
    val processorId: String

    /**
     * Remuxes (never re-encodes/transcodes) [MergeRequest.videoPath] and [MergeRequest.audioPath]
     * into one file at [MergeRequest.outputPath]. Emits progress while running, then exactly one
     * terminal [ProcessingEvent.Completed] or [ProcessingEvent.Failed].
     */
    fun merge(request: MergeRequest): Flow<ProcessingEvent>

    /** Cancels an in-flight [merge] identified by [MergeRequest.taskId]. Safe to call for an unknown or already-finished id. */
    suspend fun cancel(taskId: String)
}

data class MergeRequest(
    val taskId: String,
    /** Real, writable filesystem paths — same convention as [com.mediavault.core.domain.extractor.ExtractionRequest.destinationPath]. */
    val videoPath: String,
    val audioPath: String,
    val outputPath: String,
    /** File extension of [outputPath], e.g. "mp4"/"webm"/"mkv" — see `mergeOutputContainer` in `DownloadOption.kt` for how this is chosen. */
    val outputContainer: String,
    /** When known, lets the processor estimate a completion percentage from FFmpeg's own reported processed-time; null yields indeterminate progress. */
    val estimatedDurationSeconds: Long? = null,
)

sealed class ProcessingEvent {
    data class Progress(val taskId: String, val percent: Int?) : ProcessingEvent()
    data class Completed(val taskId: String, val outputPath: String) : ProcessingEvent()
    data class Failed(val taskId: String, val message: String, val cause: Throwable? = null) : ProcessingEvent()
}
