package com.elejar.ZentraDL

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZentraDLApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    DownloadChannels.ACTIVE,
                    getString(R.string.notif_channel_active),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    DownloadChannels.COMPLETED,
                    getString(R.string.notif_channel_completed),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    DownloadChannels.FAILED,
                    getString(R.string.notif_channel_failed),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }
}

/** Notification channel IDs. */
object DownloadChannels {
    const val ACTIVE = "active_downloads"
    const val COMPLETED = "completed_downloads"
    const val FAILED = "failed_downloads"
}
