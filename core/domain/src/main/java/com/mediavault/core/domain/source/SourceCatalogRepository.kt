package com.mediavault.core.domain.source

import com.mediavault.core.model.Source

/**
 * Sole owner of the Supported Sources catalog. Backed by static, generated data (see
 * `core/extractor-ytdlp/scripts/generate_source_catalog.py`) — this is deliberately not a
 * live network call, so the UI never depends on yt-dlp internals or a server round-trip
 * just to show what's supported.
 */
interface SourceCatalogRepository {
    /** Loads the catalog once; cheap to call repeatedly — implementations cache after the first load. */
    suspend fun getCatalog(): SourceCatalog
}

data class SourceCatalog(
    val sources: List<Source>,
    val metadata: SourceCatalogMetadata,
)

data class SourceCatalogMetadata(
    /** Which extraction engine this catalog was generated from, e.g. "ytdlp". */
    val engineId: String,
    /** The engine version the catalog was generated against — shown in the UI so support claims stay honest. */
    val engineVersion: String,
    val generatedAtEpochMs: Long,
)
