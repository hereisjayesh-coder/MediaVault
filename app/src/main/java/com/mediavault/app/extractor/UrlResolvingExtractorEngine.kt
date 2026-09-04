package com.mediavault.app.extractor

import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.extractor.ExtractionEvent
import com.mediavault.core.domain.extractor.ExtractionRequest
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.domain.urlresolution.RedirectResolver
import com.mediavault.core.domain.urlresolution.SourceRegistry
import com.mediavault.core.domain.urlresolution.UrlNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * The URL-resolution layer: the single entry point the rest of the app (the UI, playlist
 * re-analysis) goes through to turn a *pasted* URL into one [CompositeExtractorEngine] can
 * route to a backend. Bound as [ExtractorEngine] in place of [CompositeExtractorEngine] (see
 * `ExtractorModule`), which it wraps rather than replaces — backend selection itself stays
 * entirely [CompositeExtractorEngine]'s job.
 *
 * **Why this exists.** Every backend's [ExtractorEngine.canHandle] is a cheap, offline,
 * regex-shaped check (yt-dlp's own per-site `_VALID_URL`, for instance) — it can only ever
 * recognize a URL that already contains the content id in a shape it knows. A share/short link
 * (a Reddit `/s/...` link, `redd.it`, a Facebook `/share/...` link, `fb.watch`) carries no
 * content id at all — it's a redirect, and the real id only appears after that redirect is
 * followed. No amount of adding more regexes fixes that; the redirect has to actually be
 * resolved first. That is exactly what this class does, and *only* what it does: it never
 * second-guesses which backend should run, only what URL that backend should see.
 *
 * **What it changes vs. what it doesn't.** [UrlNormalizer] and [SourceRegistry] together
 * decide two things, cheaply and mostly offline: whether the pasted text is even a well-formed
 * URL, and whether it's one of the known share/short shapes that needs a redirect resolved
 * before anything else happens (see [SourceRegistry.isShortLink]). A URL that isn't one of
 * those shapes — including a canonical link for any of the hundreds of other sites yt-dlp
 * itself recognizes, not just MediaVault's five named sources — is passed straight through to
 * [delegate] unchanged. This is deliberately not a rewrite of routing: it only ever touches the
 * narrow slice of URLs that would otherwise be rejected before a backend gets a chance to see
 * them.
 *
 * **Anti-spoofing.** After a redirect is resolved, the final URL's host is re-validated against
 * [SourceRegistry] before it is ever handed to [delegate] — a share link is only ever allowed
 * to redirect to its own source's domain family, never to an arbitrary third-party host,
 * closing off redirect-based source spoofing.
 */
@Singleton
class UrlResolvingExtractorEngine @Inject constructor(
    private val delegate: CompositeExtractorEngine,
    private val redirectResolver: RedirectResolver,
) : ExtractorEngine {

    override val engineId: String = delegate.engineId
    override val engineVersion: String = delegate.engineVersion

    override suspend fun canHandle(url: String): Boolean {
        val resolved = resolveForRouting(url) as? AppResult.Success ?: return false
        return delegate.canHandle(resolved.data)
    }

    override suspend fun analyze(url: String, taskId: String): AppResult<ExtractionResult> =
        when (val resolved = resolveForRouting(url)) {
            is AppResult.Failure -> resolved
            is AppResult.Success -> delegate.analyze(resolved.data, taskId)
        }

    /** Downloads always run against a previously analyzed [ExtractionRequest.sourceUrl] — by
     * the time one exists, it already came out of a successful [analyze] (the canonical
     * `webpageUrl` an extractor itself reported), so it needs no further resolution here. */
    override fun download(request: ExtractionRequest): Flow<ExtractionEvent> = delegate.download(request)

    override suspend fun cancel(taskId: String) = delegate.cancel(taskId)

    /**
     * Normalizes [rawUrl] and, if it's a known share/short link, resolves it to its final
     * destination — re-validating that destination against [SourceRegistry] before trusting it.
     * Returns the URL string [delegate] should actually analyze/route, or the friendly failure
     * to surface as-is (malformed input, a redirect that failed, or a redirect that landed
     * somewhere no supported source recognizes).
     */
    private suspend fun resolveForRouting(rawUrl: String): AppResult<String> {
        val normalized = UrlNormalizer.normalize(rawUrl)
            ?: return AppResult.Failure(AppError.Unsupported(MALFORMED_URL_MESSAGE))

        if (!SourceRegistry.isShortLink(normalized)) {
            return AppResult.Success(normalized.toUrlString())
        }

        return when (val resolved = redirectResolver.resolveFinalUrl(normalized.toUrlString())) {
            is AppResult.Failure -> resolved
            is AppResult.Success -> {
                val finalNormalized = UrlNormalizer.normalize(resolved.data)
                if (finalNormalized == null || SourceRegistry.sourceOf(finalNormalized) == null) {
                    AppResult.Failure(UNRECOGNIZED_URL_ERROR)
                } else {
                    AppResult.Success(finalNormalized.toUrlString())
                }
            }
        }
    }

    private companion object {
        const val MALFORMED_URL_MESSAGE = "That doesn't look like a valid link."
        val UNRECOGNIZED_URL_ERROR = AppError.Unsupported("This link isn't from a source MediaVault's extractor recognizes yet.")
    }
}
