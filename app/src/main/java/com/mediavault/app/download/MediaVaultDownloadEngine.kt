package com.mediavault.app.download

import android.content.Context
import android.net.Uri
import com.mediavault.app.storage.MediaVaultStorage
import com.mediavault.app.util.DeviceStatusProvider
import com.mediavault.app.util.nextAvailableFileName
import com.mediavault.app.util.sanitizeFileName
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
import com.mediavault.core.domain.download.buildDownloadOptions
import com.mediavault.core.domain.download.findMatching
import com.mediavault.core.domain.extractor.ExtractionEvent
import com.mediavault.core.domain.extractor.ExtractionRequest
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractionStage
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.domain.network.NetworkPolicyDecision
import com.mediavault.core.domain.network.NetworkPolicyManager
import com.mediavault.core.domain.processing.MediaProcessor
import com.mediavault.core.domain.processing.MergeRequest
import com.mediavault.core.domain.processing.ProcessingEvent
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
 * [NetworkPolicyManager] before starting, and copies the finished file into MediaVault's
 * app-private [MediaVaultStorage] — see PROJECT_MASTER.md's private-storage decision.
 * Source-agnostic — everything here works the same regardless of which [ExtractorEngine] is
 * bound; nothing here knows about yt-dlp.
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
    private val mediaVaultStorage: MediaVaultStorage,
    private val mediaProcessor: MediaProcessor,
) : DownloadEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val queueMutex = Mutex()

    /**
     * Resets anything left DOWNLOADING/PROCESSING/MERGING by a killed process back to PAUSED
     * (a split-stream task interrupted mid-merge just re-downloads both streams and re-merges
     * on resume — wasteful but safe, same as any other non-resumable task restarting clean),
     * resumes any playlist whose format resolution was interrupted mid-flight (ANALYZING tasks
     * with no live coroutine behind them any more), then resumes the queue.
     */
    fun recoverAfterProcessDeath() {
        engineScope.launch {
            dao.reassignStatus(
                fromStatuses = listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING, DownloadStatus.MERGING),
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
                    audioFormatId = request.audioFormatId,
                    container = request.container,
                    destinationTreeUri = null,
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
                    playlistTitle = request.playlistContext?.playlistTitle,
                    playlistThumbnailUrl = request.playlistContext?.playlistThumbnailUrl,
                    durationSeconds = request.durationSeconds,
                    resolutionLabel = request.resolutionLabel,
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
                    // Rebuilds this item's own video+audio pairing independently — same
                    // `buildDownloadOptions` the single-item flow uses, so a merge-required
                    // quality resolves through the exact same pairing/language logic, never a
                    // second, duplicated implementation of it.
                    val option = media?.formats?.let(::buildDownloadOptions)?.findMatching(descriptor)
                    when {
                        media == null -> fail(current.id, AppError.Unsupported("This item is a nested playlist and can't be resolved directly."))
                        option == null -> fail(current.id, AppError.Unsupported("The selected quality isn't available for this item."))
                        else -> {
                            val primaryFormat = option.videoFormat ?: option.audioFormat
                            dao.update(
                                current.copy(
                                    status = DownloadStatus.QUEUED,
                                    formatId = primaryFormat?.formatId,
                                    audioFormatId = option.audioFormat?.formatId.takeIf { option.requiresProcessing },
                                    container = option.outputContainer,
                                    mediaType = if (option.videoFormat != null) MediaType.VIDEO else MediaType.AUDIO,
                                    totalBytes = option.combinedEstimatedSizeBytes,
                                    // A split video+audio task is never byte-offset-resumable — same rule as the single-item flow.
                                    canResume = if (option.requiresProcessing) false else primaryFormat?.supportsResume ?: false,
                                    durationSeconds = media.durationSeconds ?: current.durationSeconds,
                                    resolutionLabel = option.videoFormat?.resolutionLabel,
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
        // MERGING isn't pausable (a stream-copy remux of already-downloaded files is normally a
        // few seconds) — Pause is a no-op there; Cancel (below) still works mid-merge.
        if (task.status != DownloadStatus.DOWNLOADING && task.status != DownloadStatus.PROCESSING) return
        if (!task.canResume) {
            // This format's protocol can't safely continue from a byte offset (e.g. HLS/DASH
            // segments) — discard the partial file now so a later "resume" is an honest clean
            // restart instead of silently corrupting a half-written file. A split video+audio
            // task is never resumable (see enqueue()'s canResume=false for paired requests), so
            // this branch also always applies to those, clearing both cache files.
            task.localCachePath?.let { runCatching { File(it).delete() } }
            task.audioLocalCachePath?.let { runCatching { File(it).delete() } }
            dao.update(
                task.copy(
                    status = DownloadStatus.PAUSED,
                    bytesTransferred = 0,
                    localCachePath = null,
                    audioLocalCachePath = null,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
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
        mediaProcessor.cancel(task.id)
        activeJobs[task.id]?.cancel()
        if (task.status == DownloadStatus.COMPLETED || task.status == DownloadStatus.CANCELLED) return
        task.localCachePath?.let { runCatching { File(it).delete() } }
        task.audioLocalCachePath?.let { runCatching { File(it).delete() } }
        dao.update(
            task.copy(
                status = DownloadStatus.CANCELLED,
                audioLocalCachePath = null,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
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

    override fun remove(taskId: String) {
        engineScope.launch {
            val task = dao.getById(taskId) ?: return@launch
            if (!task.isRemovable()) return@launch
            // Terminal already (isRemovable() guarantees FAILED/CANCELLED/COMPLETED), but a
            // stray cache file from an interrupted attempt can still be sitting there — clean
            // it up same as cancel() does. Never touches mediaItemDao: a COMPLETED task's
            // Library row is a separate table, deleted only via the Library's own delete action.
            task.localCachePath?.let { runCatching { File(it).delete() } }
            task.audioLocalCachePath?.let { runCatching { File(it).delete() } }
            dao.delete(task)
        }
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
            val running = dao.getByStatuses(listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING, DownloadStatus.MERGING))
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

        if (task.audioFormatId != null) {
            runSplitStreamDownload(task)
        } else {
            runSingleStreamDownload(task)
        }
    }

    /** The original, unmodified direct-download path — a muxed or audio-only format downloaded straight to its final container, no processing. */
    private suspend fun runSingleStreamDownload(task: DownloadTaskEntity) {
        val cachePath = task.localCachePath
            ?: File(context.cacheDir, "downloads/${task.id}.${task.container ?: "bin"}").path
        updateTask(task.id) { it.copy(status = DownloadStatus.DOWNLOADING, localCachePath = cachePath) }

        val request = ExtractionRequest(
            taskId = task.id,
            sourceUrl = task.sourceUrl,
            formatId = task.formatId.orEmpty(),
            destinationPath = cachePath,
            preferredEngineId = task.preferredEngineIdOrNull(),
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

    /**
     * Downloads the video-only stream, then the audio-only stream, then hands both to
     * [mediaProcessor] to remux — see `DownloadOption.requiresProcessing`/`MediaProcessor`.
     * Combined progress is reported throughout: [DownloadTaskEntity.totalBytes] is the combined
     * estimate set at enqueue time and never overwritten mid-flight, so the bar doesn't jump
     * around as each phase reports its own (smaller) total.
     */
    private suspend fun runSplitStreamDownload(task: DownloadTaskEntity) {
        val videoCachePath = task.localCachePath
            ?: File(context.cacheDir, "downloads/${task.id}.video.tmp").path
        val audioCachePath = task.audioLocalCachePath
            ?: File(context.cacheDir, "downloads/${task.id}.audio.tmp").path
        updateTask(task.id) {
            it.copy(status = DownloadStatus.DOWNLOADING, localCachePath = videoCachePath, audioLocalCachePath = audioCachePath)
        }

        val videoOutcome = collectStream(task.id, task.sourceUrl, task.formatId.orEmpty(), videoCachePath, baseBytesTransferred = 0L)
        when (videoOutcome) {
            is StreamOutcome.Failed -> {
                fail(task.id, videoOutcome.error)
                cleanupSplitCache(videoCachePath, audioCachePath)
                return
            }
            StreamOutcome.Stopped -> return // pause()/cancel() already set the terminal DB state
            StreamOutcome.Completed -> Unit
        }

        val videoBytes = runCatching { File(videoCachePath).length() }.getOrDefault(0L)
        val audioOutcome = collectStream(task.id, task.sourceUrl, task.audioFormatId.orEmpty(), audioCachePath, baseBytesTransferred = videoBytes)
        when (audioOutcome) {
            is StreamOutcome.Failed -> {
                fail(task.id, audioOutcome.error)
                cleanupSplitCache(videoCachePath, audioCachePath)
                return
            }
            StreamOutcome.Stopped -> return
            StreamOutcome.Completed -> Unit
        }

        mergeAndFinish(task.id, videoCachePath, audioCachePath)
    }

    private sealed class StreamOutcome {
        object Completed : StreamOutcome()
        object Stopped : StreamOutcome()
        data class Failed(val error: AppError) : StreamOutcome()
    }

    /** Downloads one stream, reporting combined progress as `baseBytesTransferred + this stream's own bytes`. */
    private suspend fun collectStream(
        taskId: String,
        sourceUrl: String,
        formatId: String,
        cachePath: String,
        baseBytesTransferred: Long,
    ): StreamOutcome {
        var outcome: StreamOutcome = StreamOutcome.Completed
        val request = ExtractionRequest(taskId = taskId, sourceUrl = sourceUrl, formatId = formatId, destinationPath = cachePath)

        extractorEngine.download(request).collect { event ->
            when (event) {
                is ExtractionEvent.Progress -> updateTask(taskId) {
                    it.copy(
                        status = if (event.stage == ExtractionStage.PROCESSING) DownloadStatus.PROCESSING else DownloadStatus.DOWNLOADING,
                        bytesTransferred = baseBytesTransferred + event.bytesTransferred,
                        totalBytes = it.totalBytes ?: event.totalBytes,
                    )
                }

                is ExtractionEvent.Completed -> outcome = StreamOutcome.Completed
                is ExtractionEvent.Failed -> outcome = StreamOutcome.Failed(AppError.Unknown(event.message, event.cause))
            }
        }

        val current = dao.getById(taskId)
        if (current != null && current.status != DownloadStatus.DOWNLOADING && current.status != DownloadStatus.PROCESSING) {
            return StreamOutcome.Stopped
        }
        return outcome
    }

    private fun cleanupSplitCache(videoCachePath: String, audioCachePath: String) {
        runCatching { File(videoCachePath).delete() }
        runCatching { File(audioCachePath).delete() }
    }

    /** Remuxes the two downloaded streams, then hands the merged file to the same [finish] every direct download already uses to reach the Library. */
    private suspend fun mergeAndFinish(taskId: String, videoCachePath: String, audioCachePath: String) {
        val task = dao.getById(taskId) ?: return
        updateTask(taskId) { it.copy(status = DownloadStatus.MERGING) }

        val outputContainer = task.container ?: "mkv"
        val mergedPath = File(context.cacheDir, "downloads/$taskId.merged.$outputContainer").path

        val mergeRequest = MergeRequest(
            taskId = taskId,
            videoPath = videoCachePath,
            audioPath = audioCachePath,
            outputPath = mergedPath,
            outputContainer = outputContainer,
            estimatedDurationSeconds = task.durationSeconds,
        )

        var result: AppResult<String>? = null
        mediaProcessor.merge(mergeRequest).collect { event ->
            when (event) {
                is ProcessingEvent.Progress -> updateTask(taskId) { it.copy(status = DownloadStatus.MERGING) }
                is ProcessingEvent.Completed -> result = AppResult.Success(event.outputPath)
                is ProcessingEvent.Failed -> result = AppResult.Failure(AppError.Unknown(event.message, event.cause))
            }
        }

        val current = dao.getById(taskId)
        if (current != null && current.status != DownloadStatus.MERGING) {
            // pause()/cancel() raced the merge and already set a terminal state — leave it.
            runCatching { File(mergedPath).delete() }
            return
        }

        when (val outcome = result) {
            null -> {
                fail(taskId, AppError.Unknown("Merging was interrupted before it could finish."))
                cleanupSplitCache(videoCachePath, audioCachePath)
                runCatching { File(mergedPath).delete() }
            }
            is AppResult.Failure -> {
                fail(taskId, outcome.error)
                cleanupSplitCache(videoCachePath, audioCachePath)
                runCatching { File(mergedPath).delete() }
            }
            is AppResult.Success -> {
                cleanupSplitCache(videoCachePath, audioCachePath)
                finish(taskId, outcome.data)
            }
        }
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
            audioLocalCachePath = null,
            errorMessage = null,
            updatedAtEpochMs = now,
        )
        dao.update(completed)

        val networkType = networkPolicyManager.currentNetworkType()
        networkPolicyManager.recordTransferredBytes(networkType, completed.bytesTransferred)

        mediaItemDao.upsert(
            buildMediaItemEntity(completed, finalUri, UUID.randomUUID().toString(), now),
        )
    }

    /**
     * Copies the finished cache file into MediaVault's private media directory and deletes the
     * cache copy. Never touches SAF/MediaStore — new downloads are app-private by design (see
     * PROJECT_MASTER.md's private-storage decision), so nothing here exposes the file to the
     * public Gallery.
     */
    private fun copyToDestination(task: DownloadTaskEntity, cacheOutputPath: String): String {
        val cacheFile = File(cacheOutputPath)
        if (!cacheFile.exists()) throw java.io.IOException("Downloaded file went missing before it could be saved.")

        if (mediaVaultStorage.freeSpaceBytes() < cacheFile.length()) {
            throw java.io.IOException("Not enough storage space to finish this download.")
        }

        val extension = task.container ?: cacheFile.extension.ifBlank { "bin" }
        // A playlist-item prefix keeps files in playlist order and, since siblings can otherwise
        // share a title, meaningfully reduces name collisions — nextAvailableFileName is the
        // final safety net so a same-named file is never silently overwritten.
        val indexPrefix = task.playlistItemIndex?.let { "%03d - ".format(it) }.orEmpty()
        val desiredName = indexPrefix + sanitizeFileName(task.title ?: task.id) + "." + extension

        val mediaDir = mediaVaultStorage.mediaDirectory()
        val existingNames = mediaDir.list()?.toSet() ?: emptySet()
        val destinationFile = File(mediaDir, nextAvailableFileName(desiredName, existingNames))

        destinationFile.outputStream().use { output ->
            cacheFile.inputStream().use { input -> input.copyTo(output) }
        }

        runCatching { cacheFile.delete() }
        return Uri.fromFile(destinationFile).toString()
    }

    private suspend fun fail(taskId: String, error: AppError) {
        updateTask(taskId) { it.copy(status = DownloadStatus.FAILED, errorMessage = error.message) }
    }

    private suspend fun updateTask(taskId: String, transform: (DownloadTaskEntity) -> DownloadTaskEntity) {
        val current = dao.getById(taskId) ?: return
        dao.update(transform(current).copy(updatedAtEpochMs = System.currentTimeMillis()))
    }
}

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
        destinationTreeUri = null,
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
        qualityRequiresProcessing = request.qualityDescriptor.requiresProcessing,
        qualityAudioLanguageCode = request.qualityDescriptor.audioLanguageCode,
        durationSeconds = item.durationSeconds,
        resolutionLabel = null, // unknown until this item's own format resolves
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

/**
 * Whether [DownloadEngine.remove] may delete this task's own queue record — only ever true once
 * the task is done one way or another. Never true for anything still active or queued: removing
 * the row out from under a running/scheduled transfer would orphan its cache file and progress
 * updates. Deliberately never checks anything about the Library — a COMPLETED task's media row
 * lives in a separate table this function knows nothing about, which is exactly the point.
 */
internal fun DownloadTaskEntity.isRemovable(): Boolean =
    status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED || status == DownloadStatus.COMPLETED

/**
 * Routing hint for [com.mediavault.app.extractor.CompositeExtractorEngine] — see
 * [ExtractionRequest.preferredEngineId]'s own KDoc for why this exists at all. Only meaningful
 * for an Instagram image/carousel, where yt-dlp and Instaloader can both legitimately claim the
 * same URL. A Reddit image post is IMAGE too, but only yt-dlp's `canHandle` ever agrees to it —
 * [com.mediavault.app.extractor.CompositeExtractorEngine] already resolves that case correctly
 * on its own (from analyze-time memory, or its own `canHandle` fallback after a cold process
 * restart), so hinting "instaloader" for every IMAGE task regardless of source would incorrectly
 * force a Reddit image download through a backend that was never involved in resolving it.
 */
internal fun DownloadTaskEntity.preferredEngineIdOrNull(): String? =
    if (mediaType == MediaType.IMAGE && sourceUrl.contains("instagram.com", ignoreCase = true)) "instaloader" else null

/** Pure: which playlists have format resolution stuck (an ANALYZING task with no live coroutine behind it — e.g. after process death). */
internal fun List<DownloadTaskEntity>.playlistIdsNeedingResolution(): List<String> =
    filter { it.status == DownloadStatus.ANALYZING }.mapNotNull { it.playlistId }.distinct()

/** Null unless every quality field was persisted — always true together for a playlist task, never set for a single-item one. */
private fun DownloadTaskEntity.toQualityDescriptor(): QualityDescriptor? {
    val container = qualityContainer ?: return null
    val hasVideo = qualityHasVideo ?: return null
    val hasAudio = qualityHasAudio ?: return null
    return QualityDescriptor(
        resolutionLabel = qualityResolutionLabel,
        container = container,
        hasVideo = hasVideo,
        hasAudio = hasAudio,
        requiresProcessing = qualityRequiresProcessing ?: false,
        audioLanguageCode = qualityAudioLanguageCode,
    )
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

/** Pure: maps a just-COMPLETED task to the Library row it becomes — no DAO, directly unit-testable. */
internal fun buildMediaItemEntity(task: DownloadTaskEntity, mediaUri: String, id: String, nowMs: Long) =
    MediaItemEntity(
        id = id,
        title = task.title ?: "Untitled",
        mediaUri = mediaUri,
        mediaType = task.mediaType,
        durationMs = task.durationSeconds?.let { it * 1000 },
        sizeBytes = task.bytesTransferred,
        container = task.container,
        resolutionLabel = task.resolutionLabel,
        thumbnailUrl = task.thumbnailUrl,
        isImported = false,
        sourceDownloadTaskId = task.id,
        lastPlaybackPositionMs = 0,
        isFavorite = false,
        addedAtEpochMs = nowMs,
    )
