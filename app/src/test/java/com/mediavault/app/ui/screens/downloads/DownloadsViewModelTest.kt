package com.mediavault.app.ui.screens.downloads

import com.mediavault.app.player.FakeLibraryRepository
import com.mediavault.app.ui.screens.home.FakeDownloadEngine
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

/** Covers "Open" on a completed download — must resolve to the Library item the task produced and hand off to the same Player route Library itself uses (see MediaVaultNavHost). */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var downloadEngine: FakeDownloadEngine
    private lateinit var libraryRepository: FakeLibraryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        downloadEngine = FakeDownloadEngine()
        libraryRepository = FakeLibraryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = DownloadsViewModel(downloadEngine, libraryRepository)

    private fun libraryItem(id: String, sourceDownloadTaskId: String?) = MediaItemEntity(
        id = id,
        title = "Video $id",
        mediaUri = "file:///private/media/$id.mp4",
        mediaType = MediaType.VIDEO,
        durationMs = 60_000,
        sizeBytes = 1_000L,
        container = "mp4",
        isImported = false,
        sourceDownloadTaskId = sourceDownloadTaskId,
        lastPlaybackPositionMs = 0,
        isFavorite = false,
        addedAtEpochMs = 0L,
    )

    @Test
    fun `opening a completed video task resolves it as a Player target`() = runTest {
        libraryRepository.setItems(listOf(libraryItem(id = "media-1", sourceDownloadTaskId = "task-1")))
        val viewModel = viewModel()

        viewModel.openInPlayer("task-1")
        dispatcher.scheduler.runCurrent()

        assertEquals(DownloadsOpenTarget.Player("media-1"), viewModel.openTarget.value)
    }

    @Test
    fun `opening a completed image task resolves it as an ImageViewer target, never the Player`() = runTest {
        libraryRepository.setItems(
            listOf(libraryItem(id = "media-1", sourceDownloadTaskId = "task-1").copy(mediaType = MediaType.IMAGE)),
        )
        val viewModel = viewModel()

        viewModel.openInPlayer("task-1")
        dispatcher.scheduler.runCurrent()

        assertEquals(DownloadsOpenTarget.ImageViewer("media-1"), viewModel.openTarget.value)
    }

    @Test
    fun `consuming the open signal clears it back to null`() = runTest {
        libraryRepository.setItems(listOf(libraryItem(id = "media-1", sourceDownloadTaskId = "task-1")))
        val viewModel = viewModel()
        viewModel.openInPlayer("task-1")
        dispatcher.scheduler.runCurrent()

        viewModel.consumeOpenInPlayer()

        assertNull(viewModel.openTarget.value)
    }

    @Test
    fun `opening a task with no matching library row surfaces an error instead of doing nothing`() = runTest {
        libraryRepository.setItems(listOf(libraryItem(id = "media-1", sourceDownloadTaskId = "some-other-task")))
        val viewModel = viewModel()

        viewModel.openInPlayer("task-1")
        dispatcher.scheduler.runCurrent()

        assertNull(viewModel.openTarget.value)
        assertEquals(
            "Couldn't find this item in your Library. It may have been removed or renamed.",
            viewModel.openInPlayerError.value,
        )
    }

    @Test
    fun `consuming the open-in-player error clears it back to null`() = runTest {
        libraryRepository.setItems(emptyList())
        val viewModel = viewModel()
        viewModel.openInPlayer("task-1")
        dispatcher.scheduler.runCurrent()

        viewModel.consumeOpenInPlayerError()

        assertNull(viewModel.openInPlayerError.value)
    }
}
