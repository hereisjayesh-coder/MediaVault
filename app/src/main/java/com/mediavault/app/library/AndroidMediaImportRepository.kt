package com.mediavault.app.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.database.dao.MediaItemDao
import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidMediaImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MediaItemDao,
    private val probe: MediaMetadataProbe,
) : MediaImportRepository {

    override suspend fun importFile(uri: Uri): AppResult<MediaItemEntity> = withContext(Dispatchers.IO) {
        takePersistableReadPermission(uri)

        val doc = DocumentFile.fromSingleUri(context, uri)
            ?: return@withContext AppResult.Failure(AppError.Permission("Couldn't access that file."))
        val name = doc.name
            ?: return@withContext AppResult.Failure(AppError.Unsupported("Couldn't determine this file's name."))
        val mediaType = mediaTypeForExtension(extensionOf(name))
            ?: return@withContext AppResult.Failure(AppError.Unsupported("\"$name\" isn't a supported audio/video format."))

        val entity = buildEntity(uri, name, mediaType, doc.length().takeIf { it > 0 })
        dao.upsert(entity)
        AppResult.Success(entity)
    }

    override suspend fun importFolder(treeUri: Uri): AppResult<FolderImportResult> = withContext(Dispatchers.IO) {
        takePersistableReadPermission(treeUri)

        val treeDoc = DocumentFile.fromTreeUri(context, treeUri)?.takeIf { it.isDirectory }
            ?: return@withContext AppResult.Failure(AppError.Permission("Couldn't access that folder."))
        // A single, non-recursive listing — deliberately never descends into subfolders, so a
        // folder import can never turn into an unbounded device-wide scan.
        val children = runCatching { treeDoc.listFiles() }.getOrNull()
            ?: return@withContext AppResult.Failure(AppError.Permission("Couldn't read that folder's contents."))

        var skipped = 0
        val imported = mutableListOf<MediaItemEntity>()
        for (child in children) {
            if (!child.isFile) continue
            val name = child.name
            val mediaType = name?.let { mediaTypeForExtension(extensionOf(it)) }
            if (name == null || mediaType == null) {
                skipped++
                continue
            }
            val entity = buildEntity(child.uri, name, mediaType, child.length().takeIf { it > 0 })
            dao.upsert(entity)
            imported += entity
        }
        AppResult.Success(FolderImportResult(imported = imported, skippedCount = skipped))
    }

    /** Best-effort: probing failure degrades to null metadata fields rather than dropping a file the user explicitly chose to import — it may still be perfectly playable even when [MediaMetadataProbe] itself can't read it. */
    private suspend fun buildEntity(uri: Uri, displayName: String, mediaType: MediaType, sizeBytes: Long?): MediaItemEntity {
        val probed = probe.probe(uri, mediaType)
        val id = UUID.randomUUID().toString()
        return MediaItemEntity(
            id = id,
            title = titleFromFileName(displayName),
            mediaUri = uri.toString(),
            mediaType = mediaType,
            durationMs = probed?.durationMs,
            sizeBytes = sizeBytes,
            container = extensionOf(displayName).ifBlank { null },
            resolutionLabel = probed?.resolutionLabel,
            thumbnailUrl = probed?.thumbnail?.let { persistThumbnail(id, it) },
            isImported = true,
            sourceDownloadTaskId = null,
            lastPlaybackPositionMs = 0L,
            isFavorite = false,
            addedAtEpochMs = System.currentTimeMillis(),
        )
    }

    /** `ACTION_OPEN_DOCUMENT[_TREE]` grants are only ever requested as read-only — MediaVault never needs to write into a user-picked import location. Best-effort: some providers don't support persistence at all, which shouldn't fail the import itself, only the "survives an app restart" guarantee for that one item. */
    private fun takePersistableReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun persistThumbnail(id: String, bitmap: Bitmap): String? = runCatching {
        val dir = File(context.cacheDir, IMPORT_THUMBNAIL_CACHE_DIR_NAME).apply { mkdirs() }
        val file = File(dir, "$id.jpg")
        val scaled = scaleDownIfNeeded(bitmap, maxDimensionPx = MAX_THUMBNAIL_DIMENSION_PX)
        FileOutputStream(file).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, out) }
        Uri.fromFile(file).toString()
    }.getOrNull()

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= maxDimensionPx) return bitmap
        val scale = maxDimensionPx.toFloat() / largestSide
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }

    private companion object {
        const val MAX_THUMBNAIL_DIMENSION_PX = 512
        const val THUMBNAIL_JPEG_QUALITY = 85
    }
}
