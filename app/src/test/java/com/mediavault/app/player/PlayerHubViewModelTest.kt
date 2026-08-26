package com.mediavault.app.player

import com.mediavault.app.ui.screens.player.PlayerHubViewModel
import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Covers the Player tab's watch-history partitioning and file-missing filtering — see [com.mediavault.app.ui.screens.player.toWatchHistorySections] for the pure partition logic itself. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerHubViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var libraryRepository: FakeLibraryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        libraryRepository = FakeLibraryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item(
        id: String,
        positionMs: Long,
        durationMs: Long? = 100_000L,
        watchedAtEpochMs: Long? = null,
    ) = MediaItemEntity(
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
        lastWatchedAtEpochMs = watchedAtEpochMs,
    )

    @Test
    fun `nothing ever watched shows an empty, non-loading state`() = runTest {
        val viewModel = PlayerHubViewModel(libraryRepository)
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue(!state.isLoading)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `an in-progress item lands in continue watching, most recent first`() = runTest {
        libraryRepository.setItems(
            listOf(
                item("a", positionMs = 10_000L, watchedAtEpochMs = 1_000L),
                item("b", positionMs = 20_000L, watchedAtEpochMs = 2_000L),
            ),
        )

        val viewModel = PlayerHubViewModel(libraryRepository)
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf("b", "a"), state.continueWatching.map { it.id })
        assertTrue(state.recentlyWatched.isEmpty())
    }

    @Test
    fun `a finished item lands in recently watched, not continue watching`() = runTest {
        libraryRepository.setItems(
            listOf(item("a", positionMs = 99_000L, durationMs = 100_000L, watchedAtEpochMs = 1_000L)),
        )

        val viewModel = PlayerHubViewModel(libraryRepository)
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.continueWatching.isEmpty())
        assertEquals(listOf("a"), state.recentlyWatched.map { it.id })
    }

    @Test
    fun `an item whose underlying file is missing is excluded from both sections`() = runTest {
        libraryRepository.setItems(listOf(item("a", positionMs = 10_000L, watchedAtEpochMs = 1_000L)))
        libraryRepository.existingIds = emptySet()

        val viewModel = PlayerHubViewModel(libraryRepository)
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `an item never played is excluded from watch history entirely`() = runTest {
        libraryRepository.setItems(listOf(item("a", positionMs = 0L, watchedAtEpochMs = null)))

        val viewModel = PlayerHubViewModel(libraryRepository)
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `refresh picks up a newly watched item added after the initial load`() = runTest {
        val viewModel = PlayerHubViewModel(libraryRepository)
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isEmpty)

        libraryRepository.setItems(listOf(item("a", positionMs = 5_000L, watchedAtEpochMs = 1_000L)))
        viewModel.refresh()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf("a"), viewModel.uiState.value.continueWatching.map { it.id })
    }
}
