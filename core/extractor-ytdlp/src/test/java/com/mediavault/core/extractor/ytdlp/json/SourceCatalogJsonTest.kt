package com.mediavault.core.extractor.ytdlp.json

import com.mediavault.core.model.SourceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCatalogJsonTest {

    @Test
    fun `maps engine metadata`() {
        val catalog = decode(SAMPLE_JSON)

        assertEquals("ytdlp", catalog.metadata.engineId)
        assertEquals("2026.08.19", catalog.metadata.engineVersion)
        assertEquals(1_700_000_000_000L, catalog.metadata.generatedAtEpochMs)
    }

    @Test
    fun `maps a fully-populated source`() {
        val source = decode(SAMPLE_JSON).sources.first { it.id == "youtube" }

        assertEquals("YouTube", source.displayName)
        assertEquals("youtube.com", source.domain)
        assertEquals(listOf("Youtube", "YoutubeTab"), source.extractorIds)
        assertEquals(listOf(SourceCategory.VIDEO, SourceCategory.MUSIC), source.categories)
        assertEquals(listOf("youtube", "youtube.com"), source.aliases)
        assertTrue(source.isSupported)
        assertEquals("https://example.com/favicon?domain=youtube.com", source.faviconUrl)
    }

    @Test
    fun `a source with no domain maps favicon and domain to null`() {
        val source = decode(SAMPLE_JSON).sources.first { it.id == "generic" }

        assertNull(source.domain)
        assertNull(source.faviconUrl)
    }

    @Test
    fun `an unrecognized category string falls back to OTHER instead of failing`() {
        val source = decode(SAMPLE_JSON).sources.first { it.id == "future-service" }

        assertEquals(listOf(SourceCategory.OTHER), source.categories)
    }

    @Test
    fun `a source with an empty categories list falls back to OTHER`() {
        val source = decode(SAMPLE_JSON).sources.first { it.id == "no-category" }

        assertEquals(listOf(SourceCategory.OTHER), source.categories)
    }

    @Test
    fun `an unsupported source keeps isSupported false`() {
        val source = decode(SAMPLE_JSON).sources.first { it.id == "no-category" }

        assertTrue(!source.isSupported)
    }

    private fun decode(json: String) =
        ytDlpJson.decodeFromString(SourceCatalogJson.serializer(), json).toSourceCatalog()

    private companion object {
        val SAMPLE_JSON = """
            {
              "engineId": "ytdlp",
              "engineVersion": "2026.08.19",
              "generatedAtEpochMs": 1700000000000,
              "sources": [
                {
                  "id": "youtube",
                  "displayName": "YouTube",
                  "domain": "youtube.com",
                  "extractorIds": ["Youtube", "YoutubeTab"],
                  "categories": ["VIDEO", "MUSIC"],
                  "aliases": ["youtube", "youtube.com"],
                  "isSupported": true,
                  "faviconUrl": "https://example.com/favicon?domain=youtube.com"
                },
                {
                  "id": "generic",
                  "displayName": "Generic",
                  "domain": null,
                  "extractorIds": ["Generic"],
                  "categories": ["OTHER"],
                  "aliases": ["generic"],
                  "isSupported": true,
                  "faviconUrl": null
                },
                {
                  "id": "future-service",
                  "displayName": "Future Service",
                  "extractorIds": ["FutureService"],
                  "categories": ["SOME_NEW_CATEGORY_NOT_YET_KNOWN"],
                  "aliases": ["future-service"],
                  "isSupported": true
                },
                {
                  "id": "no-category",
                  "displayName": "No Category",
                  "extractorIds": ["NoCategory"],
                  "categories": [],
                  "aliases": ["no-category"],
                  "isSupported": false
                }
              ]
            }
        """.trimIndent()
    }
}
