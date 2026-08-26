package com.mediavault.core.domain.source

import com.mediavault.core.model.Source
import com.mediavault.core.model.SourceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDescriptionsTest {

    private fun source(
        id: String,
        categories: List<SourceCategory> = listOf(SourceCategory.VIDEO),
        description: String? = null,
    ) = Source(
        id = id,
        displayName = id,
        domain = "$id.com",
        extractorIds = listOf(id),
        categories = categories,
        aliases = emptyList(),
        isSupported = true,
        faviconUrl = null,
        description = description,
    )

    @Test
    fun `every curated entry is a non-blank, compact one-to-two-sentence description`() {
        CuratedSourceDescriptions.byId.values.forEach { description ->
            assertTrue("blank curated description", description.isNotBlank())
            assertTrue("curated description too long: $description", description.length <= 200)
        }
    }

    @Test
    fun `a major service resolves its exact curated description`() {
        val instagram = source("instagram")

        assertEquals(
            "Social media platform for sharing photos, videos, Stories and Reels.",
            instagram.displayDescription(),
        )
        assertEquals(CuratedSourceDescriptions.byId.getValue("instagram"), instagram.displayDescription())
    }

    @Test
    fun `a source with no curated entry falls back to a category-based generic description`() {
        val obscure = source("some-obscure-service-xyz", categories = listOf(SourceCategory.MUSIC))

        val description = obscure.displayDescription()

        assertTrue(description.contains("music"))
        assertTrue(description.contains("MediaVault"))
    }

    @Test
    fun `an already-populated description is never overwritten by the curated map`() {
        val preset = source("youtube", description = "Custom description set elsewhere.")

        assertEquals("Custom description set elsewhere.", preset.displayDescription())
    }

    @Test
    fun `withCuratedDescriptions enriches matching sources and leaves others null`() {
        val catalog = SourceCatalog(
            sources = listOf(source("youtube"), source("some-obscure-service-xyz")),
            metadata = SourceCatalogMetadata(engineId = "ytdlp", engineVersion = "test", generatedAtEpochMs = 0L),
        )

        val enriched = catalog.withCuratedDescriptions()

        assertEquals(CuratedSourceDescriptions.byId.getValue("youtube"), enriched.sources.first { it.id == "youtube" }.description)
        assertNull(enriched.sources.first { it.id == "some-obscure-service-xyz" }.description)
    }
}
