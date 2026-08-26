package com.mediavault.core.model

/**
 * One service in the Supported Sources catalog — one row per real-world site/service, not
 * one per extractor variant. A single service (e.g. YouTube) is usually backed by several
 * extractor classes internally ([extractorIds]) which the catalog generator groups together.
 */
data class Source(
    /** Stable slug derived from the service's domain/name, e.g. "youtube". Not a display value. */
    val id: String,
    val displayName: String,
    /** Canonical domain, e.g. "youtube.com" — null for keyword-only/utility extractors. */
    val domain: String?,
    /** Every underlying extractor identifier grouped into this service, for future analysis routing. */
    val extractorIds: List<String>,
    /** First entry is the primary category; a source may reasonably belong to more than one. */
    val categories: List<SourceCategory>,
    /** Lowercased, searchable terms — display name, domain, and extractor ids. */
    val aliases: List<String>,
    /** Whether the current extraction engine reports this source as working — not a live check. */
    val isSupported: Boolean,
    /** Suggested favicon URL to fetch/cache; null when no domain is known. */
    val faviconUrl: String?,
    /**
     * Hand-curated, human-readable description ("what is this platform / what media does it
     * carry"), e.g. "Social media platform for sharing photos, videos, Stories and Reels."
     * Null for the vast majority of the catalog, which has no curated entry — see
     * `core/domain/source/SourceDescriptions.kt` for the curated map and the generic,
     * category-based fallback shown instead.
     */
    val description: String? = null,
)
