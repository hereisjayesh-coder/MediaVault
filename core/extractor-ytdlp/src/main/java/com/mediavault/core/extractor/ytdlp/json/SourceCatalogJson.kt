package com.mediavault.core.extractor.ytdlp.json

import com.mediavault.core.domain.source.SourceCatalog
import com.mediavault.core.domain.source.SourceCatalogMetadata
import com.mediavault.core.model.Source
import com.mediavault.core.model.SourceCategory
import kotlinx.serialization.Serializable

/** Mirrors the JSON shape written by `core/extractor-ytdlp/scripts/generate_source_catalog.py`. */
@Serializable
data class SourceCatalogJson(
    val engineId: String,
    val engineVersion: String,
    val generatedAtEpochMs: Long,
    val sources: List<SourceJson>,
)

@Serializable
data class SourceJson(
    val id: String,
    val displayName: String,
    val domain: String? = null,
    val extractorIds: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val isSupported: Boolean = true,
    val faviconUrl: String? = null,
)

fun SourceCatalogJson.toSourceCatalog(): SourceCatalog = SourceCatalog(
    sources = sources.map { it.toSource() },
    metadata = SourceCatalogMetadata(
        engineId = engineId,
        engineVersion = engineVersion,
        generatedAtEpochMs = generatedAtEpochMs,
    ),
)

private fun SourceJson.toSource(): Source = Source(
    id = id,
    displayName = displayName,
    domain = domain,
    extractorIds = extractorIds,
    // Unknown/future category strings degrade to OTHER instead of failing catalog load.
    categories = categories.mapNotNull { raw -> runCatching { SourceCategory.valueOf(raw) }.getOrNull() }
        .ifEmpty { listOf(SourceCategory.OTHER) },
    aliases = aliases,
    isSupported = isSupported,
    faviconUrl = faviconUrl,
)
