package com.mediavault.core.domain.urlresolution

import com.mediavault.core.common.AppResult

/**
 * Safely follows HTTP redirects for a share/short link down to wherever it finally lands — the
 * *only* thing in MediaVault's analysis pipeline allowed to make a network call before a URL
 * has been confirmed to belong to a supported source. See [SourceRegistry.isShortLink] for
 * exactly which URLs ever reach this.
 *
 * Implementations must: follow http(s) redirects only, cap the number of hops, and never
 * resolve a redirect target that points at a loopback/private/link-local address — the caller
 * still separately re-validates the final URL's host against [SourceRegistry] before trusting
 * it, but this is the layer responsible for the redirect walk itself being safe.
 */
interface RedirectResolver {
    /** Returns the final URL after following redirects, or an [AppResult.Failure] for a network
     * failure, a redirect chain that's too long, or a redirect to an unsafe target. */
    suspend fun resolveFinalUrl(url: String): AppResult<String>
}
