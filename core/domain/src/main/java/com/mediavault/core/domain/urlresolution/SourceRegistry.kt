package com.mediavault.core.domain.urlresolution

/** A service MediaVault has a dedicated extractor path for. */
enum class SupportedSource {
    YOUTUBE, INSTAGRAM, FACEBOOK, REDDIT, TWITTER,
}

private data class SourceDefinition(
    val id: SupportedSource,
    /** Every host (and its subdomains) that belongs to this source's domain family — canonical
     * and mobile/www variants alike, since suffix matching covers all of them at once. */
    val hostSuffixes: Set<String>,
    /** Hosts that are *always* a redirect/shortlink for this source (e.g. `redd.it`) — the URL
     * carries no content id of its own and must be resolved before any extractor can recognize
     * what it points to. */
    val shortLinkHosts: Set<String> = emptySet(),
    /** Path shapes that are share/redirect links on an otherwise-canonical host of this source
     * (e.g. `facebook.com/share/...`, `reddit.com/r/x/s/...`) — same "must resolve first" rule
     * as [shortLinkHosts], just gated by path instead of host. */
    val shortLinkPathPatterns: List<Regex> = emptyList(),
) {
    fun matchesHost(host: String): Boolean = hostSuffixes.any { host == it || host.endsWith(".$it") }
}

/**
 * Centralized table of the URL shapes MediaVault's own backends are built to recognize —
 * the one place that knows what counts as "an Instagram URL" etc., instead of that knowledge
 * being scattered across the UI or individual extractors.
 *
 * This registry is deliberately narrow: it exists only to answer two questions cheaply and
 * offline — "does this host belong to one of MediaVault's named sources" ([sourceOf]) and "is
 * this a share/short link that must be redirect-resolved before an extractor's own regex-based
 * `canHandle` has any chance of recognizing it" ([isShortLink]). It is not a general "is this
 * URL supported" gate — a host this registry has never heard of still reaches the extractor
 * layer unchanged and may well be one of the hundreds of other sites yt-dlp itself recognizes;
 * see [com.mediavault.core.domain.extractor.ExtractorEngine.canHandle] for that check.
 */
object SourceRegistry {

    private val DEFINITIONS = listOf(
        SourceDefinition(
            id = SupportedSource.YOUTUBE,
            hostSuffixes = setOf("youtube.com", "youtube-nocookie.com", "youtu.be"),
        ),
        SourceDefinition(
            id = SupportedSource.INSTAGRAM,
            hostSuffixes = setOf("instagram.com"),
            shortLinkPathPatterns = listOf(Regex("^/share/")),
        ),
        SourceDefinition(
            id = SupportedSource.FACEBOOK,
            hostSuffixes = setOf("facebook.com", "fb.com", "fb.watch"),
            shortLinkHosts = setOf("fb.watch"),
            shortLinkPathPatterns = listOf(Regex("^/share/")),
        ),
        SourceDefinition(
            id = SupportedSource.REDDIT,
            hostSuffixes = setOf("reddit.com", "redd.it", "redditmedia.com"),
            shortLinkHosts = setOf("redd.it"),
            shortLinkPathPatterns = listOf(Regex("/s/[^/?#]+/?$")),
        ),
        SourceDefinition(
            id = SupportedSource.TWITTER,
            hostSuffixes = setOf("twitter.com", "x.com"),
        ),
    )

    /** Which supported source (if any) [url]'s host belongs to — true for that source's share
     * links too, not just its canonical URLs. */
    fun sourceOf(url: NormalizedUrl): SupportedSource? = DEFINITIONS.firstOrNull { it.matchesHost(url.host) }?.id

    /** True when [url] is a known share/short link that carries no content id of its own, so a
     * redirect must be resolved before it is routed to an extractor. */
    fun isShortLink(url: NormalizedUrl): Boolean = DEFINITIONS.any { definition ->
        definition.shortLinkHosts.any { url.host == it || url.host.endsWith(".$it") } ||
            (definition.matchesHost(url.host) && definition.shortLinkPathPatterns.any { it.containsMatchIn(url.path) })
    }
}
