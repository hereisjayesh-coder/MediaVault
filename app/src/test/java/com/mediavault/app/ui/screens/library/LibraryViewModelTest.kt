package com.mediavault.app.ui.screens.library

import com.mediavault.app.library.FakeMediaImportRepository
import com.mediavault.app.player.FakeLibraryRepository
import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeLibraryRepository
    private lateinit var fakeImportRepository: FakeMediaImportRepository
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fakeRepository = FakeLibraryRepository()
        fakeImportRepository = FakeMediaImportRepository()
        viewModel = LibraryViewModel(fakeRepository, fakeImportRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleItem(id: String = "1") = MediaItemEntity(
        id = id,
        title = "A video",
        mediaUri = "file:///private/media/$id.mp4",
        mediaType = MediaType.VIDEO,
        durationMs = null,
        sizeBytes = null,
        container = "mp4",
        isImported = false,
        sourceDownloadTaskId = null,
        lastPlaybackPositionMs = 0,
        isFavorite = false,
        addedAtEpochMs = 0L,
    )

    // --- Save to device chooser ---------------------------------------------------------

    @Test
    fun `requesting save-to-device opens the chooser for that item`() {
        val item = sampleItem()

        viewModel.onSaveToDeviceRequested(item)

        assertEquals(item, viewModel.uiState.value.saveToDeviceTarget)
    }

    @Test
    fun `dismissing save-to-device closes the chooser`() {
        viewModel.onSaveToDeviceRequested(sampleItem())

        viewModel.onSaveToDeviceDismissed()

        assertNull(viewModel.uiState.value.saveToDeviceTarget)
    }

    // --- Save to Gallery -----------------------------------------------------------------

    @Test
    fun `a successful Gallery save reports success and forwards the item's id`() = runTest {
        val item = sampleItem("abc")
        fakeRepository.saveToGalleryResult = AppResult.Success(Unit)

        viewModel.saveToGallery(item)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("abc"), fakeRepository.saveToGalleryCalls)
        assertEquals("Saved to Gallery.", viewModel.uiState.value.infoMessage)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `a failed Gallery save surfaces the repository's error message, not a generic one`() = runTest {
        val item = sampleItem()
        fakeRepository.saveToGalleryResult = AppResult.Failure(AppError.Unsupported("Saving to Gallery needs Android 10 or later on this device — use Save to Files instead."))

        viewModel.saveToGallery(item)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "Saving to Gallery needs Android 10 or later on this device — use Save to Files instead.",
            viewModel.uiState.value.errorMessage,
        )
        assertNull(viewModel.uiState.value.infoMessage)
    }
}
