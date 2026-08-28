package com.mediavault.app.extractor

import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.extractor.ExtractionEvent
import com.mediavault.core.domain.extractor.ExtractionRequest
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractorEngine
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The one place that knows more than one [ExtractorEngine] backend exists — everything else
 * (the UI, `DownloadEngine`) depends only on the plain [ExtractorEngine] interface. See
 * PROJECT_MASTER.md's Instaloader decision log entry for the full architecture reasoning.
 *
 * **Routing.** yt-dlp stays primary: [ENGINE_PRIORITY_ORDER] tries it first. A backend only
 * gets a turn when its own [ExtractorEngine.canHandle] agrees the URL is its territory — a
 * cheap, local, offline check (see each backend's own `can_handle`), so a host neither backend
 * recognizes never triggers a wasted network call from either. Within that candidate set,
 * [analyze] tries each backend in priority order and only moves on to the next when the
 * current one *fails* — the common real case being yt-dlp correctly recognizing an Instagram
 * URL but finding no video to extract, at which point Instaloader gets a genuine chance
 * instead of the URL being rejected outright. This engine never inspects a URL's host itself —
 * every platform-specific decision stays inside each backend's own `canHandle`/error mapping,
 * so this class has no Instagram/Reddit/Facebook-specific knowledge at all.
 *
 * **Which backend handles a later [download]/[cancel].** Two backends can both legitimately
 * recognize the same URL (an Instagram post: yt-dlp for a Reel, Instaloader for an image) —
 * [ExtractionRequest.preferredEngineId] (set by whoever built the request, when it's known) is
 * checked first; failing that, whichever backend's [analyze] most recently *succeeded* for
 * that [ExtractionRequest.sourceUrl] (remembered in [engineForSourceUrl] for this process's
 * lifetime); failing that (a cold process with no memory of either), the first candidate in
 * priority order — correct for the common case where only one backend would ever really claim
 * the URL, and a graceful, clearly-erroring guess otherwise.
 *
 * **Adding a third backend** is exactly: implement [ExtractorEngine] in its own module, add
 * one `@IntoSet` binding in `ExtractorModule`, and — only if it should ever take priority over
 * an existing backend for a URL more than one recognizes — add its
 * [ExtractorEngine.engineId] to [ENGINE_PRIORITY_ORDER]. Nothing here or in the UI changes.
 */
@Singleton
class CompositeExtractorEngine @Inject constructor(
    engines: Set<@JvmSuppressWildcards ExtractorEngine>,
) : ExtractorEngine {

    private val orderedEngines: List<ExtractorEngine> = engines.sortedBy { engine ->
        ENGINE_PRIORITY_ORDER.indexOf(engine.engineId).let { if (it == -1) ENGINE_PRIORITY_ORDER.size else it }
    }

    override val engineId: String = "composite"
    override val engineVersion: String = orderedEngines.joinToString(separator = ",") { "${it.engineId}:${it.engineVersion}" }

    private val engineForSourceUrl = ConcurrentHashMap<String, ExtractorEngine>()

    override suspend fun canHandle(url: String): Boolean = orderedEngines.any { it.canHandle(url) }

    override suspend fun analyze(url: String, taskId: String): AppResult<ExtractionResult> {
        val candidates = orderedEngines.filter { it.canHandle(url) }
        if (candidates.isEmpty()) {
            return AppResult.Failure(UNRECOGNIZED_URL_ERROR)
        }

        var lastFailure: AppResult.Failure? = null
        for (engine in candidates) {
            when (val result = engine.analyze(url, taskId)) {
                is AppResult.Success -> {
                    engineForSourceUrl[url] = engine
                    return result
                }
                is AppResult.Failure -> lastFailure = result
            }
        }
        // Every candidate failed — the most specific (last-tried) backend's failure is the
        // most informative one to surface, never a generic "unsupported" that hides it.
        return lastFailure ?: AppResult.Failure(UNRECOGNIZED_URL_ERROR)
    }

    override fun download(request: ExtractionRequest): Flow<ExtractionEvent> = flow {
        val engine = resolveEngineFor(request)
        if (engine == null) {
            emit(ExtractionEvent.Failed(request.taskId, UNRECOGNIZED_URL_ERROR.message))
            return@flow
        }
        emitAll(engine.download(request))
    }

    private suspend fun resolveEngineFor(request: ExtractionRequest): ExtractorEngine? =
        request.preferredEngineId?.let { id -> orderedEngines.firstOrNull { it.engineId == id } }
            ?: engineForSourceUrl[request.sourceUrl]
            ?: orderedEngines.firstOrNull { it.canHandle(request.sourceUrl) }

    override suspend fun cancel(taskId: String) {
        // Cheap and safe to broadcast: each backend's own cancel() is a documented no-op for
        // an id it doesn't recognize.
        orderedEngines.forEach { it.cancel(taskId) }
    }

    private companion object {
        val ENGINE_PRIORITY_ORDER = listOf("ytdlp", "instaloader")
        val UNRECOGNIZED_URL_ERROR = AppError.Unsupported("This link isn't from a source MediaVault's extractor recognizes yet.")
    }
}
