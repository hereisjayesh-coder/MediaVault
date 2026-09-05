package com.mediavault.app.ui.screens.home

import com.mediavault.app.download.FakeNetworkPolicyManager
import com.mediavault.app.player.FakeLibraryRepository
import com.mediavault.app.util.NetworkStatus
import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.domain.download.QualityTier
import com.mediavault.core.domain.download.SelectedAudioTrack
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.MediaCollectionItem
import com.mediavault.core.domain.extractor.MediaCollectionResult
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
    private lateinit var fakeLibraryRepository: FakeLibraryRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fakeEngine = FakeExtractorEngine()
        fakeDownloadEngine = FakeDownloadEngine()
        fakeNetworkPolicyManager = FakeNetworkPolicyManager()
        fakeLibraryRepository = FakeLibraryRepository()
        viewModel = HomeViewModel(fakeEngine, FakeDeviceStatusProvider(), fakeDownloadEngine, fakeNetworkPolicyManager, fakeLibraryRepository)
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

    // --- Recent Activity: sourced from the same Room-backed LibraryRepository flow Library uses ---
    // Regression coverage for the reported defect: Recent Activity always showed "No recent
    // activity" even after successful downloads, because it was never wired to any data source —
    // a hard-coded empty state (see HomeScreen's old RecentActivitySection). These tests exercise
    // the real fix (HomeViewModel collecting LibraryRepository.observeAll()) rather than a
    // hand-maintained stand-in.

    private fun mediaItem(id: String, title: String = "Item $id", addedAtEpochMs: Long = 0L, mediaType: MediaType = MediaType.VIDEO) =
        MediaItemEntity(
            id = id,
            title = title,
            mediaUri = "file:///storage/$id.mp4",
            mediaType = mediaType,
            durationMs = 60_000L,
            sizeBytes = 1_000_000L,
            container = "mp4",
            isImported = false,
            sourceDownloadTaskId = "task-$id",
            lastPlaybackPositionMs = 0L,
            isFavorite = false,
            addedAtEpochMs = addedAtEpochMs,
        )

    @Test
    fun `recent activity is empty when no downloads exist yet`() = runTest {
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.recentActivity.isEmpty())
    }

    @Test
    fun `recent activity reflects existing library items on start`() = runTest {
        fakeLibraryRepository.setItems(listOf(mediaItem("a", addedAtEpochMs = 2000L), mediaItem("b", addedAtEpochMs = 1000L)))

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("a", "b"), viewModel.uiState.value.recentActivity.map { it.id })
    }

    @Test
    fun `recent activity preserves the repository's newest-first ordering rather than re-sorting`() = runTest {
        // LibraryRepository.observeAll() (backed by MediaItemDao's own "ORDER BY addedAtEpochMs
        // DESC" query) is the single source of ordering truth — HomeViewModel must never
        // re-sort what it's handed, only cap it.
        val newestFirst = listOf(
            mediaItem("newest", addedAtEpochMs = 3000L),
            mediaItem("middle", addedAtEpochMs = 2000L),
            mediaItem("oldest", addedAtEpochMs = 1000L),
        )
        fakeLibraryRepository.setItems(newestFirst)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("newest", "middle", "oldest"), viewModel.uiState.value.recentActivity.map { it.id })
    }

    @Test
    fun `recent activity is capped to a short preview, not the full library`() = runTest {
        val sixItems = (1..6).map { mediaItem("item$it", addedAtEpochMs = it.toLong()) }
        fakeLibraryRepository.setItems(sixItems)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.recentActivity.size)
    }

    @Test
    fun `a newly completed download appears in recent activity without recreating the ViewModel`() = runTest {
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.recentActivity.isEmpty())

        // Simulates MediaVaultDownloadEngine.finish() inserting the completed download's row —
        // the same Room table Library observes, reused rather than a second history source.
        fakeLibraryRepository.setItems(listOf(mediaItem("new-download", addedAtEpochMs = 5000L)))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("new-download"), viewModel.uiState.value.recentActivity.map { it.id })
    }

    @Test
    fun `resetToCleanState keeps recent activity instead of flashing empty on every Home re-entry`() = runTest {
        fakeLibraryRepository.setItems(listOf(mediaItem("a", addedAtEpochMs = 1000L)))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("a"), viewModel.uiState.value.recentActivity.map { it.id })

        viewModel.resetToCleanState()

        assertEquals(listOf("a"), viewModel.uiState.value.recentActivity.map { it.id })
    }

    // --- Reset on fresh Home entry (see HomeScreen's remember(Unit)) -----------------------

    @Test
    fun `resetToCleanState clears a completed analysis back to the default state`() = runTest {
        val analysisResult = ExtractionResult.Single(sampleMedia())
        fakeEngine.nextResult = AppResult.Success(analysisResult)
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(analysisResult, viewModel.uiState.value.result)

        viewModel.resetToCleanState()

        val state = viewModel.uiState.value
        assertEquals("", state.url)
        assertNull(state.result)
        assertNull(state.formatSelection)
        assertEquals(SelectedQualityState(), state.selectedQuality)
    }

    @Test
    fun `resetToCleanState cancels an in-flight analysis rather than letting a late result land after reset`() = runTest {
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia()))
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        // Deliberately not advancing the dispatcher — the analysis is still "in flight".

        viewModel.resetToCleanState()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.result)
        assertTrue(!viewModel.uiState.value.isAnalyzing)
    }

    @Test
    fun `resetToCleanState keeps the already-loaded device status instead of clearing it`() = runTest {
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(10_000_000_000L, viewModel.uiState.value.freeStorageBytes)

        viewModel.resetToCleanState()

        assertEquals(10_000_000_000L, viewModel.uiState.value.freeStorageBytes)
        assertEquals(NetworkStatus.WIFI, viewModel.uiState.value.networkStatus)
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
        assertEquals(listOf("m1"), setup!!.formatSelection!!.videoQualityGroups.flatMap { it.variants }.map { it.formatId })
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

        val tier = viewModel.uiState.value.playlistDownloadSetup!!.formatSelection!!.videoQualityGroups.single().tier
        viewModel.onPlaylistQualityTierSelected(tier)
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
        val tier = viewModel.uiState.value.playlistDownloadSetup!!.formatSelection!!.videoQualityGroups.single().tier
        viewModel.onPlaylistQualityTierSelected(tier)
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

    // --- Playlist merge-required (paired video+audio) quality selection ---------------

    @Test
    fun `a merge-required playlist quality is now selectable, with the sole audio track auto-picked`() = runTest {
        loadPlaylist()
        val video = sampleFormat("v1080", hasVideo = true, hasAudio = false)
        val audio = sampleFormat("a-en", hasVideo = false, hasAudio = true, languageCode = "en")
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(video, audio))))

        viewModel.downloadEntirePlaylist()
        dispatcher.scheduler.advanceUntilIdle()

        val tier = viewModel.uiState.value.playlistDownloadSetup!!.formatSelection!!.videoQualityGroups.single().tier
        viewModel.onPlaylistQualityTierSelected(tier)

        val selection = viewModel.uiState.value.playlistDownloadSetup!!.selectedQuality
        assertEquals(tier, selection.tier)
        assertEquals(setOf("a-en"), selection.selectedAudioFormatIds)
    }

    @Test
    fun `queuing a merge-required playlist quality carries requiresProcessing and every selected audio language`() = runTest {
        loadPlaylist()
        val video = sampleFormat("v1080", hasVideo = true, hasAudio = false)
        val english = sampleFormat("a-en", hasVideo = false, hasAudio = true, languageCode = "en")
        val spanish = sampleFormat("a-es", hasVideo = false, hasAudio = true, languageCode = "es")
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(video, english, spanish))))

        viewModel.downloadEntirePlaylist()
        dispatcher.scheduler.advanceUntilIdle()
        val tier = viewModel.uiState.value.playlistDownloadSetup!!.formatSelection!!.videoQualityGroups.single().tier
        viewModel.onPlaylistQualityTierSelected(tier)
        viewModel.onPlaylistIncludeMultipleAudioToggled(true)
        viewModel.onPlaylistAudioTrackToggled("a-es") // adds Spanish alongside whichever track tier-selection auto-picked
        viewModel.onQueuePlaylistClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val request = fakeDownloadEngine.enqueuedPlaylists.single()
        assertEquals(QualityTier.FULL_HD_1080P, request.qualityDescriptor.tier)
        assertEquals(setOf("en", "es"), request.qualityDescriptor.audioLanguageCodes.toSet())
    }

    @Test
    fun `a video-only quality with no audio anywhere resolves as a direct pick, same as the single-item picker`() = runTest {
        loadPlaylist()
        val silentVideo = sampleFormat("v1", hasVideo = true, hasAudio = false)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(silentVideo))))

        viewModel.downloadEntirePlaylist()
        dispatcher.scheduler.advanceUntilIdle()

        val setup = viewModel.uiState.value.playlistDownloadSetup!!
        viewModel.onPlaylistQualityTierSelected(setup.formatSelection!!.videoQualityGroups.single().tier)

        val updatedSetup = viewModel.uiState.value.playlistDownloadSetup!!
        val resolved = updatedSetup.formatSelection!!.resolve(updatedSetup.selectedQuality)
        assertTrue(resolved != null && !resolved.requiresProcessing)
    }

    // --- Format selection & download -------------------------------------------------

    @Test
    fun `muxed formats can be selected`() = runTest {
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()

        val tier = viewModel.uiState.value.formatSelection!!.videoQualityGroups.single().tier
        viewModel.onQualityTierSelected(tier)

        assertEquals(tier, viewModel.uiState.value.selectedQuality.tier)
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
        val tier = viewModel.uiState.value.formatSelection!!.videoQualityGroups.single().tier
        viewModel.onQualityTierSelected(tier)

        viewModel.onDownloadClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val request = fakeDownloadEngine.enqueued.single()
        assertEquals("m1", request.formatId)
        assertEquals("https://example.com/video", request.sourceUrl)
        assertEquals(MediaType.VIDEO, request.mediaType)
        assertTrue(viewModel.uiState.value.justQueued)
    }

    @Test
    fun `selecting a video-only quality with separate audio enqueues a split-stream download request`() = runTest {
        val video = sampleFormat("v1080", hasVideo = true, hasAudio = false, container = "mp4")
        val audio = sampleFormat("a1", hasVideo = false, hasAudio = true, container = "m4a")
        fakeEngine.nextResult = AppResult.Success(
            ExtractionResult.Single(sampleMedia(formats = listOf(video, audio), webpageUrl = "https://example.com/video")),
        )
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()

        val tier = viewModel.uiState.value.formatSelection!!.videoQualityGroups.single().tier
        viewModel.onQualityTierSelected(tier) // auto-selects the sole audio track — the video has no embedded audio
        viewModel.onDownloadClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val request = fakeDownloadEngine.enqueued.single()
        assertEquals("v1080", request.formatId)
        assertEquals(listOf(SelectedAudioTrack("a1", null)), request.audioTracks)
        assertEquals("mp4", request.container)
        assertEquals(MediaType.VIDEO, request.mediaType)
        // A split-stream task is never byte-offset-resumable, regardless of the source format's own supportsResume.
        assertTrue(!request.canResume)
        assertTrue(viewModel.uiState.value.justQueued)
    }

    @Test
    fun `a video-only quality with no audio anywhere is still selectable and enqueues as a direct download`() = runTest {
        // No separate audio-only format exists for this source at all (a genuinely silent
        // clip) — unlike the pre-redesign model, which refused to let this be selected, the
        // current model treats the video variant itself as already the complete file: there is
        // nothing to pair it with, so it downloads exactly as-is, no merge, no audio track.
        val silentVideo = sampleFormat("v1", hasVideo = true, hasAudio = false, container = "mp4")
        fakeEngine.nextResult = AppResult.Success(
            ExtractionResult.Single(sampleMedia(formats = listOf(silentVideo), webpageUrl = "https://example.com/video")),
        )
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.formatSelection!!.audioTracks.isEmpty())
        val tier = viewModel.uiState.value.formatSelection!!.videoQualityGroups.single().tier
        viewModel.onQualityTierSelected(tier)
        viewModel.onDownloadClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val request = fakeDownloadEngine.enqueued.single()
        assertEquals("v1", request.formatId)
        assertTrue(request.audioTracks.isEmpty())
        assertEquals("mp4", request.container)
        assertTrue(viewModel.uiState.value.justQueued)
    }

    @Test
    fun `selecting multiple audio tracks enqueues every one of them for muxing into a single file`() = runTest {
        val video = sampleFormat("v1080", hasVideo = true, hasAudio = false, container = "mp4")
        val english = sampleFormat("a-en", hasVideo = false, hasAudio = true, languageCode = "en")
        val hindi = sampleFormat("a-hi", hasVideo = false, hasAudio = true, languageCode = "hi")
        fakeEngine.nextResult = AppResult.Success(
            ExtractionResult.Single(sampleMedia(formats = listOf(video, english, hindi), webpageUrl = "https://example.com/video")),
        )
        viewModel.onUrlChanged("https://example.com/video")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()

        val tier = viewModel.uiState.value.formatSelection!!.videoQualityGroups.single().tier
        viewModel.onQualityTierSelected(tier)
        viewModel.onIncludeMultipleAudioToggled(true)
        viewModel.onAudioTrackToggled("a-hi") // adds Hindi alongside whichever track tier-selection auto-picked
        viewModel.onDownloadClicked()
        dispatcher.scheduler.advanceUntilIdle()

        val request = fakeDownloadEngine.enqueued.single()
        assertEquals(setOf("a-en", "a-hi"), request.audioTracks.map { it.formatId }.toSet())
        // Two or more audio tracks always mux into mkv — see FormatSelectionModel's own container logic.
        assertEquals("mkv", request.container)
    }

    @Test
    fun `download engine is not touched when no quality is selected`() = runTest {
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
        viewModel.onQualityTierSelected(viewModel.uiState.value.formatSelection!!.videoQualityGroups.single().tier)

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
        viewModel.onQualityTierSelected(viewModel.uiState.value.formatSelection!!.videoQualityGroups.single().tier)

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
        viewModel.onQualityTierSelected(viewModel.uiState.value.formatSelection!!.videoQualityGroups.single().tier)
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
        viewModel.onQualityTierSelected(viewModel.uiState.value.formatSelection!!.videoQualityGroups.single().tier)

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
        val tier = viewModel.uiState.value.playlistDownloadSetup!!.formatSelection!!.videoQualityGroups.single().tier
        viewModel.onPlaylistQualityTierSelected(tier)
        viewModel.onQueuePlaylistClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeDownloadEngine.enqueuedPlaylists.isEmpty())
        assertEquals("Today's mobile-data budget is used up.", viewModel.uiState.value.errorMessage)
        // The setup step stays open so the user can pick a smaller quality instead.
        assertEquals(tier, viewModel.uiState.value.playlistDownloadSetup?.selectedQuality?.tier)
    }

    @Test
    fun `a risky playlist queue waits for confirmation, then enqueues once confirmed`() = runTest {
        loadPlaylist()
        val muxed = sampleFormat("m1", hasVideo = true, hasAudio = true)
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Single(sampleMedia(formats = listOf(muxed))))
        fakeNetworkPolicyManager.decision = NetworkPolicyDecision.Warn("This may exceed today's remaining mobile-data budget.")

        viewModel.downloadEntirePlaylist()
        dispatcher.scheduler.advanceUntilIdle()
        val tier = viewModel.uiState.value.playlistDownloadSetup!!.formatSelection!!.videoQualityGroups.single().tier
        viewModel.onPlaylistQualityTierSelected(tier)
        viewModel.onQueuePlaylistClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeDownloadEngine.enqueuedPlaylists.isEmpty())
        assertTrue(viewModel.uiState.value.networkWarning is NetworkWarning.Playlist)

        viewModel.onNetworkWarningConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeDownloadEngine.enqueuedPlaylists.size)
        assertTrue(viewModel.uiState.value.justQueued)
    }

    // --- Image collection (single image / carousel) download --------------------------

    private fun collectionItem(
        index: Int,
        id: String = "shortcode_$index",
        mediaType: MediaType = MediaType.IMAGE,
        isAvailable: Boolean = true,
    ) = MediaCollectionItem(
        id = id,
        index = index,
        mediaType = mediaType,
        mediaUrl = if (isAvailable) "https://cdn.example.com/img$index.jpg" else null,
        isAvailable = isAvailable,
        thumbnailUrl = "https://cdn.example.com/thumb$index.jpg",
    )

    private fun sampleCollection(items: List<MediaCollectionItem>, webpageUrl: String? = "https://instagram.com/p/shortcode/") =
        MediaCollectionResult(
            id = "shortcode",
            sourceName = "Instagram",
            title = "A caption",
            thumbnailUrl = "https://cdn.example.com/thumb1.jpg",
            webpageUrl = webpageUrl,
            items = items,
        )

    private suspend fun loadCollection(items: List<MediaCollectionItem>) {
        fakeEngine.nextResult = AppResult.Success(ExtractionResult.Collection(sampleCollection(items)))
        viewModel.onUrlChanged("https://instagram.com/p/shortcode/")
        viewModel.analyze()
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `downloading a single-image collection enqueues exactly one image task, ungrouped`() = runTest {
        loadCollection(listOf(collectionItem(1)))

        viewModel.downloadEntireCollection()
        dispatcher.scheduler.advanceUntilIdle()

        val request = fakeDownloadEngine.enqueued.single()
        assertEquals(MediaType.IMAGE, request.mediaType)
        assertEquals("1", request.formatId)
        assertEquals("https://instagram.com/p/shortcode/", request.sourceUrl)
        assertNull(request.playlistContext) // a "batch of one" gets no group header
        assertTrue(viewModel.uiState.value.justQueued)
    }

    @Test
    fun `downloading an entire carousel enqueues one task per item, in order, grouped`() = runTest {
        loadCollection(listOf(collectionItem(1), collectionItem(2), collectionItem(3)))

        viewModel.downloadEntireCollection()
        dispatcher.scheduler.advanceUntilIdle()

        val requests = fakeDownloadEngine.enqueued
        assertEquals(3, requests.size)
        assertEquals(listOf("1", "2", "3"), requests.map { it.formatId })
        assertEquals(listOf(1, 2, 3), requests.map { it.playlistContext?.itemIndex })
        // Every item shares the same group id, and only one group id was generated.
        val groupIds = requests.mapNotNull { it.playlistContext?.playlistId }.toSet()
        assertEquals(1, groupIds.size)
    }

    /**
     * Regression test for the real reported defect: every item of a mixed carousel — not just
     * the image ones — must enqueue, each routed by its own real media type, in original order.
     */
    @Test
    fun `downloading a mixed carousel enqueues every item, each routed by its own real media type`() = runTest {
        loadCollection(
            listOf(
                collectionItem(1, mediaType = MediaType.IMAGE),
                collectionItem(2, mediaType = MediaType.VIDEO),
                collectionItem(3, mediaType = MediaType.VIDEO),
                collectionItem(4, mediaType = MediaType.IMAGE),
            ),
        )

        viewModel.downloadEntireCollection()
        dispatcher.scheduler.advanceUntilIdle()

        val requests = fakeDownloadEngine.enqueued
        assertEquals(4, requests.size)
        assertEquals(listOf("1", "2", "3", "4"), requests.map { it.formatId })
        assertEquals(
            listOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.VIDEO, MediaType.IMAGE),
            requests.map { it.mediaType },
        )
    }

    @Test
    fun `an unavailable carousel item is excluded from Download all and cannot be selected`() = runTest {
        loadCollection(listOf(collectionItem(1), collectionItem(2, isAvailable = false), collectionItem(3)))

        viewModel.onCollectionItemTapped(collectionItem(2, isAvailable = false))
        assertTrue(viewModel.uiState.value.playlistSelection.selectedItemIds.isEmpty())

        viewModel.downloadEntireCollection()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("1", "3"), fakeDownloadEngine.enqueued.map { it.formatId })
    }

    @Test
    fun `downloading selected carousel items only enqueues those items`() = runTest {
        loadCollection(listOf(collectionItem(1), collectionItem(2), collectionItem(3)))
        viewModel.onCollectionItemTapped(collectionItem(1))
        viewModel.onCollectionItemTapped(collectionItem(3))

        viewModel.downloadSelectedCollectionItems()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("1", "3"), fakeDownloadEngine.enqueued.map { it.formatId })
    }

    @Test
    fun `a selected subset of a carousel is still numbered against the full collection size, not the batch size`() = runTest {
        // Confirmed live on a Pixel 7a: downloading only items 2 and 4 out of a real 5-image
        // carousel previously labeled them "(2/2)" and "(4/2)" — numbered against the
        // 2-item *download batch*, not the collection's real 5-item shape, producing a
        // nonsensical "(4/2)" (position 4 of only 2).
        loadCollection(listOf(collectionItem(1), collectionItem(2), collectionItem(3), collectionItem(4), collectionItem(5)))
        viewModel.onCollectionItemTapped(collectionItem(2))
        viewModel.onCollectionItemTapped(collectionItem(4))

        viewModel.downloadSelectedCollectionItems()
        dispatcher.scheduler.advanceUntilIdle()

        val titles = fakeDownloadEngine.enqueued.map { it.title }
        assertTrue(titles.any { it.endsWith("(2/5)") })
        assertTrue(titles.any { it.endsWith("(4/5)") })
    }

    @Test
    fun `downloading a carousel with nothing selected asks the user to select first`() = runTest {
        loadCollection(listOf(collectionItem(1), collectionItem(2)))

        viewModel.downloadSelectedCollectionItems()

        assertEquals("Select at least one item first.", viewModel.uiState.value.infoMessage)
        assertTrue(fakeDownloadEngine.enqueued.isEmpty())
    }

    @Test
    fun `an already-downloaded carousel item is skipped when the skip toggle is on`() = runTest {
        loadCollection(listOf(collectionItem(1), collectionItem(2)))
        fakeDownloadEngine.alreadyDownloadedSourceMediaIds = setOf(collectionItem(1).id)

        viewModel.downloadEntireCollection()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("2"), fakeDownloadEngine.enqueued.map { it.formatId })
    }

    @Test
    fun `an already-downloaded carousel item still queues when the skip toggle is off`() = runTest {
        loadCollection(listOf(collectionItem(1), collectionItem(2)))
        fakeDownloadEngine.alreadyDownloadedSourceMediaIds = setOf(collectionItem(1).id)
        viewModel.onSkipAlreadyDownloadedToggled(false)

        viewModel.downloadEntireCollection()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("1", "2"), fakeDownloadEngine.enqueued.map { it.formatId })
    }

    @Test
    fun `a blocked collection download is never enqueued and shows the block reason`() = runTest {
        loadCollection(listOf(collectionItem(1)))
        fakeNetworkPolicyManager.decision = NetworkPolicyDecision.Block("Today's mobile-data budget is used up.")

        viewModel.downloadEntireCollection()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeDownloadEngine.enqueued.isEmpty())
        assertEquals("Today's mobile-data budget is used up.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `a risky collection download waits for confirmation, then enqueues the same items once confirmed`() = runTest {
        loadCollection(listOf(collectionItem(1), collectionItem(2)))
        fakeNetworkPolicyManager.decision = NetworkPolicyDecision.Warn("This may exceed today's remaining mobile-data budget.")

        viewModel.downloadEntireCollection()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeDownloadEngine.enqueued.isEmpty())
        assertTrue(viewModel.uiState.value.networkWarning is NetworkWarning.Collection)

        viewModel.onNetworkWarningConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("1", "2"), fakeDownloadEngine.enqueued.map { it.formatId })
        assertTrue(viewModel.uiState.value.justQueued)
    }

    private fun sampleFormat(
        id: String,
        hasVideo: Boolean,
        hasAudio: Boolean,
        container: String = "mp4",
        languageCode: String? = null,
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
        heightPx = if (hasVideo) 1080 else null,
        languageCode = languageCode,
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
