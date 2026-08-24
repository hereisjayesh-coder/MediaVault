package com.mediavault.core.domain.torrent

import com.mediavault.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Application-facing contract for magnet/`.torrent` handling. The initial implementation is
 * expected to wrap libtorrent, but nothing outside this module may depend on that fact.
 *
 * Deliberately out of scope: torrent discovery/search/indexing. This engine only manages
 * handles the user explicitly supplied (a magnet link or a `.torrent` file).
 */
interface TorrentEngine {

    suspend fun addMagnet(magnetUri: String): AppResult<TorrentHandle>

    suspend fun addTorrentFile(torrentFileUri: String): AppResult<TorrentHandle>

    suspend fun fetchMetadata(handleId: String): AppResult<TorrentMetadata>

    suspend fun selectFiles(handleId: String, fileIndices: Set<Int>)

    fun start(handleId: String)
    fun pause(handleId: String)
    fun remove(handleId: String, deleteFiles: Boolean)

    fun observeProgress(handleId: String): Flow<TorrentProgress>
}

data class TorrentHandle(val id: String)

data class TorrentMetadata(
    val name: String,
    val totalSizeBytes: Long,
    val files: List<TorrentFileInfo>,
)

data class TorrentFileInfo(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
)

data class TorrentProgress(
    val handleId: String,
    val status: TorrentStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val downloadRateBytesPerSecond: Long,
    val uploadRateBytesPerSecond: Long,
    val peerCount: Int,
    val seedCount: Int,
)

enum class TorrentStatus {
    FETCHING_METADATA,
    QUEUED,
    DOWNLOADING,
    SEEDING,
    PAUSED,
    COMPLETED,
    ERROR,
}
