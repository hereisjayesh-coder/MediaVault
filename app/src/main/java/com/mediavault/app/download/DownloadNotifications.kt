package com.mediavault.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mediavault.app.R
import com.mediavault.core.model.DownloadStatus

private const val CHANNEL_ID = "downloads"
const val DOWNLOAD_NOTIFICATION_ID = 4201

/** One persistent notification summarizing the whole queue — not one per task. */
internal fun ensureDownloadChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.download_notification_channel_name),
        NotificationManager.IMPORTANCE_LOW,
    )
    manager.createNotificationChannel(channel)
}

internal fun buildDownloadNotification(
    context: Context,
    activeTitle: String?,
    progressPercent: Int?,
    queuedCount: Int,
    isIndeterminate: Boolean,
): Notification {
    val contentText = when {
        activeTitle != null -> activeTitle
        queuedCount > 0 -> context.getString(R.string.download_notification_queued, queuedCount)
        else -> context.getString(R.string.download_notification_idle)
    }
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(context.getString(R.string.download_notification_title))
        .setContentText(contentText)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)

    if (activeTitle != null) {
        if (isIndeterminate || progressPercent == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, progressPercent.coerceIn(0, 100), false)
        }
    }

    return builder.build()
}

internal fun DownloadStatus.isActiveTransfer(): Boolean =
    this == DownloadStatus.DOWNLOADING || this == DownloadStatus.PROCESSING
