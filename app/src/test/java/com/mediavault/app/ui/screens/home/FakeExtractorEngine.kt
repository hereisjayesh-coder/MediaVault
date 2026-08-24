package com.mediavault.app.ui.screens.home

import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.extractor.ExtractionEvent
import com.mediavault.core.domain.extractor.ExtractionRequest
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractorEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Test double standing in for [com.mediavault.core.extractor.ytdlp.YtDlpExtractorEngine].
 *
 * If [nextResult] is set, [analyze] returns it immediately. Otherwise [analyze] suspends
 * until either [completePending] or [cancel] is called, which is what lets tests exercise
 * the loading and cancellation states.
 */
class FakeExtractorEngine : ExtractorEngine {

    override val engineId: String = "fake"
    override val engineVersion: String = "0.0.0"

    var nextResult: AppResult<ExtractionResult>? = null
    val analyzeCalls = mutableListOf<Pair<String, String>>()
    val cancelledTaskIds = mutableListOf<String>()

    private var pending: CompletableDeferred<AppResult<ExtractionResult>>? = null

    override suspend fun canHandle(url: String): Boolean = true

    override suspend fun analyze(url: String, taskId: String): AppResult<ExtractionResult> {
        analyzeCalls.add(url to taskId)
        nextResult?.let { return it }
        val deferred = CompletableDeferred<AppResult<ExtractionResult>>()
        pending = deferred
        return deferred.await()
    }

    override fun download(request: ExtractionRequest): Flow<ExtractionEvent> = emptyFlow()

    override suspend fun cancel(taskId: String) {
        cancelledTaskIds.add(taskId)
        pending?.cancel()
    }

    fun completePending(result: AppResult<ExtractionResult>) {
        pending?.complete(result)
    }
}
