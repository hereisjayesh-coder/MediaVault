package com.mediavault.app.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Thin seam around [DownloadForegroundService.start] so [MediaVaultDownloadEngine]'s queue
 * logic can be unit-tested with a fake implementation instead of needing a real Android
 * [Context] to start a real service on every enqueue.
 */
interface DownloadForegroundServiceStarter {
    fun start()
}

class AndroidDownloadForegroundServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
) : DownloadForegroundServiceStarter {
    override fun start() = DownloadForegroundService.start(context)
}
