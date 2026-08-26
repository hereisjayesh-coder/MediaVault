package com.mediavault.app.player

import android.net.Uri
import com.mediavault.app.library.LibraryRepository
import com.mediavault.core.common.AppResult
import com.mediavault.core.database.entity.MediaItemEntity
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLibraryRepository : LibraryRepository {
    private val state = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    var existingIds: Set<String> = emptySet()
    val updatedPositions = mutableListOf<Pair<String, Long>>()
    var playlistSiblings: List<MediaItemEntity> = emptyList()

    fun setItems(items: List<MediaItemEntity>) {
        state.value = items
        existingIds = items.map { it.id }.toSet()
    }

    override fun observeAll() = state

    override suspend fun getById(id: String): MediaItemEntity? = state.value.firstOrNull { it.id == id }

    override fun fileFor(item: MediaItemEntity): File? = null

    override fun fileExists(item: MediaItemEntity): Boolean = item.id in existingIds

    override fun shareUriFor(item: MediaItemEntity): Uri? = null

    override suspend fun rename(id: String, newTitle: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun exportTo(id: String, targetUri: Uri): AppResult<Unit> = AppResult.Success(Unit)

    var saveToGalleryResult: AppResult<Unit> = AppResult.Success(Unit)
    val saveToGalleryCalls = mutableListOf<String>()

    override suspend fun saveToGallery(id: String): AppResult<Unit> {
        saveToGalleryCalls.add(id)
        return saveToGalleryResult
    }

    override suspend fun updatePlaybackPosition(id: String, positionMs: Long) {
        updatedPositions.add(id to positionMs)
        state.value = state.value.map { if (it.id == id) it.copy(lastPlaybackPositionMs = positionMs) else it }
    }

    override suspend fun getPlaylistSiblings(item: MediaItemEntity): List<MediaItemEntity> = playlistSiblings
}
