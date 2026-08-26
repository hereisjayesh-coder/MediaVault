package com.mediavault.app.library

import android.net.Uri
import com.mediavault.core.common.AppResult
import com.mediavault.core.database.entity.MediaItemEntity

/**
 * Indexes media the user explicitly picked via the system document picker (`ACTION_OPEN_DOCUMENT`
 * / `ACTION_OPEN_DOCUMENT_TREE`) into the Library — never anything else. There is deliberately no
 * "scan this whole device/gallery" method here: every entry point takes a [Uri] the OS itself
 * already mediated user consent for, and a folder import only ever looks at that one folder's own
 * direct children, never subfolders or anywhere else. Persists a read [Uri] reference and probed
 * metadata only — the underlying bytes are never copied into MediaVault's own storage.
 */
interface MediaImportRepository {

    /** Indexes a single file the user picked via `ACTION_OPEN_DOCUMENT`. */
    suspend fun importFile(uri: Uri): AppResult<MediaItemEntity>

    /** Indexes every supported media file directly inside the folder the user picked via `ACTION_OPEN_DOCUMENT_TREE` — not its subfolders. */
    suspend fun importFolder(treeUri: Uri): AppResult<FolderImportResult>
}

/** [skippedCount] covers both non-media files (a photo, a document, ...) and media files that couldn't be read — the folder import is best-effort, one bad file never fails the whole batch. */
data class FolderImportResult(
    val imported: List<MediaItemEntity>,
    val skippedCount: Int,
)

/** Human-readable summary for the Library screen's info toast — never invents a number, just reports what happened. */
fun FolderImportResult.summaryMessage(): String = when {
    imported.isEmpty() && skippedCount == 0 -> "That folder has no media files."
    imported.isEmpty() -> "No supported media found ($skippedCount file(s) skipped)."
    skippedCount == 0 -> "Imported ${imported.size} item(s)."
    else -> "Imported ${imported.size} item(s), skipped $skippedCount unsupported file(s)."
}

/** Shared by [AndroidMediaImportRepository] (writes) and [AndroidLibraryRepository] (deletes on "remove from Library") so the two can never disagree about where generated thumbnails live. */
internal const val IMPORT_THUMBNAIL_CACHE_DIR_NAME = "import_thumbnails"
