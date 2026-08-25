package com.mediavault.app.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.mediavault.app.util.nextAvailableFileName
import com.mediavault.app.util.sanitizeFileName
import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.database.dao.DownloadTaskDao
import com.mediavault.core.database.dao.MediaItemDao
import com.mediavault.core.database.entity.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * The Library's read/write surface over [MediaItemEntity] plus the real file operations a
 * three-dot menu needs (rename/delete/export/share). Room stays the single source of truth —
 * `delete` always removes the DB row even if the underlying file is already gone, so a library
 * item can never outlive both without the other silently lingering.
 *
 * Every new download since this milestone lands in app-private storage (`file://`), but items
 * completed by the previous milestone's SAF-based flow (`content://`, in a folder the user
 * picked) still have valid rows here too — see PROJECT_MASTER.md's private-storage decision.
 * Play/Share/Export/exists-checks honor both; Rename is file-only (SAF rename needs a document
 * tree permission grant this app no longer requests) and reports a clear "can't rename" instead
 * of silently failing.
 */
interface LibraryRepository {
    fun observeAll(): Flow<List<MediaItemEntity>>
    suspend fun getById(id: String): MediaItemEntity?

    /** The real file backing this item, or null for a non-`file://` (e.g. legacy SAF) item. */
    fun fileFor(item: MediaItemEntity): File?
    fun fileExists(item: MediaItemEntity): Boolean

    /** A URI safe to hand to another app (Share), or null if the underlying file is missing. */
    fun shareUriFor(item: MediaItemEntity): Uri?

    suspend fun rename(id: String, newTitle: String): AppResult<Unit>

    /** Removes the DB row regardless of whether the file still exists — never leaves an orphaned record. */
    suspend fun delete(id: String): AppResult<Unit>

    /** Copies this item's bytes to a user-chosen [targetUri] (from `ACTION_CREATE_DOCUMENT`). */
    suspend fun exportTo(id: String, targetUri: Uri): AppResult<Unit>

    suspend fun updatePlaybackPosition(id: String, positionMs: Long)

    /**
     * Every Library item downloaded as part of the same playlist as [item], in original
     * playlist order (including [item] itself) — the basis for the Player's Previous/Next
     * controls. Empty when [item] wasn't a playlist download, or when it was the only item
     * from that playlist to actually finish into the Library; callers should only show
     * Previous/Next when this has more than one entry.
     */
    suspend fun getPlaylistSiblings(item: MediaItemEntity): List<MediaItemEntity>
}

@Singleton
class AndroidLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MediaItemDao,
    private val downloadTaskDao: DownloadTaskDao,
) : LibraryRepository {

    override fun observeAll(): Flow<List<MediaItemEntity>> = dao.observeAll()

    override suspend fun getById(id: String): MediaItemEntity? = dao.getById(id)

    override fun fileFor(item: MediaItemEntity): File? {
        val uri = Uri.parse(item.mediaUri)
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        return File(path)
    }

    override fun fileExists(item: MediaItemEntity): Boolean {
        val uri = Uri.parse(item.mediaUri)
        return when (uri.scheme) {
            "file" -> uri.path?.let { File(it).exists() } == true
            "content" -> runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
            }.getOrDefault(false)
            else -> false
        }
    }

    override fun shareUriFor(item: MediaItemEntity): Uri? {
        val uri = Uri.parse(item.mediaUri)
        return when (uri.scheme) {
            "file" -> fileFor(item)?.takeIf { it.exists() }
                ?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
            "content" -> uri.takeIf { fileExists(item) }
            else -> null
        }
    }

    override suspend fun rename(id: String, newTitle: String): AppResult<Unit> {
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isEmpty()) return AppResult.Failure(AppError.Unsupported("Name can't be empty."))

        val item = dao.getById(id) ?: return AppResult.Failure(AppError.Unsupported("This item no longer exists."))
        val file = fileFor(item) ?: return AppResult.Failure(AppError.Unsupported("This item can't be renamed."))
        if (!file.exists()) return AppResult.Failure(AppError.Storage("This file is missing — it may have been moved or deleted."))

        val newFile = try {
            renameFile(file, trimmedTitle)
        } catch (e: SecurityException) {
            return AppResult.Failure(AppError.Permission("MediaVault couldn't rename this file."))
        }
        if (newFile == null) return AppResult.Failure(AppError.Storage("Couldn't rename this file."))

        dao.update(item.copy(title = trimmedTitle, mediaUri = Uri.fromFile(newFile).toString()))
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        val item = dao.getById(id) ?: return AppResult.Success(Unit)
        val uri = Uri.parse(item.mediaUri)
        when (uri.scheme) {
            "file" -> fileFor(item)?.let { runCatching { it.delete() } }
            "content" -> runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }
        dao.delete(item)
        return AppResult.Success(Unit)
    }

    override suspend fun exportTo(id: String, targetUri: Uri): AppResult<Unit> {
        val item = dao.getById(id) ?: return AppResult.Failure(AppError.Unsupported("This item no longer exists."))
        if (!fileExists(item)) {
            return AppResult.Failure(AppError.Storage("This file is missing — it may have been moved or deleted."))
        }

        return try {
            val output = context.contentResolver.openOutputStream(targetUri)
                ?: return AppResult.Failure(AppError.Storage("Couldn't open the chosen location for writing."))
            val input = context.contentResolver.openInputStream(Uri.parse(item.mediaUri))
                ?: return AppResult.Failure(AppError.Storage("Couldn't open this file for reading."))
            output.use { out -> input.use { it.copyTo(out) } }
            AppResult.Success(Unit)
        } catch (e: IOException) {
            AppResult.Failure(AppError.Storage("Couldn't export this file.", e))
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.Permission("MediaVault doesn't have permission to write there."))
        }
    }

    override suspend fun updatePlaybackPosition(id: String, positionMs: Long) {
        val item = dao.getById(id) ?: return
        dao.update(item.copy(lastPlaybackPositionMs = positionMs))
    }

    override suspend fun getPlaylistSiblings(item: MediaItemEntity): List<MediaItemEntity> {
        val taskId = item.sourceDownloadTaskId ?: return emptyList()
        val playlistId = downloadTaskDao.getById(taskId)?.playlistId ?: return emptyList()
        // Already ordered by playlistItemIndex; some entries may have no matching Library row
        // (failed/cancelled/still downloading), so map through a lookup rather than assuming a 1:1 join.
        val orderedTasks = downloadTaskDao.getByPlaylistId(playlistId)
        val libraryItemsByTaskId = dao.getBySourceDownloadTaskIds(orderedTasks.map { it.id })
            .associateBy { it.sourceDownloadTaskId }
        return orderedTasks.mapNotNull { libraryItemsByTaskId[it.id] }
    }
}

/**
 * Renames [file] on disk to a sanitized, collision-safe name derived from [newTitle], keeping
 * its original extension. No Context/DAO involved — real `File` I/O against a real directory,
 * so this is directly unit-testable (a temp directory works exactly like private storage does).
 * Returns the new [File], or null if the OS-level rename itself failed.
 */
internal fun renameFile(file: File, newTitle: String): File? {
    val extension = file.extension
    val desiredName = sanitizeFileName(newTitle) + (if (extension.isNotEmpty()) ".$extension" else "")
    val existingNames = (file.parentFile?.list()?.toSet().orEmpty()) - file.name
    val newFile = File(file.parentFile, nextAvailableFileName(desiredName, existingNames))
    return if (file.renameTo(newFile)) newFile else null
}
