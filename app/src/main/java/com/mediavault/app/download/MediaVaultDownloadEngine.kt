package com.mediavault.app.download

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.mediavault.app.util.DeviceStatusProvider
import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.database.dao.DownloadTaskDao
import com.mediavault.core.database.dao.MediaItemDao
import com.mediavault.core.database.entity.DownloadTaskEntity
import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.domain.download.DownloadEngine
import com.mediavault.core.domain.download.DownloadProgress
import com.mediavault.core.domain.download.DownloadRequest
import com.mediavault.core.domain.download.PlaylistDownloadRequest
import com.mediavault.core.domain.download.QualityDescriptor
import com.mediavault.core.domain.download.findMatching
import com.mediavault.core.domain.extractor.ExtractionEvent
import com.mediavault.core.domain.extractor.ExtractionRequest
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractionStage
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.domain.network.NetworkPolicyDecision
import com.mediavault.core.domain.network.NetworkPolicyManager
import com.mediavault.core.model.DownloadStatus
import com.mediavault.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Real [DownloadEngine]: persists every task in Room (the single source of truth, so the queue
 * survives process death), runs one transfer at a time via [ExtractorEngine.download], applies
 * [NetworkPolicyManager] before starting, and copies the finished file into the user's SAF
 * folder. Source-agnostic — everything here works the same regardless of which [ExtractorEngine]
 * is bound; nothing here knows about yt-dlp.
 */
@Singleton
class MediaVaultDownloadEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadTaskDao,
    private val mediaItemDao: MediaItemDao,
    private val extractorEngine: ExtractorEngine,
    private val networkPolicyManager: NetworkPolicyManager,
    private val deviceStatusProvider: DeviceStatusProvider,
    private val foregroundServiceStarter: DownloadForegroundServiceStarter,
) : DownloadEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val queueMutex = Mutex()

    /**
     * Resets anything left DOWNLOADING/PROCESSING by a killed process back to PAUSED, resumes
     * any playlist whose format resolution was interrupted mid-flight (ANALYZING tasks with no
     * live coroutine behind them any more), then resumes the queue.
     */
    fun recoverAfterProcessDeath() {
        engineScope.launch {
            dao.reassignStatus(
                fromStatuses = listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING),
                newStatus = DownloadStatus.PAUSED,
                nowMs = System.currentTimeMillis(),
            )
            val stuckPlaylistIds = dao.getByStatuses(listOf(DownloadStatus.ANALYZING)).playlistIdsNeedingResolution()
            stuckPlaylistIds.forEach { resolvePlaylistFormats(it) }
            processQueue()
        }
    }

    override fun enqueue(request: DownloadRequest) {
        engineScope.launch {
            val now = System.currentTimeMillis()
            dao.upsert(
                DownloadTaskEntity(
                    id = request.taskId,
                    sourceUrl = request.sourceUrl,
                    title = request.title,
                    sourceName = request.sourceName,
                    thumbnailUrl = request.thumbnailUrl,
                    mediaType = request.mediaType,
                    formatId = request.formatId,
                    container = request.container,
                    destinationTreeUri = request.destinationTreeUri,
                    destinationUri = null,
                    localCachePath = null,
                    status = DownloadStatus.QUEUED,
                    bytesTransferred = 0,
                    totalBytes = request.expectedSizeBytes,
                    canResume = request.canResume,
                    errorMessage = null,
                    sourceMediaId = request.sourceMediaId,
                    playlistId = request.playlistContext?.playlistId,
                    playlistItemIndex = request.playlistContext?.itemIndex,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                ),
            )
            foregroundServiceStarter.start()
            processQueue()
        }
    }

    override fun enqueuePlaylist(request: PlaylistDownloadRequest) {
        engineScope.launch {
            val alreadyDownloadedIds = if (request.skipAlreadyDownloaded) {
                request.items.map { it.sourceMediaId }
                    .filter { dao.countBySourceMediaIdAndStatus(it, DownloadStatus.COMPLETED) > 0 }
                    .toSet()
            } else {
                emptySet()
            }

            buildPlaylistTaskEntities(request, alreadyDownloadedIds, System.currentTimeMillis())
                .forEach { dao.upsert(it) }

            foregroundServiceStarter.start()
            resolvePlaylistFormats(request.playlistId)
        }
    }

    /**
     * Resolves every still-[DownloadStatus.ANALYZING] task in [playlistId] against the quality
     * it was queued with, one at a time (preserves order, avoids concurrent Chaquopy calls):
     * analyzes the item's own URL, finds the matching format, and either moves it to QUEUED
     * (letting [processQueue] pick it up immediately) or FAILED with a clear reason — a missing
     * format for one item never silently substitutes a different quality, and never blocks the
     * rest of the playlist. Also the resume path after process death — see [recoverAfterProcessDeath].
     */
    private suspend fun resolvePlaylistFormats(playlistId: String) {
        val pending = dao.getByPlaylistId(playlistId).filter { it.status == DownloadStatus.ANALYZING }
        val descriptor = pending.firstOrNull()?.toQualityDescriptor() ?: return

        for (task in pending) {
            // Re-fetch: another call (a per-task or whole-playlist cancel) may have already
            // moved this task out of ANALYZING while we were resolving an earlier item.
            val current = dao.getById(task.id) ?: continue
            if (current.status != DownloadStatus.ANALYZING) continue

            when (val outcome = extractorEngine.analyze(current.sourceUrl, current.id)) {
                is AppResult.Success -> {
                    val media = (outcome.data as? ExtractionResult.Single)?.media
                    val format = media?.formats?.findMatching(descriptor)
                    when {
                        media == null -> fail(current.id, AppError.Unsupported("This item is a nested playlist and can't be resolved directly."))
                        format == null -> fail(current.id, AppError.Unsupported("The selected quality isn't available for this item."))
                        else -> {
                            dao.update(
                                current.copy(
                                    status = DownloadStatus.QUEUED,
                                    formatId = format.formatId,
                                    container = format.container,
                                    mediaType = if (format.hasVideo) MediaType.VIDEO else MediaType.AUDIO,
                                    totalBytes = format.estimatedSizeBytes,
                                    canResume = format.supportsResume,
                                    updatedAtEpochMs = System.currentTimeMillis(),
                                ),
                            )
                            processQueue()
                        }
                    }
                }

                is AppResult.Failure -> fail(current.id, outcome.error)
            }
        }
    }

    override fun pause(taskId: String) {
        engineScope.launch {
            val task = dao.getById(taskId) ?: return@launch
            pauseTask(task)
        }
    }

    override fun pausePlaylist(playlistId: String) {
        engineScope.launch {
            dao.getByPlaylistId(playlistId).forEach { pauseTask(it) }
        }
    }

    private suspend fun pauseTask(task: DownloadTaskEntity) {
        extractorEngine.cancel(task.id)
        // Guards against a race where the transfer finishes (or fails) in the moment between
        // the user tapping Pause and this running — a terminal status must never be clobbered.
        if (task.status != DownloadStatus.DOWNLOADING && task.status != DownloadStatus.PROCESSING) return
        if (!task.canResume) {
            // This format's protocol can't safely continue from a byte offset (e.g. HLS/DASH
            // segments) — discard the partial file now so a later "resume" is an honest clean
            // restart instead of silently corrupting a half-written file.
            task.localCachePath?.let { runCatching { File(it).delete() } }
            dao.update(task.copy(status = DownloadStatus.PAUSED, bytesTransferred = 0, localCachePath = null, updatedAtEpochMs = System.currentTimeMillis()))
        } else {
            dao.update(task.copy(status = DownloadStatus.PAUSED, updatedAtEpochMs = System.currentTimeMillis()))
        }
    }

    override fun resume(taskId: String) {
        engineScope.launch {
            val task = dao.getById(taskId) ?: return@launch
            if (task.status != DownloadStatus.PAUSED) return@launch
            dao.update(task.copy(status = DownloadStatus.QUEUED, errorMessage = null, updatedAtEpochMs = System.currentTimeMillis()))
            foregroundServiceStarter.start()
            processQueue()
        }
    }

    override fun cancel(taskId: String) {
        engineScope.launch {
            val task = dao.getById(taskId) ?: return@launch
            cancelTask(task)
        }
    }

    override fun cancelPlaylist(playlistId: String) {
        engineScope.launch {
            dao.getByPlaylistId(playlistId).forEach { cancelTask(it) }
        }
    }

    private suspend fun cancelTask(task: DownloadTaskEntity) {
        extractorEngine.cancel(task.id)
        activeJobs[task.id]?.cancel()
        if (task.status == DownloadStatus.COMPLETED || task.status == DownloadStatus.CANCELLED) return
        task.localCachePath?.let { runCatching { File(it).delete() } }
        dao.update(task.copy(status = DownloadStatus.CANCELLED, updatedAtEpochMs = System.currentTimeMillis()))
    }

    override fun retry(taskId: String) {
        engineScope.launch {
            val task = dao.getById(taskId) ?: return@launch
            retryTask(task)
            foregroundServiceStarter.start()
            task.playlistId?.let { resolvePlaylistFormats(it) }
            processQueue()
        }
    }

    override fun retryFailedInPlaylist(playlistId: String) {
        engineScope.launch {
            dao.getByPlaylistId(playlistId)
                .filter { it.status == DownloadStatus.FAILED }
                .forEach { retryTask(it) }
            foregroundServiceStarter.start()
            resolvePlaylistFormats(playlistId)
            processQueue()
        }
    }

    private suspend fun retryTask(task: DownloadTaskEntity) {
        val nextStatus = task.retryNextStatusOrNull() ?: return
        dao.update(task.copy(status = nextStatus, errorMessage = null, updatedAtEpochMs = System.currentTimeMillis()))
    }

    override suspend fun isAlreadyDownloaded(sourceMediaId: String): Boolean =
        dao.countBySourceMediaIdAndStatus(sourceMediaId, DownloadStatus.COMPLETED) > 0

    override fun observeProgress(taskId: String): Flow<DownloadProgress> =
        dao.observeById(taskId).filterNotNull().map { it.toDownloadProgress() }

    override fun observeAll(): Flow<List<DownloadProgress>> =
        dao.observeAll().map { tasks -> tasks.map { it.toDownloadProgress() } }

    // --- Queue processing -------------------------------------------------------------------

    private suspend fun processQueue() {
        val next = queueMutex.withLock {
            val running = dao.getByStatuses(listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING))
            if (running.isNotEmpty()) return
            dao.getByStatuses(listOf(DownloadStatus.QUEUED)).firstOrNull()
        } ?: return

        val job = engineScope.launch { runDownload(next) }
        activeJobs[next.id] = job
        job.invokeOnCompletion {
            activeJobs.remove(next.id)
            engineScope.launch { processQueue() }
        }
    }

    private suspend fun runDownload(task: DownloadTaskEntity) {
        val decision = networkPolicyManager.evaluate(task.totalBytes ?: 0L)
        when (decision) {
            is NetworkPolicyDecision.Block -> {
                fail(task.id, AppError.Network(decision.reason))
                return
            }
            is NetworkPolicyDecision.QueueForWifi -> {
                // Leave it QUEUED — the user (or a future auto-retry-on-Wi-Fi) can retry later.
                updateTask(task.id) {
                    it.copy(errorMessage = "Waiting for Wi-Fi — this exceeds your per-download mobile-data limit.")
                }
                return
            }
            is NetworkPolicyDecision.Warn, NetworkPolicyDecision.Allow -> Unit
        }

        val expected = task.totalBytes ?: 0L
        if (expected > 0 && deviceStatusProvider.freeStorageBytes() < expected) {
            fail(task.id, AppError.Storage("Not enough free storage for this download."))
            return
        }

        val cachePath = task.localCachePath
            ?: File(context.cacheDir, "downloads/${task.id}.${task.container ?: "bin"}").path
        updateTask(task.id) { it.copy(status = DownloadStatus.DOWNLOADING, localCachePath = cachePath) }

        val request = ExtractionRequest(
            taskId = task.id,
            sourceUrl = task.sourceUrl,
            formatId = task.formatId.orEmpty(),
            destinationPath = cachePath,
        )

        extractorEngine.download(request).collect { event ->
            when (event) {
                is ExtractionEvent.Progress -> updateTask(task.id) {
                    it.copy(
                        status = if (event.stage == ExtractionStage.PROCESSING) DownloadStatus.PROCESSING else DownloadStatus.DOWNLOADING,
                        bytesTransferred = event.bytesTransferred,
                        totalBytes = event.totalBytes ?: it.totalBytes,
                    )
                }

                is ExtractionEvent.Completed -> finish(task.id, event.outputPath)

                is ExtractionEvent.Failed -> fail(task.id, AppError.Unknown(event.message, event.cause))
            }
        }
        // If the flow ended without a terminal event, it was our own pause()/cancel() — the DB
        // status those already set (PAUSED/CANCELLED) stands; there's nothing more to do here.
    }

    private suspend fun finish(taskId: String, cacheOutputPath: String) {
        updateTask(taskId) { it.copy(status = DownloadStatus.PROCESSING) }
        val task = dao.getById(taskId) ?: return

        val finalUri = try {
            withContext(Dispatchers.IO) { copyToDestination(task, cacheOutputPath) }
        } catch (e: Exception) {
            fail(taskId, e.toDownloadAppError())
            return
        }

        val now = System.currentTimeMillis()
        val completed = task.copy(
            status = DownloadStatus.COMPLETED,
            destinationUri = finalUri,
            localCachePath = null,
            errorMessage = null,
            updatedAtEpochMs = now,
        )
        dao.update(completed)

        val networkType = networkPolicyManager.currentNetworkType()
        networkPolicyManager.recordTransferredBytes(networkType, completed.bytesTransferred)

        mediaItemDao.upsert(
            MediaItemEntity(
                id = UUID.randomUUID().toString(),
                title = completed.title ?: "Untitled",
                mediaUri = finalUri,
                mediaType = completed.mediaType,
                durationMs = null,
                sizeBytes = completed.bytesTransferred,
                container = completed.container,
                isImported = false,
                sourceDownloadTaskId = completed.id,
                lastPlaybackPositionMs = 0,
                isFavorite = false,
                addedAtEpochMs = now,
            ),
        )
    }

    /** Copies the finished cache file into the user's SAF folder and deletes the cache copy. */
    private fun copyToDestination(task: DownloadTaskEntity, cacheOutputPath: String): String {
        val treeUriString = task.destinationTreeUri
            ?: throw IllegalStateException("No destination folder was selected for this download.")
        val cacheFile = File(cacheOutputPath)
        if (!cacheFile.exists()) throw java.io.IOException("Downloaded file went missing before it could be saved.")

        val extension = task.container ?: cacheFile.extension.ifBlank { "bin" }
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        // A playlist-item prefix keeps files in playlist order in the destination folder and,
        // since siblings can otherwise share a title, meaningfully reduces name collisions —
        // the SAF provider itself auto-suffixes any name that still collides ("Title (1).ext"),
        // so no file is ever silently overwritten either way.
        val indexPrefix = task.playlistItemIndex?.let { "%03d - ".format(it) }.orEmpty()
        val fileName = indexPrefix + sanitizeFileName(task.title ?: task.id) + "." + extension

        val treeUri = Uri.parse(treeUriString)
        val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val newFileUri = DocumentsContract.createDocument(context.contentResolver, treeDocUri, mimeType, fileName)
            ?: throw java.io.IOException("Couldn't create the destination file.")

        context.contentResolver.openOutputStream(newFileUri)?.use { output ->
            cacheFile.inputStream().use { input -> input.copyTo(output) }
        } ?: throw java.io.IOException("Couldn't open the destination file for writing.")

        runCatching { cacheFile.delete() }
        return newFileUri.toString()
    }

    private suspend fun fail(taskId: String, error: AppError) {
        updateTask(taskId) { it.copy(status = DownloadStatus.FAILED, errorMessage = error.message) }
    }

    private suspend fun updateTask(taskId: String, transform: (DownloadTaskEntity) -> DownloadTaskEntity) {
        val current = dao.getById(taskId) ?: return
        dao.update(transform(current).copy(updatedAtEpochMs = System.currentTimeMillis()))
    }
}

private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(150).ifBlank { "download" }

/**
 * Pure: turns a [PlaylistDownloadRequest] into the rows to insert, in playlist order, with
 * items in [alreadyDownloadedSourceMediaIds] marked CANCELLED instead of ANALYZING — no DAO,
 * no coroutines, so this is directly unit-testable without Room or Android.
 */
internal fun buildPlaylistTaskEntities(
    request: PlaylistDownloadRequest,
    alreadyDownloadedSourceMediaIds: Set<String>,
    nowMs: Long,
): List<DownloadTaskEntity> = request.items.mapIndexed { offset, item ->
    val alreadyDownloaded = item.sourceMediaId in alreadyDownloadedSourceMediaIds
    DownloadTaskEntity(
        id = UUID.randomUUID().toString(),
        sourceUrl = item.sourceUrl,
        title = item.title,
        sourceName = request.sourceName,
        thumbnailUrl = item.thumbnailUrl,
        mediaType = MediaType.VIDEO, // placeholder — corrected once this item's format resolves
        formatId = null,
        container = null,
        destinationTreeUri = request.destinationTreeUri,
        destinationUri = null,
        localCachePath = null,
        status = if (alreadyDownloaded) DownloadStatus.CANCELLED else DownloadStatus.ANALYZING,
        bytesTransferred = 0,
        totalBytes = null,
        canResume = false,
        errorMessage = if (alreadyDownloaded) "Already downloaded — skipped" else null,
        sourceMediaId = item.sourceMediaId,
        playlistId = request.playlistId,
        playlistItemIndex = item.itemIndex,
        playlistTitle = request.playlistTitle,
        playlistThumbnailUrl = request.playlistThumbnailUrl,
        qualityResolutionLabel = request.qualityDescriptor.resolutionLabel,
        qualityContainer = request.qualityDescriptor.container,
        qualityHasVideo = request.qualityDescriptor.hasVideo,
        qualityHasAudio = request.qualityDescriptor.hasAudio,
        // Offset by item order, not wall-clock arrival, so playlist order survives even
        // though every row would otherwise be inserted with the same millisecond timestamp.
        createdAtEpochMs = nowMs + offset,
        updatedAtEpochMs = nowMs + offset,
    )
}

/**
 * Pure: what status a retry should move this task to, or null if it isn't retryable right
 * now. A playlist task that failed/was skipped before ever resolving a format (no formatId
 * yet) needs its format resolved again, not a raw re-download attempt.
 */
internal fun DownloadTaskEntity.retryNextStatusOrNull(): DownloadStatus? {
    if (status != DownloadStatus.FAILED && status != DownloadStatus.CANCELLED) return null
    return if (playlistId != null && formatId == null) DownloadStatus.ANALYZING else DownloadStatus.QUEUED
}

/** Pure: which playlists have format resolution stuck (an ANALYZING task with no live coroutine behind it — e.g. after process death). */
internal fun List<DownloadTaskEntity>.playlistIdsNeedingResolution(): List<String> =
    filter { it.status == DownloadStatus.ANALYZING }.mapNotNull { it.playlistId }.distinct()

/** Null unless every quality field was persisted — always true together for a playlist task, never set for a single-item one. */
private fun DownloadTaskEntity.toQualityDescriptor(): QualityDescriptor? {
    val container = qualityContainer ?: return null
    val hasVideo = qualityHasVideo ?: return null
    val hasAudio = qualityHasAudio ?: return null
    return QualityDescriptor(qualityResolutionLabel, container, hasVideo, hasAudio)
}

private fun DownloadTaskEntity.toDownloadProgress(): DownloadProgress = DownloadProgress(
    taskId = id,
    title = title,
    sourceName = sourceName,
    thumbnailUrl = thumbnailUrl,
    status = status,
    bytesTransferred = bytesTransferred,
    totalBytes = totalBytes,
    throughputBytesPerSecond = null,
    etaSeconds = null,
    canResume = canResume,
    errorMessage = errorMessage,
    destinationUri = destinationUri,
    createdAtEpochMs = createdAtEpochMs,
    playlistId = playlistId,
    playlistItemIndex = playlistItemIndex,
    playlistTitle = playlistTitle,
    playlistThumbnailUrl = playlistThumbnailUrl,
)
