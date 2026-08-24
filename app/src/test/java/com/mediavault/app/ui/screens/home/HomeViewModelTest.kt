package com.mediavault.app.ui.screens.home

import com.mediavault.app.util.NetworkStatus
import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistCollectionType
import com.mediavault.core.domain.extractor.PlaylistItem
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var fakeEngine: FakeExtractorEngine
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fakeEngine = FakeExtractorEngine()
        viewModel = HomeViewModel(fakeEngine, FakeDeviceStatusProvider())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `device status is loaded on start`() = runTest {
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(10_000_000_000L, state.freeStorageBytes)
        assertEquals(NetworkStatus.WIFI, state.networkStatus)
    }

    @Test
    fun `blank url shows an error and never calls the engine`() = runTest {
        viewModel.analyze()

        assertEquals("Paste a link first.", viewModel.uiState.value.errorMessage)
        assertTrue(fakeEngine.analyzeCalls.isEmpty())
    }

    @Test
    fun `successful analyze populates the result and clears loading`() = runTest {
        val analysisResult = ExtractionResult.Single(sampleMedia())
        fakeEngine.nextResult = AppResult.Success(analysisResult)
        viewModel.onUrlChanged("https://example.com/video")

        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(analysisResult, state.result)
        assertTrue(!state.isAnalyzing)
        assertNull(state.errorMessage)
    }

    @Test
    fun `failed analyze surfaces the error message and clears loading`() = runTest {
        fakeEngine.nextResult = AppResult.Failure(AppError.Unsupported("nope"))
        viewModel.onUrlChanged("https://example.com/video")

        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.result)
        assertEquals("nope", state.errorMessage)
        assertTrue(!state.isAnalyzing)
    }

    @Test
    fun `cancelling an in-flight analysis notifies the engine with the same taskId`() = runTest {
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isAnalyzing)
        val (_, taskId) = fakeEngine.analyzeCalls.single()

        viewModel.cancelInFlightAnalysis()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(taskId), fakeEngine.cancelledTaskIds)
        assertTrue(!viewModel.uiState.value.isAnalyzing)
    }

    @Test
    fun `starting a new analysis cancels the previous one`() = runTest {
        viewModel.onUrlChanged("https://example.com/first")
        viewModel.analyze()
        dispatcher.scheduler.runCurrent()
        val firstTaskId = fakeEngine.analyzeCalls.single().second

        viewModel.onUrlChanged("https://example.com/second")
        viewModel.analyze()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(firstTaskId), fakeEngine.cancelledTaskIds)
        assertEquals(2, fakeEngine.analyzeCalls.size)
    }

    // --- Playlist selection ---------------------------------------------------------

    @Test
    fun `a new analysis resets any leftover playlist selection`() = runTest {
        loadPlaylist()
        viewModel.onPlaylistItemTapped(item("a"))
        assertEquals(setOf("a"), viewModel.uiState.value.playlistSelection.selectedItemIds)

        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia()))
        viewModel.onUrlChanged("https://example.com/other")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.playlistSelection.selectedItemIds.isEmpty())
    }

    @Test
    fun `tapping an item toggles its selection`() = runTest {
        loadPlaylist()

        viewModel.onPlaylistItemTapped(item("a"))
        assertEquals(setOf("a"), viewModel.uiState.value.playlistSelection.selectedItemIds)

        viewModel.onPlaylistItemTapped(item("a"))
        assertTrue(viewModel.uiState.value.playlistSelection.selectedItemIds.isEmpty())
    }

    @Test
    fun `unavailable items cannot be selected`() = runTest {
        loadPlaylist()

        viewModel.onPlaylistItemTapped(item("c", isAvailable = false))

        assertTrue(viewModel.uiState.value.playlistSelection.selectedItemIds.isEmpty())
    }

    @Test
    fun `range selection selects every available item between two taps, inclusive`() = runTest {
        loadPlaylist()

        viewModel.beginRangeSelection()
        viewModel.onPlaylistItemTapped(item("a"))
        assertTrue(viewModel.uiState.value.playlistSelection.isRangeSelectionActive)

        viewModel.onPlaylistItemTapped(item("d"))

        val selection = viewModel.uiState.value.playlistSelection
        // "c" sits between a and d but is unavailable, so it must not be selected.
        assertEquals(setOf("a", "b", "d"), selection.selectedItemIds)
        assertTrue(!selection.isRangeSelectionActive)
        assertNull(selection.rangeAnchorId)
    }

    @Test
    fun `cancelling selection clears selected items and exits range mode`() = runTest {
        loadPlaylist()
        viewModel.onPlaylistItemTapped(item("a"))
        viewModel.beginRangeSelection()

        viewModel.cancelSelection()

        val selection = viewModel.uiState.value.playlistSelection
        assertTrue(selection.selectedItemIds.isEmpty())
        assertTrue(!selection.isRangeSelectionActive)
    }

    @Test
    fun `downloading with nothing selected asks the user to select first`() = runTest {
        loadPlaylist()

        viewModel.downloadSelectedItems()

        assertEquals("Select at least one item first.", viewModel.uiState.value.infoMessage)
    }

    @Test
    fun `downloading selected items reports how many, without starting anything`() = runTest {
        loadPlaylist()
        viewModel.onPlaylistItemTapped(item("a"))
        viewModel.onPlaylistItemTapped(item("b"))

        viewModel.downloadSelectedItems()

        assertEquals(
            "Downloading isn't implemented yet — would queue 2 selected item(s).",
            viewModel.uiState.value.infoMessage,
        )
    }

    @Test
    fun `downloading the entire playlist counts only available items`() = runTest {
        loadPlaylist()

        viewModel.downloadEntirePlaylist()

        // 4 items total, "c" is unavailable.
        assertEquals(
            "Downloading isn't implemented yet — would queue all 3 available item(s).",
            viewModel.uiState.value.infoMessage,
        )
    }

    private suspend fun loadPlaylist() {
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Playlist(samplePlaylist()))
        viewModel.onUrlChanged("https://example.com/playlist")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun item(id: String, isAvailable: Boolean = true) =
        samplePlaylist().items.first { it.id == id }.copy(isAvailable = isAvailable)

    private fun sampleMedia() = MediaAnalysisResult(
        id = "abc123",
        sourceName = "Youtube",
        title = "Test",
        durationSeconds = 120,
        thumbnailUrl = null,
        webpageUrl = null,
        formats = emptyList(),
        audioTracks = emptyList(),
        subtitleTracks = emptyList(),
    )

    private fun samplePlaylist() = PlaylistAnalysisResult(
        sourceName = "Youtube",
        title = "Sample playlist",
        thumbnailUrl = null,
        webpageUrl = null,
        collectionType = PlaylistCollectionType.PLAYLIST,
        itemCount = 4,
        items = listOf(
            PlaylistItem("a", 1, "A", null, 60, "https://example.com/a", isAvailable = true),
            PlaylistItem("b", 2, "B", null, 60, "https://example.com/b", isAvailable = true),
            PlaylistItem("c", 3, "C", null, null, null, isAvailable = false),
            PlaylistItem("d", 4, "D", null, 60, "https://example.com/d", isAvailable = true),
        ),
    )
}
