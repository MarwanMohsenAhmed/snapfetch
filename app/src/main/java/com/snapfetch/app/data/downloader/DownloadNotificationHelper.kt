package com.snapfetch.app.data.downloader

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.snapfetch.app.MainActivity

class DownloadNotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_PROGRESS = "snapfetch_download_progress"
        const val CHANNEL_COMPLETE = "snapfetch_download_complete"
        const val CHANNEL_PLAYBACK = "snapfetch_media_playback"
    }

    fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS,
            "Downloads & Compression",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active download and video compression progress"
            setShowBadge(false)
        }
        val completeChannel = NotificationChannel(
            CHANNEL_COMPLETE,
            "Completed Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when downloads finish"
        }
        val playbackChannel = NotificationChannel(
            CHANNEL_PLAYBACK,
            "Background Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Media player notification controls"
            setShowBadge(false)
        }
        manager.createNotificationChannels(listOf(progressChannel, completeChannel, playbackChannel))
    }

    fun buildProgressForegroundInfo(
        notificationId: Int,
        title: String,
        subtitle: String,
        progressPercent: Int,
        indeterminate: Boolean = false
    ): ForegroundInfo {
        val notification = buildProgressNotification(title, subtitle, progressPercent, indeterminate)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    fun buildProgressNotification(
        title: String,
        subtitle: String,
        progressPercent: Int,
        indeterminate: Boolean
    ): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent.coerceIn(0, 100), indeterminate)
            .setContentIntent(pending)
            .build()
    }

    fun notifyComplete(id: Int, title: String, subtitle: String, enabled: Boolean) {
        if (!enabled || !canPostNotifications()) return
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }
}
