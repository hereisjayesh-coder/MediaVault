package com.mediavault.core.extractor.instaloader

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
import com.mediavault.core.domain.extractor.ExtractionStage
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.extractor.instaloader.json.InstaloaderPostJson
import com.mediavault.core.extractor.instaloader.json.instaloaderJson
import com.mediavault.core.extractor.instaloader.json.toExtractionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
 * [ExtractorEngine] backed by Instaloader, running inside the same embedded Python
 * interpreter (Chaquopy) `YtDlpExtractorEngine` uses. All Instaloader/Chaquopy specifics are
 * private to this class — callers only ever see [ExtractorEngine]. Never used directly by the
 * UI/download layer; [com.mediavault.app.extractor.CompositeExtractorEngine] is the only thing
 * that knows this engine exists, per the project's "keep backend selection centralized" rule.
 *
 * Anonymous by design — see `mediavault_instaloader.py`'s own docstring: this engine never
 * logs in or attempts to access private/login-gated content. A post that genuinely needs
 * authentication surfaces as a clean [AppError.Unsupported] (see [InstaloaderErrorMapper]),
 * never bypassed.
 */
@Singleton
class InstaloaderExtractorEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExtractorEngine {

    override val engineId: String = "instaloader"
    override val engineVersion: String = PINNED_INSTALOADER_VERSION

    private val initMutex = Mutex()

    @Volatile
    private var bridgeModule: PyObject? = null

    /** taskId -> worker thread, so [cancel] can interrupt a specific in-flight call — same best-effort contract as `YtDlpExtractorEngine` (Chaquopy has no built-in call-cancellation primitive). */
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
            val post = instaloaderJson.decodeFromString(InstaloaderPostJson.serializer(), rawJson)
            val result = post.toExtractionResult() as ExtractionResult.Collection
            if (result.collection.items.isEmpty()) {
                // A genuinely empty post (no nodes at all) — the mapper now keeps every item
                // regardless of type (image or video), so this no longer happens just because a
                // carousel happened to be all-video; that case now correctly succeeds instead.
                AppResult.Failure(AppError.Unsupported("This post doesn't contain any downloadable media."))
            } else {
                AppResult.Success(result)
            }
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

    /**
     * Downloads a single image (identified by [ExtractionRequest.formatId], the item's
     * 1-based index within the post — see `mediavault_instaloader.py`'s `download()`) to
     * [ExtractionRequest.destinationPath]. Unlike yt-dlp's transfer, there is no native
     * progress-hook mechanism to poll — an image is one HTTP GET, not a segmented/streamed
     * transfer — so this reports one indeterminate [ExtractionEvent.Progress] before the
     * blocking call, matching the existing "no known total" indeterminate-progress-bar
     * treatment `DownloadTaskCard` already has, then a single terminal event.
     */
    override fun download(request: ExtractionRequest): Flow<ExtractionEvent> = flow {
        emit(
            ExtractionEvent.Progress(
                taskId = request.taskId,
                bytesTransferred = 0L,
                totalBytes = null,
                stage = ExtractionStage.DOWNLOADING,
            ),
        )

        val module = try {
            ensureModule()
        } catch (e: Exception) {
            emit(ExtractionEvent.Failed(request.taskId, "The extraction engine failed to start.", e))
            return@flow
        }

        File(request.destinationPath).parentFile?.mkdirs()

        try {
            val finalPath = runOnWorkerThread(request.taskId) {
                module.callAttr(
                    FUNCTION_DOWNLOAD,
                    request.taskId,
                    request.sourceUrl,
                    request.formatId,
                    request.destinationPath,
                ).toString()
            }
            // A single HTTP GET has no intermediate progress to report, but the real,
            // now-known byte count still needs to reach MediaVaultDownloadEngine — it only
            // ever records `bytesTransferred` from a Progress event, never by independently
            // stat-ing the file, so skipping this would leave every downloaded image showing
            // no size anywhere in the app (Library, Downloads, Details) despite the file
            // being real and fully downloaded.
            val bytesWritten = File(finalPath).length()
            emit(
                ExtractionEvent.Progress(
                    taskId = request.taskId,
                    bytesTransferred = bytesWritten,
                    totalBytes = bytesWritten,
                    stage = ExtractionStage.DOWNLOADING,
                ),
            )
            emit(ExtractionEvent.Completed(request.taskId, finalPath))
        } catch (e: CancellationException) {
            throw e
        } catch (e: PyException) {
            emit(ExtractionEvent.Failed(request.taskId, e.toAppError().message, e))
        } catch (e: Exception) {
            emit(ExtractionEvent.Failed(request.taskId, e.message ?: "Unknown download error.", e))
        }
    }

    /** Stops an in-flight [analyze] or [download] call for [taskId] — best-effort, same contract as `YtDlpExtractorEngine.cancel`. */
    override suspend fun cancel(taskId: String) {
        activeCalls[taskId]?.interrupt()
    }

    private suspend fun ensureModule(): PyObject {
        bridgeModule?.let { return it }
        return initMutex.withLock {
            bridgeModule?.let { return it }
            val module = runOnWorkerThread(taskId = null) {
                // Chaquopy's Python interpreter is one shared process-wide instance — starting
                // it here is safe and idempotent even if YtDlpExtractorEngine already did.
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context))
                }
                Python.getInstance().getModule(BRIDGE_MODULE_NAME)
            }
            bridgeModule = module
            module
        }
    }

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
            }, "instaloader-worker")
            thread.isDaemon = true

            if (taskId != null) activeCalls[taskId] = thread
            continuation.invokeOnCancellation { thread.interrupt() }
            thread.start()
        }

    private companion object {
        const val BRIDGE_MODULE_NAME = "mediavault_instaloader"
        const val FUNCTION_CAN_HANDLE = "can_handle"
        const val FUNCTION_ANALYZE = "analyze"
        const val FUNCTION_DOWNLOAD = "download"

        /** Kept in sync with the pip pin in this module's build.gradle.kts. */
        const val PINNED_INSTALOADER_VERSION = "4.15.3"
    }
}
