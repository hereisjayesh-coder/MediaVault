package com.mediavault.app.processing

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.mediavault.core.domain.processing.MediaProcessor
import com.mediavault.core.domain.processing.MergeRequest
import com.mediavault.core.domain.processing.ProcessingEvent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [MediaProcessor] backed by FFmpegKit (LGPL v3 build — see THIRD-PARTY-NOTICES.md). Only ever
 * remuxes (`-c copy`): MediaVault never re-encodes video or audio, so this never re-transcodes
 * or downgrades quality — it just combines two already-downloaded streams into one container.
 */
@Singleton
class FFmpegMediaProcessor @Inject constructor() : MediaProcessor {

    override val processorId: String = "ffmpeg"

    /** taskId -> FFmpegKit session id, so [cancel] can stop the right in-flight session. */
    private val activeSessions = ConcurrentHashMap<String, Long>()

    override fun merge(request: MergeRequest): Flow<ProcessingEvent> = callbackFlow {
        val arguments = arrayOf(
            "-y",
            "-i", request.videoPath,
            "-i", request.audioPath,
            "-map", "0:v:0",
            "-map", "1:a:0",
            "-c", "copy",
            request.outputPath,
        )
        val estimatedDurationMs = (request.estimatedDurationSeconds ?: 0L) * 1000

        val session = FFmpegKit.executeWithArgumentsAsync(
            arguments,
            { completedSession ->
                val returnCode = completedSession.returnCode
                when {
                    ReturnCode.isSuccess(returnCode) ->
                        trySend(ProcessingEvent.Completed(request.taskId, request.outputPath))

                    ReturnCode.isCancel(returnCode) ->
                        trySend(ProcessingEvent.Failed(request.taskId, "Merging was cancelled."))

                    else -> trySend(
                        ProcessingEvent.Failed(
                            request.taskId,
                            "FFmpeg couldn't combine the video and audio streams (code $returnCode).",
                        ),
                    )
                }
                close()
            },
            { /* raw FFmpeg log lines — not surfaced to the UI */ },
            { statistics: Statistics ->
                val percent = if (estimatedDurationMs > 0) {
                    ((statistics.time / estimatedDurationMs.toDouble()) * 100).toInt().coerceIn(0, 100)
                } else {
                    null
                }
                trySend(ProcessingEvent.Progress(request.taskId, percent))
            },
        )
        activeSessions[request.taskId] = session.sessionId

        awaitClose { activeSessions.remove(request.taskId) }
    }

    override suspend fun cancel(taskId: String) {
        activeSessions[taskId]?.let { sessionId -> FFmpegKit.cancel(sessionId) }
    }
}
