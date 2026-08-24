package com.mediavault.app.download

import com.mediavault.core.database.dao.MediaItemDao
import com.mediavault.core.database.entity.MediaItemEntity
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMediaItemDao : MediaItemDao {

    private val state = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val all: List<MediaItemEntity> get() = state.value

    override suspend fun upsert(item: MediaItemEntity) {
        state.value = state.value.filterNot { it.id == item.id } + item
    }

    override suspend fun update(item: MediaItemEntity) {
        state.value = state.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun delete(item: MediaItemEntity) {
        state.value = state.value.filterNot { it.id == item.id }
    }

    override suspend fun getById(id: String): MediaItemEntity? = state.value.firstOrNull { it.id == id }

    override fun observeAll() = state

    override fun observeFavorites() = MutableStateFlow(state.value.filter { it.isFavorite })
}
