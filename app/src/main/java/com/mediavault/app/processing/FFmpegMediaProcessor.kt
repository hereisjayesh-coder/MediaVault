package com.mediavault.app.processing

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.mediavault.core.domain.processing.MediaProcessor
import com.mediavault.core.domain.processing.MergeRequest
import com.mediavault.core.domain.processing.ProcessingEvent
import java.util.Locale
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
        val arguments = buildFfmpegArguments(request)
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

/**
 * `-i video -i audio0 -i audio1 ... -map 0:v:0 -map 1:a:0 -map 2:a:0 ... -c copy
 * -metadata:s:a:0 language=xxx ... output` — one video input, one input per
 * [MergeRequest.audioTracks] entry (in the order given, so track *N* in the output is always
 * [MergeRequest.audioTracks]`[N]`), each tagged with its own language when known. Exactly the
 * original single-video-plus-single-audio command when [MergeRequest.audioTracks] has one entry
 * — multi-track muxing is a generalization of that same command, not a second code path.
 */
private fun buildFfmpegArguments(request: MergeRequest): Array<String> {
    val arguments = mutableListOf("-y", "-i", request.videoPath)
    request.audioTracks.forEach { track -> arguments += listOf("-i", track.path) }

    arguments += listOf("-map", "0:v:0")
    request.audioTracks.indices.forEach { index -> arguments += listOf("-map", "${index + 1}:a:0") }

    arguments += listOf("-c", "copy")

    request.audioTracks.forEachIndexed { index, track ->
        val languageTag = track.languageCode?.let(::isoLanguageTag) ?: return@forEachIndexed
        arguments += listOf("-metadata:s:a:$index", "language=$languageTag")
    }

    arguments += request.outputPath
    return arguments.toTypedArray()
}

/**
 * [languageCode] (whatever the source itself reported — e.g. "en", "zh-Hans") to the 3-letter
 * ISO 639-2 tag FFmpeg/Matroska metadata expects (e.g. "eng"), via the JDK's own locale data —
 * never a guessed/hand-maintained code table. Null when [Locale] can't resolve a 3-letter form
 * for this code (rare); the track is still muxed in, just without a language tag.
 */
private fun isoLanguageTag(languageCode: String): String? =
    runCatching { Locale.forLanguageTag(languageCode).isO3Language.takeIf { it.isNotBlank() } }.getOrNull()
