package com.mediavault.app.ui.screens.player

import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure, DAO/Compose-free coverage of the Player tab's Continue Watching vs. Recently Watched split. */
class WatchHistoryTest {

    private fun item(id: String, positionMs: Long, durationMs: Long? = 100_000L) = MediaItemEntity(
        id = id,
        title = "Video $id",
        mediaUri = "file:///private/media/$id.mp4",
        mediaType = MediaType.VIDEO,
        durationMs = durationMs,
        sizeBytes = 1_000L,
        container = "mp4",
        isImported = false,
        sourceDownloadTaskId = null,
        lastPlaybackPositionMs = positionMs,
        isFavorite = false,
        addedAtEpochMs = 0L,
        lastWatchedAtEpochMs = 1_000L,
    )

    @Test
    fun `partway through duration is continue watching`() {
        val sections = listOf(item("a", positionMs = 50_000L, durationMs = 100_000L)).toWatchHistorySections()

        assertEquals(listOf("a"), sections.continueWatching.map { it.id })
        assertEquals(emptyList<String>(), sections.recentlyWatched.map { it.id })
    }

    @Test
    fun `past the 95 percent finished threshold is recently watched, not continue watching`() {
        val sections = listOf(item("a", positionMs = 96_000L, durationMs = 100_000L)).toWatchHistorySections()

        assertEquals(emptyList<String>(), sections.continueWatching.map { it.id })
        assertEquals(listOf("a"), sections.recentlyWatched.map { it.id })
    }

    @Test
    fun `exactly at the finished threshold counts as finished, not continue watching`() {
        val sections = listOf(item("a", positionMs = 95_000L, durationMs = 100_000L)).toWatchHistorySections()

        assertEquals(listOf("a"), sections.recentlyWatched.map { it.id })
    }

    @Test
    fun `zero position is never continue watching even though it was technically opened`() {
        val sections = listOf(item("a", positionMs = 0L, durationMs = 100_000L)).toWatchHistorySections()

        assertEquals(emptyList<String>(), sections.continueWatching.map { it.id })
        assertEquals(listOf("a"), sections.recentlyWatched.map { it.id })
    }

    @Test
    fun `unknown duration with real progress counts as continue watching, never guessed finished`() {
        val sections = listOf(item("a", positionMs = 50_000L, durationMs = null)).toWatchHistorySections()

        assertEquals(listOf("a"), sections.continueWatching.map { it.id })
    }

    @Test
    fun `order within each section is preserved from the input`() {
        val items = listOf(
            item("b", positionMs = 10_000L, durationMs = 100_000L),
            item("a", positionMs = 20_000L, durationMs = 100_000L),
        )

        val sections = items.toWatchHistorySections()

        assertEquals(listOf("b", "a"), sections.continueWatching.map { it.id })
    }
}
