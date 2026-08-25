package com.mediavault.app.player

import androidx.lifecycle.SavedStateHandle
import com.mediavault.app.ui.screens.player.PlayerViewModel
import com.mediavault.app.ui.screens.player.SleepTimerOption
import com.mediavault.app.ui.screens.player.VideoResizeMode
import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.model.MediaTrackInfo
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
    private lateinit var audioPreferenceProvider: FakeAudioPreferenceProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        libraryRepository = FakeLibraryRepository()
        engine = FakePlayerEngine()
        engineFactory = FakePlayerEngineFactory(engine)
        lastPlayedProvider = FakeLastPlayedProvider()
        audioPreferenceProvider = FakeAudioPreferenceProvider()
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
        audioPreferenceProvider = audioPreferenceProvider,
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

    // --- -10s/+10s seek -----------------------------------------------------------------

    @Test
    fun `seekBy clamps within the item's duration bounds`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()
        engine.state.value = engine.state.value.copy(positionMs = 5_000L, durationMs = 10_000L)
        dispatcher.scheduler.runCurrent()

        viewModel.seekBy(10_000L) // would overshoot past the end
        dispatcher.scheduler.runCurrent()
        assertEquals(10_000L, engine.seekCalls.last())

        viewModel.seekBy(-100_000L) // would undershoot past zero
        dispatcher.scheduler.runCurrent()
        assertEquals(0L, engine.seekCalls.last())
        viewModel.cancelBackgroundWorkForTesting()
    }

    // --- Loop / resize mode ---------------------------------------------------------------

    @Test
    fun `toggling loop flips the engine's looping state`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        viewModel.onLoopToggled()
        dispatcher.scheduler.runCurrent()
        assertTrue(engine.state.value.isLooping)

        viewModel.onLoopToggled()
        dispatcher.scheduler.runCurrent()
        assertTrue(!engine.state.value.isLooping)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `selecting a resize mode updates ui state only, without touching the engine`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        viewModel.onResizeModeSelected(VideoResizeMode.ZOOM)
        dispatcher.scheduler.runCurrent()

        assertEquals(VideoResizeMode.ZOOM, viewModel.uiState.value.resizeMode)
        viewModel.cancelBackgroundWorkForTesting()
    }

    // --- Preferred audio language -----------------------------------------------------------

    @Test
    fun `a stored preferred audio language is applied automatically when a matching track exists`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        audioPreferenceProvider.languageCode = "es"
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        engine.state.value = engine.state.value.copy(
            availableAudioTracks = listOf(
                MediaTrackInfo(id = "0:0", languageCode = "en", label = null, isDefault = true),
                MediaTrackInfo(id = "0:1", languageCode = "es", label = null, isDefault = false),
            ),
            selectedAudioTrackId = "0:0",
        )
        dispatcher.scheduler.runCurrent()

        assertEquals("0:1", engine.state.value.selectedAudioTrackId)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `selecting an audio track with a language code persists it as the preferred language`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()
        engine.state.value = engine.state.value.copy(
            availableAudioTracks = listOf(MediaTrackInfo(id = "0:0", languageCode = "ja", label = null, isDefault = true)),
        )
        dispatcher.scheduler.runCurrent()

        viewModel.onAudioTrackSelected("0:0")
        dispatcher.scheduler.runCurrent()

        assertEquals("ja", audioPreferenceProvider.languageCode)
        viewModel.cancelBackgroundWorkForTesting()
    }

    // --- Playlist previous/next -------------------------------------------------------------

    @Test
    fun `standalone media never exposes previous or next`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        assertTrue(!viewModel.uiState.value.hasPrevious)
        assertTrue(!viewModel.uiState.value.hasNext)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `a playlist item exposes previous next and preserves order when navigating`() = runTest {
        val a = item("a")
        val b = item("b")
        val c = item("c")
        libraryRepository.setItems(listOf(a, b, c))
        libraryRepository.playlistSiblings = listOf(a, b, c)

        val viewModel = viewModel("b")
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.hasPrevious)
        assertTrue(viewModel.uiState.value.hasNext)

        viewModel.onNext()
        dispatcher.scheduler.runCurrent()
        assertEquals("c", viewModel.uiState.value.item?.id)

        viewModel.onPrevious()
        dispatcher.scheduler.runCurrent()
        assertEquals("b", viewModel.uiState.value.item?.id)

        assertEquals(
            listOf("file:///private/media/b.mp4", "file:///private/media/c.mp4", "file:///private/media/b.mp4"),
            engine.prepareCalls,
        )
        viewModel.cancelBackgroundWorkForTesting()
    }

    // --- Completed playback -----------------------------------------------------------------

    @Test
    fun `reaching the end of a playlist item auto-advances to the next one`() = runTest {
        val a = item("a")
        val b = item("b")
        libraryRepository.setItems(listOf(a, b))
        libraryRepository.playlistSiblings = listOf(a, b)

        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        engine.state.value = engine.state.value.copy(isEnded = true, isPlaying = false)
        dispatcher.scheduler.runCurrent()

        assertEquals("b", viewModel.uiState.value.item?.id)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `reaching the end of standalone media does not auto-advance`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        engine.state.value = engine.state.value.copy(isEnded = true, isPlaying = false)
        dispatcher.scheduler.runCurrent()

        assertEquals("a", viewModel.uiState.value.item?.id)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `tapping play after standalone media ends replays from the start`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()
        engine.state.value = engine.state.value.copy(isEnded = true, isPlaying = false, positionMs = 60_000L)
        dispatcher.scheduler.runCurrent()

        viewModel.onPlayPauseToggled()
        dispatcher.scheduler.runCurrent()

        assertEquals(0L, engine.seekCalls.last())
        assertTrue(engine.playCalled)
        viewModel.cancelBackgroundWorkForTesting()
    }

    // --- Sleep timer -------------------------------------------------------------------------

    @Test
    fun `selecting the end-of-media sleep timer option pauses without auto-advancing at the end`() = runTest {
        val a = item("a")
        val b = item("b")
        libraryRepository.setItems(listOf(a, b))
        libraryRepository.playlistSiblings = listOf(a, b)

        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        viewModel.onSleepTimerSelected(SleepTimerOption.END_OF_MEDIA)
        dispatcher.scheduler.runCurrent()

        engine.state.value = engine.state.value.copy(isEnded = true, isPlaying = false)
        dispatcher.scheduler.runCurrent()

        assertEquals("a", viewModel.uiState.value.item?.id)
        assertEquals(SleepTimerOption.OFF, viewModel.uiState.value.sleepTimer)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `a fixed-duration sleep timer pauses playback once it elapses`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()

        viewModel.onSleepTimerSelected(SleepTimerOption.MIN_15)
        dispatcher.scheduler.advanceTimeBy(15 * 60_000L + 1_000L)
        dispatcher.scheduler.runCurrent()

        assertTrue(engine.pauseCalled)
        assertEquals(SleepTimerOption.OFF, viewModel.uiState.value.sleepTimer)
        viewModel.cancelBackgroundWorkForTesting()
    }

    // --- Long-press-to-2x gesture ------------------------------------------------------------

    @Test
    fun `engaging the long-press gesture jumps to 2x speed`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()
        engine.state.value = engine.state.value.copy(playbackSpeed = 1.5f)
        dispatcher.scheduler.runCurrent()

        viewModel.onLongPressSpeedEngaged()
        dispatcher.scheduler.runCurrent()

        assertEquals(2f, engine.state.value.playbackSpeed)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `releasing the long-press gesture restores the exact speed from before it, not just 1x`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()
        engine.state.value = engine.state.value.copy(playbackSpeed = 1.5f)
        dispatcher.scheduler.runCurrent()

        viewModel.onLongPressSpeedEngaged()
        dispatcher.scheduler.runCurrent()
        viewModel.onLongPressSpeedReleased()
        dispatcher.scheduler.runCurrent()

        assertEquals(1.5f, engine.state.value.playbackSpeed)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `engaging the long-press gesture twice in a row does not overwrite the remembered speed with 2x`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()
        engine.state.value = engine.state.value.copy(playbackSpeed = 0.75f)
        dispatcher.scheduler.runCurrent()

        viewModel.onLongPressSpeedEngaged()
        dispatcher.scheduler.runCurrent()
        viewModel.onLongPressSpeedEngaged() // e.g. a duplicate call — must not clobber the remembered pre-boost speed with the current (2x) one
        dispatcher.scheduler.runCurrent()
        viewModel.onLongPressSpeedReleased()
        dispatcher.scheduler.runCurrent()

        assertEquals(0.75f, engine.state.value.playbackSpeed)
        viewModel.cancelBackgroundWorkForTesting()
    }

    @Test
    fun `releasing without a prior engage is a no-op`() = runTest {
        libraryRepository.setItems(listOf(item("a")))
        val viewModel = viewModel("a")
        dispatcher.scheduler.runCurrent()
        engine.state.value = engine.state.value.copy(playbackSpeed = 1f)
        dispatcher.scheduler.runCurrent()

        viewModel.onLongPressSpeedReleased()
        dispatcher.scheduler.runCurrent()

        assertEquals(1f, engine.state.value.playbackSpeed)
        viewModel.cancelBackgroundWorkForTesting()
    }
}
