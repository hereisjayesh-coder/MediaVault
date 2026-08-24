package com.mediavault.core.extractor.ytdlp

import android.content.Context
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.extractor.ExtractionEvent
import com.mediavault.core.domain.extractor.ExtractionRequest
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.extractor.ytdlp.json.YtDlpInfoJson
import com.mediavault.core.extractor.ytdlp.json.toExtractionResult
import com.mediavault.core.extractor.ytdlp.json.ytDlpJson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

/**
 * [ExtractorEngine] backed by yt-dlp running inside an embedded Python interpreter
 * (Chaquopy). All yt-dlp/Chaquopy specifics are private to this class — callers only ever
 * see [ExtractorEngine].
 */
@Singleton
class YtDlpExtractorEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExtractorEngine {

    override val engineId: String = "ytdlp"
    override val engineVersion: String = PINNED_YTDLP_VERSION

    private val initMutex = Mutex()

    @Volatile
    private var bridgeModule: PyObject? = null

    /** taskId -> worker thread, so [cancel] can interrupt a specific in-flight call. */
    private val activeCalls = ConcurrentHashMap<String, Thread>()

    override suspend fun canHandle(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val module = ensureModule()
            runOnWorkerThread(taskId = null) {
                module.callAttr(FUNCTION_CAN_HANDLE, url).toBoolean()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun analyze(url: String, taskId: String): AppResult<ExtractionResult> {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            return AppResult.Failure(AppError.Unsupported("Enter a URL to analyze."))
        }

        return try {
            val module = ensureModule()
            val rawJson = runOnWorkerThread(taskId) {
                module.callAttr(FUNCTION_ANALYZE, trimmedUrl).toString()
            }
            val info = ytDlpJson.decodeFromString(YtDlpInfoJson.serializer(), rawJson)
            AppResult.Success(info.toExtractionResult())
        } catch (e: CancellationException) {
            throw e
        } catch (e: PyException) {
            AppResult.Failure(e.toAppError())
        } catch (e: SerializationException) {
            AppResult.Failure(AppError.Unknown("Couldn't read the extractor's response.", e))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Unknown extraction error.", e))
        }
    }

    override fun download(request: ExtractionRequest): Flow<ExtractionEvent> = flow {
        emit(
            ExtractionEvent.Failed(
                taskId = request.taskId,
                message = "Downloading is not implemented yet.",
            ),
        )
    }

    override suspend fun cancel(taskId: String) {
        activeCalls[taskId]?.interrupt()
    }

    private suspend fun ensureModule(): PyObject {
        bridgeModule?.let { return it }
        return initMutex.withLock {
            bridgeModule?.let { return it }
            val module = runOnWorkerThread(taskId = null) {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context))
                }
                Python.getInstance().getModule(BRIDGE_MODULE_NAME)
            }
            bridgeModule = module
            module
        }
    }

    /**
     * Runs [block] on a dedicated background thread and suspends until it finishes.
     *
     * Chaquopy calls are blocking native calls that don't cooperate with coroutine
     * cancellation on their own, so cancellation here is best-effort: cancelling the
     * coroutine (or calling [cancel] with a matching [taskId]) interrupts the worker
     * thread and lets the caller stop waiting immediately, but yt-dlp's own network call
     * may keep running briefly in the background until it next checks for interruption.
     */
    private suspend fun <T> runOnWorkerThread(taskId: String?, block: () -> T): T =
        suspendCancellableCoroutine { continuation ->
            val thread = Thread({
                try {
                    val result = block()
                    if (continuation.isActive) continuation.resumeWith(Result.success(result))
                } catch (t: Throwable) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(t))
                } finally {
                    if (taskId != null) activeCalls.remove(taskId)
                }
            }, "ytdlp-worker")
            thread.isDaemon = true

            if (taskId != null) activeCalls[taskId] = thread
            continuation.invokeOnCancellation { thread.interrupt() }
            thread.start()
        }

    private companion object {
        const val BRIDGE_MODULE_NAME = "mediavault_ytdlp"
        const val FUNCTION_CAN_HANDLE = "can_handle"
        const val FUNCTION_ANALYZE = "analyze"

        /** Kept in sync with the pip pin in this module's build.gradle.kts. */
        const val PINNED_YTDLP_VERSION = "2026.8.19"
    }
}
