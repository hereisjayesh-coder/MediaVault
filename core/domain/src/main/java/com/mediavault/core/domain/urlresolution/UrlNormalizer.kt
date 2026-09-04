package com.mediavault.core.domain.urlresolution

import java.net.URI
import java.net.URISyntaxException

/**
 * A URL that has been validated, scheme/host-lowercased, and stripped of known tracking query
 * parameters — safe to hand to [SourceRegistry] and, from there, on to an extractor backend.
 */
data class NormalizedUrl(
    val scheme: String,
    val host: String,
    val path: String,
    val query: List<Pair<String, String>>,
) {
    /** The form that should actually be analyzed/routed — not necessarily what the user pasted. */
    fun toUrlString(): String {
        val queryPart = if (query.isEmpty()) "" else "?" + query.joinToString("&") { (key, value) -> if (value.isEmpty()) key else "$key=$value" }
        return "$scheme://$host$path$queryPart"
    }
}

/**
 * Turns whatever a user pastes into a well-formed, comparable [NormalizedUrl] — or `null` if
 * it isn't one. This is the single place that decides what counts as "a valid URL" for the
 * whole analysis pipeline; nothing downstream re-parses the raw string.
 *
 * Deliberately conservative about what it changes: scheme/host casing and a small, explicit
 * allowlist of tracking-only query parameters (below) are the only things stripped. Path
 * casing and every other query parameter are preserved untouched, since either can be load
 * -bearing (a case-sensitive post slug; YouTube's `v`/`list`/`t`; Instagram's `img_index`).
 */
object UrlNormalizer {

    /**
     * Query parameters that only ever carry attribution/analytics noise for the services
     * MediaVault supports — dropping them never changes what content a URL identifies.
     * Anything not in this list is preserved.
     */
    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "fbclid", "gclid", "gclsrc", "gbraid", "wbraid",
        "igshid", "igsh",
        "mc_cid", "mc_eid", "mibextid",
        "ref_src", "spm", "_ga", "si",
    )

    private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    fun normalize(rawInput: String): NormalizedUrl? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // A bare "reddit.com/..." paste (no scheme) is common enough to accept — assume https,
        // same as a browser address bar would.
        val withScheme = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"

        val uri = try {
            URI(withScheme)
        } catch (e: URISyntaxException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }

        val scheme = uri.scheme?.lowercase() ?: return null
        // Only ever http(s) reaches an extractor — rejecting anything else here (intent://,
        // javascript:, mailto:, ...) up front means no downstream code has to think about it.
        if (scheme != "http" && scheme != "https") return null

        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty()) return null

        val path = uri.rawPath.orEmpty()
        val query = parseQuery(uri.rawQuery).filterNot { (key, _) -> key.lowercase() in TRACKING_PARAMS }

        return NormalizedUrl(scheme = scheme, host = host, path = path, query = query)
    }

    private fun parseQuery(rawQuery: String?): List<Pair<String, String>> {
        if (rawQuery.isNullOrEmpty()) return emptyList()
        return rawQuery.split("&").mapNotNull { param ->
            if (param.isEmpty()) return@mapNotNull null
            val separatorIndex = param.indexOf('=')
            if (separatorIndex == -1) param to "" else param.substring(0, separatorIndex) to param.substring(separatorIndex + 1)
        }
    }
}
