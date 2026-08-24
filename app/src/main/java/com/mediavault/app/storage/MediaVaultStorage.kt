package com.mediavault.app.storage

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaVault-managed, app-private storage for finished downloads. Files here live under the
 * app's own external-files directory — removed automatically on uninstall, not indexed by
 * MediaStore/Gallery (a public app never sees them without the user explicitly sharing or
 * exporting one), and reachable with plain [File] I/O, no storage permission or SAF grant
 * needed. This is now the default destination for every new download — see
 * PROJECT_MASTER.md's private-storage decision. "Export to device"/"Share" (library actions)
 * are the explicit, user-initiated ways a file leaves this directory.
 */
interface MediaVaultStorage {
    /** Creates the directory if needed and returns it. */
    fun mediaDirectory(): File

    /** Bytes free on the volume [mediaDirectory] lives on. */
    fun freeSpaceBytes(): Long
}

@Singleton
class AndroidMediaVaultStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaVaultStorage {

    override fun mediaDirectory(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "media").apply { mkdirs() }
    }

    override fun freeSpaceBytes(): Long = StatFs(mediaDirectory().path).availableBytes
}
