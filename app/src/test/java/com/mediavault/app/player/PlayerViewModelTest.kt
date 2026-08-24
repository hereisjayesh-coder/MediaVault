package com.mediavault.app.player

import androidx.lifecycle.SavedStateHandle
import com.mediavault.app.ui.screens.player.PlayerViewModel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PlayerViewModel] runs an intentionally-infinite periodic position-save loop for the lifetime
 * of a real playback session, cancelled only by [PlayerViewModel.onCleared] (a real Android
 * ViewModelStore teardown). `runTest` checks for leftover active coroutines as part of the same
 * call that runs the test body, so every test here must call
 * [PlayerViewModel.cancelBackgroundWorkForTesting] itself as its last step, *inside* `runTest {}`
 * — doing it from `@After` is too late, since `runTest` has already finished (and thrown) by
 * the time `@After` runs. Also never call `advanceUntilIdle()`: with that loop always scheduling
 * more virtual-clock work, it would advance forever — `runCurrent()` runs only what's ready now.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var libraryRepository: FakeLibraryRepository
    private lateinit var engine: FakePlayerEngine
    private lateinit var engineFactory: FakePlayerEngineFactory
    private lateinit var lastPlayedProvider: FakeLastPlayedProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        libraryRepository = FakeLibraryRepository()
        engine = FakePlayerEngine()
        engineFactory = FakePlayerEngineFactory(engine)
        lastPlayedProvider = FakeLastPlayedProvider()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item(id: String, lastPositionMs: Long = 0L) = MediaItemEntity(
        id = id,
        title = "Video $id",
        mediaUri = "file:///private/media/$id.mp4",
        mediaType = MediaType.VIDEO,
        durationMs = 60_000,
        sizeBytes = 1_000L,
        container = "mp4",
        isImported = false,
        sourceDownloadTaskId = null,
        lastPlaybackPositionMs = lastPositionMs,
        isFavorite = false,
        addedAtEpochMs = 0L,
    )

    private fun viewModel(requestedId: String?) = PlayerViewModel(
        savedStateHandle = SavedStateHandle(if (requestedId != null) mapOf(PlayerViewModel.MEDIA_ITEM_ID_ARG to requestedId) else emptyMap()),
        libraryRepository = libraryRepository,
        playerEngineFactory = engineFactory,
        lastPlayedProvider = lastPlayedProvider,
    )

    @Test
    fun `loading an item resumes from its last saved position`() = runTest {
        libraryRepository.setItems(listOf(item("a", lastPositionMs = 42_000L)))

        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf("file:///private/media/a.mp4"), engine.prepareCalls)
        assertEquals(listOf(42_000L), engine.seekCalls)
        assertTrue(engine.playCalled)
        assertEquals("a", viewModel.uiState.value.item?.id)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `a fresh item with no saved position is never seeked`() = runTest {
        libraryRepository.setItems(listOf(item("a", lastPositionMs = 0L)))

        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        assertTrue(engine.seekCalls.isEmpty())
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `pausing persists the current position immediately`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()
        engine.state.value = engine.state.value.copy(isPlaying = true, positionMs = 15_000L)
        dispatcher.scheduler.runCurrent()

        viewModel.onPlayPauseToggled()
        dispatcher.scheduler.runCurrent()

        assertTrue(engine.pauseCalled)
        assertEquals(listOf("a" to 15_000L), libraryRepository.updatedPositions)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `seeking persists the new position immediately`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        viewModel.onSeek(30_000L)
        dispatcher.scheduler.runCurrent()

        assertEquals(30_000L, engine.seekCalls.last())
        assertEquals(listOf("a" to 30_000L), libraryRepository.updatedPositions)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `a missing file surfaces an error and never creates a player engine`() = runTest {
        // Item exists in the DB but its backing file doesn't (fileExists returns false).
        libraryRepository.setItems(listOf(item("a")))
        libraryRepository.existingIds = emptySet()

        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        assertEquals(0, engineFactory.createCalls)
        assertTrue(viewModel.uiState.value.errorMessage != null)
        assertNull(viewModel.uiState.value.item)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `no requested id and nothing ever played shows an empty, non-error state`() = runTest {
        val viewModel = viewModel(requestedId = null)
        dispatcher.scheduler.runCurrent()

        assertEquals(0, engineFactory.createCalls)
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.item)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `no requested id falls back to the last-played item`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        lastPlayedProvider.id = "a"

        val viewModel = viewModel(requestedId = null)
        dispatcher.scheduler.runCurrent()

        assertEquals("a", viewModel.uiState.value.item?.id)
        assertEquals(listOf("file:///private/media/a.mp4"), engine.prepareCalls)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `opening an item remembers it as the last played one`() = runTest {
        libraryRepository.setItems(listOf(item("a")))

        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        assertEquals("a", lastPlayedProvider.id)
        viewModel.cancelBackgroundWorkForTesting()
    }
}
