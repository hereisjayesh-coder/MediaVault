package com.mediavault.app.library

import android.net.Uri
import com.mediavault.core.common.AppResult
import com.mediavault.core.database.entity.MediaItemEntity

class FakeMediaImportRepository : MediaImportRepository {
    var nextFileResult: AppResult<MediaItemEntity>? = null
    var nextFolderResult: AppResult<FolderImportResult>? = null
    val importedFileUris = mutableListOf<Uri>()
    val importedFolderUris = mutableListOf<Uri>()

    override suspend fun importFile(uri: Uri): AppResult<MediaItemEntity> {
        importedFileUris.add(uri)
        return nextFileResult ?: error("nextFileResult not set")
    }

    override suspend fun importFolder(treeUri: Uri): AppResult<FolderImportResult> {
        importedFolderUris.add(treeUri)
        return nextFolderResult ?: error("nextFolderResult not set")
    }
}
