package com.mediavault.app.download

import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.extractor.ExtractionEvent
import com.mediavault.core.domain.extractor.ExtractionRequest
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractorEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Per-URL configurable [ExtractorEngine] double — each playlist item's own URL can resolve to its own result. */
class FakeExtractorEngine : ExtractorEngine {

    override val engineId: String = "fake"
    override val engineVersion: String = "0.0.0"

    private val resultsByUrl = mutableMapOf<String, AppResult<ExtractionResult>>()
    val analyzeCalls = mutableListOf<String>()
    val cancelledTaskIds = mutableListOf<String>()

    fun setResult(url: String, result: AppResult<ExtractionResult>) {
        resultsByUrl[url] = result
    }

    override suspend fun canHandle(url: String): Boolean = true

    override suspend fun analyze(url: String, taskId: String): AppResult<ExtractionResult> {
        analyzeCalls.add(url)
        return resultsByUrl[url] ?: AppResult.Failure(AppError.Unknown("No fake result configured for $url"))
    }

    override fun download(request: ExtractionRequest): Flow<ExtractionEvent> = emptyFlow()

    override suspend fun cancel(taskId: String) {
        cancelledTaskIds.add(taskId)
    }
}
