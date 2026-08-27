package com.mediavault.app.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.mediavault.app.security.AppLockSettingsStore
import com.mediavault.core.domain.download.DownloadEngine
import com.mediavault.core.model.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps the process (and any in-flight download) alive while the app is backgrounded, with a
 * single ongoing notification summarizing the queue. Started/stopped by [MediaVaultDownloadEngine]
 * as work starts/finishes — this class has no download logic of its own, it only observes.
 */
@AndroidEntryPoint
class DownloadForegroundService : Service() {

    @Inject
    lateinit var downloadEngine: DownloadEngine

    @Inject
    lateinit var appLockSettingsStore: AppLockSettingsStore

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    override fun onCreate() {
        super.onCreate()
        ensureDownloadChannel(this)
        startForeground(DOWNLOAD_NOTIFICATION_ID, buildDownloadNotification(this, null, null, 0, false))

        combine(downloadEngine.observeAll(), appLockSettingsStore.settings) { tasks, lockSettings -> tasks to lockSettings.appLockEnabled }
            .onEach { (tasks, appLockEnabled) ->
                val active = tasks.firstOrNull { it.status.isActiveTransfer() }
                val queuedCount = tasks.count { it.status == DownloadStatus.QUEUED }
                val stillWorking = active != null || queuedCount > 0

                val percent = active?.totalBytes?.takeIf { it > 0 }?.let { total ->
                    ((active.bytesTransferred * 100) / total).toInt()
                }
                val notification = buildDownloadNotification(
                    context = this,
                    activeTitle = active?.title,
                    progressPercent = percent,
                    queuedCount = queuedCount,
                    isIndeterminate = active != null && percent == null,
                    hideTitleForPrivacy = appLockEnabled,
                )
                ContextCompat.getSystemService(this, android.app.NotificationManager::class.java)
                    ?.notify(DOWNLOAD_NOTIFICATION_ID, notification)

                if (!stillWorking) {
                    stopSelf()
                }
            }
            .launchIn(serviceScope)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadForegroundService::class.java))
        }
    }
}
