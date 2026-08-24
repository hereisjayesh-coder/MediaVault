package com.mediavault.core.domain.source

import com.mediavault.core.model.Source
import com.mediavault.core.model.SourceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCatalogIndexTest {

    private fun source(
        id: String,
        displayName: String,
        domain: String? = "$id.com",
        categories: List<SourceCategory> = listOf(SourceCategory.VIDEO),
        aliases: List<String> = emptyList(),
        isSupported: Boolean = true,
    ) = Source(
        id = id,
        displayName = displayName,
        domain = domain,
        extractorIds = listOf(displayName),
        categories = categories,
        aliases = aliases,
        isSupported = isSupported,
        faviconUrl = domain?.let { "https://example.com/favicon?domain=$it" },
    )

    private val sample = listOf(
        source("youtube", "YouTube", categories = listOf(SourceCategory.VIDEO, SourceCategory.MUSIC)),
        source("vimeo", "Vimeo"),
        source("reddit", "Reddit", domain = "reddit.com", categories = listOf(SourceCategory.SOCIAL_MEDIA)),
        source("tiktok", "TikTok", aliases = listOf("douyin"), categories = listOf(SourceCategory.SOCIAL_MEDIA, SourceCategory.VIDEO)),
        source("_1337x", "1337x", domain = null, categories = listOf(SourceCategory.OTHER)),
    )

    @Test
    fun `blank query returns every source`() {
        val index = SourceCatalogIndex(sample)

        assertEquals(sample.size, index.search("   ").size)
    }

    @Test
    fun `search is case-insensitive and matches display name`() {
        val index = SourceCatalogIndex(sample)

        assertEquals(listOf("YouTube"), index.search("YOUTUBE").map { it.displayName })
        assertEquals(listOf("YouTube"), index.search("you").map { it.displayName })
    }

    @Test
    fun `search matches domain`() {
        val index = SourceCatalogIndex(sample)

        assertEquals(listOf("Reddit"), index.search("reddit.com").map { it.displayName })
    }

    @Test
    fun `search matches aliases`() {
        val index = SourceCatalogIndex(sample)

        assertEquals(listOf("TikTok"), index.search("douyin").map { it.displayName })
    }

    @Test
    fun `search with no matches returns an empty list`() {
        val index = SourceCatalogIndex(sample)

        assertTrue(index.search("no-such-service-xyz").isEmpty())
    }

    @Test
    fun `category filter returns only sources carrying that category`() {
        val index = SourceCatalogIndex(sample)

        val socialMedia = index.byCategory(SourceCategory.SOCIAL_MEDIA)

        assertEquals(setOf("Reddit", "TikTok"), socialMedia.map { it.displayName }.toSet())
    }

    @Test
    fun `a source with multiple categories appears under each`() {
        val index = SourceCatalogIndex(sample)

        assertTrue(index.byCategory(SourceCategory.VIDEO).any { it.id == "tiktok" })
        assertTrue(index.byCategory(SourceCategory.SOCIAL_MEDIA).any { it.id == "tiktok" })
    }

    @Test
    fun `null category returns everything unfiltered`() {
        val index = SourceCatalogIndex(sample)

        assertEquals(sample.size, index.byCategory(null).size)
    }

    @Test
    fun `alphabetical groups are sorted A to Z by display name`() {
        val index = SourceCatalogIndex(sample)

        val groups = index.alphabeticalGroups()

        assertEquals(listOf('#', 'R', 'T', 'V', 'Y'), groups.keys.sorted())
        assertEquals(listOf("Reddit"), groups.getValue('R').map { it.displayName })
    }

    @Test
    fun `a display name starting with a digit lands in the catch-all bucket`() {
        val index = SourceCatalogIndex(sample)

        val groups = index.alphabeticalGroups()

        assertEquals(listOf("1337x"), groups.getValue('#').map { it.displayName })
    }
}
