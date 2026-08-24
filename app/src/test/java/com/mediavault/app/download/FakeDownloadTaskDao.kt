package com.mediavault.app.download

import com.mediavault.core.database.dao.DownloadTaskDao
import com.mediavault.core.database.entity.DownloadTaskEntity
import com.mediavault.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [DownloadTaskDao] — no Room/Android needed, so [MediaVaultDownloadEngine]'s queue/dedup/ordering logic can run in a plain JVM unit test. */
class FakeDownloadTaskDao : DownloadTaskDao {

    private val state = MutableStateFlow<List<DownloadTaskEntity>>(emptyList())

    val all: List<DownloadTaskEntity> get() = state.value

    override suspend fun upsert(task: DownloadTaskEntity) {
        state.value = state.value.filterNot { it.id == task.id } + task
    }

    override suspend fun update(task: DownloadTaskEntity) {
        state.value = state.value.map { if (it.id == task.id) task else it }
    }

    override suspend fun delete(task: DownloadTaskEntity) {
        state.value = state.value.filterNot { it.id == task.id }
    }

    override suspend fun getById(id: String): DownloadTaskEntity? = state.value.firstOrNull { it.id == id }

    override fun observeById(id: String): Flow<DownloadTaskEntity?> =
        state.map { list -> list.firstOrNull { it.id == id } }

    override fun observeAll(): Flow<List<DownloadTaskEntity>> = state

    override suspend fun getByStatuses(statuses: List<DownloadStatus>): List<DownloadTaskEntity> =
        state.value.filter { it.status in statuses }.sortedBy { it.createdAtEpochMs }

    override suspend fun reassignStatus(fromStatuses: List<DownloadStatus>, newStatus: DownloadStatus, nowMs: Long) {
        state.value = state.value.map {
            if (it.status in fromStatuses) it.copy(status = newStatus, updatedAtEpochMs = nowMs) else it
        }
    }

    override suspend fun getByPlaylistId(playlistId: String): List<DownloadTaskEntity> =
        state.value.filter { it.playlistId == playlistId }.sortedBy { it.playlistItemIndex ?: 0 }

    override suspend fun countBySourceMediaIdAndStatus(sourceMediaId: String, status: DownloadStatus): Int =
        state.value.count { it.sourceMediaId == sourceMediaId && it.status == status }
}
