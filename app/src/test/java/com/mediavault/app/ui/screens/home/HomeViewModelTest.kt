package com.mediavault.app.ui.screens.home

import com.mediavault.app.download.FakeNetworkPolicyManager
import com.mediavault.app.util.NetworkStatus
import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistCollectionType
import com.mediavault.core.domain.extractor.PlaylistItem
import com.mediavault.core.domain.network.NetworkPolicyDecision
import com.mediavault.core.model.MediaFormat
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var fakeEngine: FakeExtractorEngine
    private lateinit var fakeDownloadEngine: FakeDownloadEngine
    private lateinit var fakeNetworkPolicyManager: FakeNetworkPolicyManager
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fakeEngine = FakeExtractorEngine()
        fakeDownloadEngine = FakeDownloadEngine()
        fakeNetworkPolicyManager = FakeNetworkPolicyManager()
        viewModel = HomeViewModel(fakeEngine, FakeDeviceStatusProvider(), fakeDownloadEngine, fakeNetworkPolicyManager)
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
    fun `downloading selected items resolves formats from the first selected item`() = runTest {
        loadPlaylist()
        viewModel.onPlaylistItemTapped(item("a"))
        viewModel.onPlaylistItemTapped(item("b"))
        fakeEngine.nextResult = null // force the second analyze() call to suspend until completePending()

        viewModel.downloadSelectedItems()
        dispatcher.scheduler.runCurrent()

        assertEquals("https://example.com/a", fakeEngine.analyzeCalls.last().first)
        assertTrue(viewModel.uiState.value.playlistDownloadSetup!!.isResolvingFormats)

        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.completePending(AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed)))))
        dispatcher.scheduler.advanceUntilIdle()

        val setup = viewModel.uiState.value.playlistDownloadSetup
        assertEquals(listOf(muxed), setup!!.formatOptions)
        assertTrue(!setup.isResolvingFormats)
    }

    @Test
    fun `downloading the entire playlist only considers available items`() = runTest {
        loadPlaylist()

        viewModel.downloadEntirePlaylist()
        dispatcher.scheduler.runCurrent()

        // 4 items total, "c" is unavailable — the first *available* one ("a") is resolved.
        assertEquals("https://example.com/a", fakeEngine.analyzeCalls.last().first)
        assertEquals(3, viewModel.uiState.value.playlistDownloadSetup!!.items.size)
    }

    @Test
    fun `queuing a playlist download preserves order and carries playlist item ids`() = runTest {
        loadPlaylist()
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))

        viewModel.downloadEntirePlaylist()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPlaylistFormatSelected(muxed)
        viewModel.onQueuePlaylistClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val request = fakeDownloadEngine.enqueuedPlaylists.single()
        assertEquals(listOf(1, 2, 4), request.items.map { it.itemIndex })
        assertEquals(listOf("a", "b", "d"), request.items.map { it.sourceMediaId })
        assertTrue(request.skipAlreadyDownloaded)
        assertNull(viewModel.uiState.value.playlistDownloadSetup)
        assertTrue(viewModel.uiState.value.justQueued)
    }

    @Test
    fun `skip already downloaded toggle is carried into the playlist request`() = runTest {
        loadPlaylist()
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))
        viewModel.onSkipAlreadyDownloadedToggled(false)

        viewModel.downloadEntirePlaylist()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onPlaylistFormatSelected(muxed)
        viewModel.onQueuePlaylistClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(!fakeDownloadEngine.enqueuedPlaylists.single().skipAlreadyDownloaded)
    }

    @Test
    fun `cancelling playlist setup clears the setup state`() = runTest {
        loadPlaylist()
        viewModel.downloadEntirePlaylist()
        dispatcher.scheduler.runCurrent()

        viewModel.cancelPlaylistDownloadSetup()

        assertNull(viewModel.uiState.value.playlistDownloadSetup)
    }

    // --- Format selection & download -------------------------------------------------

    @Test
    fun `a video-only format with no compatible audio cannot be selected`() = runTest {
        val videoOnly = sampleFormat("v1", hasVideo = true, hasAudio = false)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(videoOnly))))
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()

        val option = viewModel.uiState.value.downloadOptions.single()
        viewModel.onDownloadOptionSelected(option)

        assertNull(viewModel.uiState.value.selectedFormatId)
    }

    @Test
    fun `muxed formats can be selected`() = runTest {
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()

        val option = viewModel.uiState.value.downloadOptions.single { it.id == "m1" }
        viewModel.onDownloadOptionSelected(option)

        assertEquals("m1", viewModel.uiState.value.selectedFormatId)
    }

    @Test
    fun `download enqueues the selected format immediately, no destination picker needed`() = runTest {
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true, container = "mp4")
        fakeEngine.nextResult = AppResult.Success(
            ExtractionResult.Single(sampleMedia(formats = listOf(muxed), webpageUrl = "https://example.com/video")),
        )
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()
        val option = viewModel.uiState.value.downloadOptions.single { it.id == "m1" }
        viewModel.onDownloadOptionSelected(option)

        viewModel.onDownloadClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val request = fakeDownloadEngine.enqueued.single()
        assertEquals("m1", request.formatId)
        assertEquals("https://example.com/video", request.sourceUrl)
        assertEquals(MediaType.VIDEO, request.mediaType)
        assertTrue(viewModel.uiState.value.justQueued)
    }

    @Test
    fun `selecting a paired video+audio option enqueues a split-stream download request`() = runTest {
        val video = sampleFormat("v1080", hasVideo = true, hasAudio = false, container = "mp4")
        val audio = sampleFormat("a1", hasVideo = false, hasAudio = true, container = "m4a")
        fakeEngine.nextResult = AppResult.Success(
            ExtractionResult.Single(sampleMedia(formats = listOf(video, audio), webpageUrl = "https://example.com/video")),
        )
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()

        val paired = viewModel.uiState.value.downloadOptions.single { it.requiresProcessing }
        assertEquals("v1080+a1", paired.id)
        viewModel.onDownloadOptionSelected(paired)
        viewModel.onDownloadClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val request = fakeDownloadEngine.enqueued.single()
        assertEquals("v1080", request.formatId)
        assertEquals("a1", request.audioFormatId)
        assertEquals("mp4", request.container)
        assertEquals(MediaType.VIDEO, request.mediaType)
        // A split-stream task is never byte-offset-resumable, regardless of the source format's own supportsResume.
        assertTrue(!request.canResume)
        assertTrue(viewModel.uiState.value.justQueued)
    }

    @Test
    fun `download engine is not touched when no format is selected`() = runTest {
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onDownloadClicked()

        assertTrue(fakeDownloadEngine.enqueued.isEmpty())
    }

    // --- Network policy gating (applied before enqueueing) ----------------------------

    @Test
    fun `a blocked download is never enqueued and shows the block reason`() = runTest {
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))
        fakeNetworkPolicyManager.decision = NetworkPolicyDecision.Block("Today's mobile-data budget is used up.")
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onDownloadOptionSelected(viewModel.uiState.value.downloadOptions.single { it.id == "m1" })

        viewModel.onDownloadClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeDownloadEngine.enqueued.isEmpty())
        assertEquals("Today's mobile-data budget is used up.", viewModel.uiState.value.errorMessage)
        assertTrue(!viewModel.uiState.value.justQueued)
    }

    @Test
    fun `a risky download waits for explicit confirmation, then enqueues once confirmed`() = runTest {
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))
        fakeNetworkPolicyManager.decision = NetworkPolicyDecision.Warn("This may exceed today's remaining mobile-data budget.")
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onDownloadOptionSelected(viewModel.uiState.value.downloadOptions.single { it.id == "m1" })

        viewModel.onDownloadClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeDownloadEngine.enqueued.isEmpty())
        val warning = viewModel.uiState.value.networkWarning as NetworkWarning.Single
        assertEquals("This may exceed today's remaining mobile-data budget.", warning.reason)

        viewModel.onNetworkWarningConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeDownloadEngine.enqueued.size)
        assertTrue(viewModel.uiState.value.justQueued)
        assertNull(viewModel.uiState.value.networkWarning)
    }

    @Test
    fun `dismissing a risky-download warning leaves it unqueued`() = runTest {
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))
        fakeNetworkPolicyManager.decision = NetworkPolicyDecision.Warn("This may exceed today's remaining mobile-data budget.")
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onDownloadOptionSelected(viewModel.uiState.value.downloadOptions.single { it.id == "m1" })
        viewModel.onDownloadClicked()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onNetworkWarningDismissed()

        assertNull(viewModel.uiState.value.networkWarning)
        assertTrue(fakeDownloadEngine.enqueued.isEmpty())
    }

    @Test
    fun `a queue-for-wifi decision still enqueues but tells the user it will wait`() = runTest {
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))
        fakeNetworkPolicyManager.decision = NetworkPolicyDecision.QueueForWifi
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onDownloadOptionSelected(viewModel.uiState.value.downloadOptions.single { it.id == "m1" })

        viewModel.onDownloadClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeDownloadEngine.enqueued.size)
        assertTrue(viewModel.uiState.value.justQueued)
        assertEquals(
            "Waiting for Wi-Fi — this exceeds your per-download mobile-data limit.",
            viewModel.uiState.value.infoMessage,
        )
    }

    @Test
    fun `a blocked playlist queue is never enqueued`() = runTest {
        loadPlaylist()
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))
        fakeNetworkPolicyManager.decision = NetworkPolicyDecision.Block("Today's mobile-data budget is used up.")

        viewModel.downloadEntirePlaylist()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onPlaylistFormatSelected(muxed)
        viewModel.onQueuePlaylistClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeDownloadEngine.enqueuedPlaylists.isEmpty())
        assertEquals("Today's mobile-data budget is used up.", viewModel.uiState.value.errorMessage)
        // The setup step stays open so the user can pick a smaller quality instead.
        assertEquals(muxed.formatId, viewModel.uiState.value.playlistDownloadSetup?.selectedFormatId)
    }

    @Test
    fun `a risky playlist queue waits for confirmation, then enqueues once confirmed`() = runTest {
        loadPlaylist()
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))
        fakeNetworkPolicyManager.decision = NetworkPolicyDecision.Warn("This may exceed today's remaining mobile-data budget.")

        viewModel.downloadEntirePlaylist()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onPlaylistFormatSelected(muxed)
        viewModel.onQueuePlaylistClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeDownloadEngine.enqueuedPlaylists.isEmpty())
        assertTrue(viewModel.uiState.value.networkWarning is NetworkWarning.Playlist)

        viewModel.onNetworkWarningConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeDownloadEngine.enqueuedPlaylists.size)
        assertTrue(viewModel.uiState.value.justQueued)
    }

    private fun sampleFormat(
        id: String,
        hasVideo: Boolean,
        hasAudio: Boolean,
        container: String = "mp4",
    ) = MediaFormat(
        formatId = id,
        resolutionLabel = if (hasVideo) "1080p" else null,
        container = container,
        videoCodec = if (hasVideo) "avc1" else null,
        audioCodec = if (hasAudio) "aac" else null,
        fps = if (hasVideo) 30 else null,
        estimatedSizeBytes = 100_000_000L,
        hasVideo = hasVideo,
        hasAudio = hasAudio,
        supportsResume = true,
    )

    private suspend fun loadPlaylist() {
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Playlist(samplePlaylist()))
        viewModel.onUrlChanged("https://example.com/playlist")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun item(id: String, isAvailable: Boolean = true) =
        samplePlaylist().items.first { it.id == id }.copy(isAvailable = isAvailable)

    private fun sampleMedia(formats: List<MediaFormat> = emptyList(), webpageUrl: String? = null) = MediaAnalysisResult(
        id = "abc123",
        sourceName = "Youtube",
        title = "Test",
        durationSeconds = 120,
        thumbnailUrl = null,
        webpageUrl = webpageUrl,
        formats = formats,
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
