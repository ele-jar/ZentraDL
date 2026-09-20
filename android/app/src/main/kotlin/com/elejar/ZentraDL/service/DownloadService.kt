package com.elejar.ZentraDL.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.elejar.ZentraDL.DownloadChannels
import com.elejar.ZentraDL.R
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.engine.model.DownloadProgress
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that exists ONLY while a download runs (P1 skeleton:
 * single shared notification; per-task notifications land in Phase 2).
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var repo: TaskRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastShownAt = 0L
    private var lastPct = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RUN -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY
                startFg(notificationFor(null, "Preparing…"))
                scope.launch {
                    val rec = repo.get(id)
                    val watcher = launch { watchProgress(id, rec?.fileName ?: "Download") }
                    try {
                        repo.run(id)
                    } finally {
                        watcher.cancel()
                    }
                    if (!repo.hasRunning()) stopSelf()
                    else startFg(notificationFor(null, "Finishing…"))
                }
            }
            ACTION_STOP -> {
                repo.cancelAll()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun watchProgress(id: String, title: String) {
        repo.progress.collect { map ->
            map[id]?.let { updateForeground(title, it) }
        }
    }

    private fun updateForeground(title: String, p: DownloadProgress) {
        val now = System.currentTimeMillis()
        if (p.percent == lastPct || now - lastShownAt < 1000) return
        lastPct = p.percent
        lastShownAt = now
        startFg(notificationFor(p, title))
    }

    private fun notificationFor(p: DownloadProgress?, title: String): Notification {
        val stop = PendingIntent.getService(
            this, 0, Intent(this, DownloadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, DownloadChannels.ACTIVE)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stop)
        if (p != null && p.totalBytes > 0) {
            builder.setContentText("${p.percent}% · ${formatBytes(p.downloadedBytes)} of ${formatBytes(p.totalBytes)}")
                .setProgress(100, p.percent, false)
        } else {
            builder.setContentText(getString(R.string.starting))
                .setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun startFg(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ServiceCompat.startForeground(this, NOTIF_ID, n)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RUN = "com.elejar.ZentraDL.action.RUN"
        const val ACTION_STOP = "com.elejar.ZentraDL.action.STOP"
        private const val EXTRA_ID = "task_id"
        private const val NOTIF_ID = 1

        fun start(ctx: Context, taskId: String) {
            val i = Intent(ctx, DownloadService::class.java).setAction(ACTION_RUN).putExtra(EXTRA_ID, taskId)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }
    }
}

private fun formatBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
