package com.elejar.ZentraDL.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.elejar.ZentraDL.DownloadChannels
import com.elejar.ZentraDL.MainActivity
import com.elejar.ZentraDL.R
import com.elejar.ZentraDL.data.SettingsStore
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.TorrentRepository
import com.elejar.ZentraDL.engine.model.DownloadProgress
import com.elejar.ZentraDL.ui.Format
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that exists ONLY while a download runs.
 *
 * P2c notifications: one progress notification per task, a group summary,
 * completion (Open/Share) and failure (Retry) notifications. Quiet
 * hours / per-category sound land with smart notifications (Phase 6).
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var repo: TaskRepository
    @Inject lateinit var trepo: TorrentRepository
    @Inject lateinit var settings: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notifIds = mutableMapOf<String, Int>()
    private var nextId = 10
    private var fgStarted = false
    private var lastSummary = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RUN -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY
                ensureFg(progressNotification(id, "Preparing…", null))
                scope.launch {
                    val rec = repo.get(id)
                    val watcher = launch { watchProgress(id, rec?.fileName ?: "Download") }
                    try {
                        repo.run(id)
                    } finally {
                        watcher.cancel()
                    }
                    finishTask(id)
                }
            }
            ACTION_RUN_TORRENT -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY
                ensureFg(progressNotification(id, "Torrent", null))
                scope.launch {
                    val name = repo.get(id)?.fileName ?: "Torrent"
                    val watcher = launch { watchTorrent(id, name) }
                    try {
                        trepo.runTorrent(id)
                    } finally {
                        watcher.cancel()
                    }
                    finishTorrent(id)
                }
            }
            ACTION_STOP_TASK -> {
                intent.getStringExtra(EXTRA_ID)?.let { id ->
                    scope.launch {
                        if (repo.get(id)?.kind == "torrent") trepo.cancelTorrent(id) else repo.cancel(id)
                    }
                }
            }
            ACTION_STOP -> {
                scope.launch {
                    withContext(NonCancellable) {
                        repo.cancelAll()
                        trepo.pauseAllTorrents()
                    }
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    /** After a run ends: swap progress notification for a terminal one, stop when idle. */
    private suspend fun finishTask(id: String) {
        cancelNotification(id)
        val rec = repo.get(id)
        when (rec?.status) {
            "completed" -> showCompleted(rec)
            "failed" -> showFailed(id, rec.fileName, rec.error, ACTION_RUN)
            else -> Unit // paused/cancelled: stay quiet
        }
        updateSummary()
        if (!repo.hasRunning() && !trepo.hasActive()) stopSelf()
    }

    /** Torrent runs park while seeding (ongoing notification kept); pause/failed end it. */
    private suspend fun finishTorrent(id: String) {
        val rec = repo.get(id)
        when (rec?.status) {
            "seeding" -> postNotification(id, seedingNotification(id, rec.fileName))
            "failed" -> {
                cancelNotification(id)
                showFailed(id, rec.fileName, rec.error, ACTION_RUN_TORRENT)
            }
            else -> cancelNotification(id) // paused/cancelled: stay quiet
        }
        updateSummary()
        if (!repo.hasRunning() && !trepo.hasActive()) stopSelf()
    }

    private suspend fun watchProgress(id: String, title: String) {
        var lastPct = -1
        var lastAt = 0L
        repo.progress.collect { map ->
            val p = map[id] ?: return@collect
            val now = System.currentTimeMillis()
            if (p.percent == lastPct || now - lastAt < 1000) return@collect
            lastPct = p.percent
            lastAt = now
            postNotification(id, progressNotification(id, title, p))
            updateSummary()
        }
    }

    private suspend fun watchTorrent(id: String, title: String) {
        var lastPct = -1
        var lastAt = 0L
        trepo.tprogress.collect { map ->
            val p = map[id] ?: return@collect
            val now = System.currentTimeMillis()
            if (p.percent == lastPct || now - lastAt < 1000) return@collect
            lastPct = p.percent
            lastAt = now
            postNotification(id, progressNotification(id, title, p))
            updateSummary()
        }
    }

    private fun seedingNotification(id: String, title: String): Notification {
        val stop = serviceIntent(ACTION_STOP_TASK, id, 0)
        val s = trepo.statsOf(id)
        val up = s?.let { com.elejar.ZentraDL.ui.Format.speed(it.upRate, it.upRate > 0) } ?: ""
        return NotificationCompat.Builder(this, DownloadChannels.ACTIVE)
            .setContentTitle(title)
            .setContentText(getString(R.string.seeding_notif, up))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setGroup(GROUP)
            .setContentIntent(contentIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stop)
            .build()
    }

    private fun progressNotification(id: String, title: String, p: DownloadProgress?): Notification {
        val stop = serviceIntent(ACTION_STOP_TASK, id, 0)
        val builder = NotificationCompat.Builder(this, DownloadChannels.ACTIVE)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setGroup(GROUP)
            .setContentIntent(contentIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stop)
        if (p != null && p.totalBytes > 0) {
            builder.setContentText("${p.percent}% · ${Format.bytes(p.downloadedBytes)} of ${Format.bytes(p.totalBytes)}")
                .setProgress(100, p.percent, false)
        } else {
            builder.setContentText(getString(R.string.starting))
                .setProgress(0, 0, true)
        }
        return builder.build()
    }

    private suspend fun showCompleted(rec: com.elejar.ZentraDL.data.local.TaskRecord) {
        // S10-lite: failures-only skips these; hide-tiny skips <1 MB files.
        if (settings.failuresOnly.first()) return
        if (settings.hideTiny.first() && rec.totalBytes in 1..1_048_575) return
        val quiet = quietNow()
        val file = File(rec.destPath, rec.fileName)
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", file)
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val open = PendingIntent.getActivity(
            this, rec.id.hashCode(),
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val share = PendingIntent.getActivity(
            this, -rec.id.hashCode(),
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType(mime)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                rec.fileName,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, DownloadChannels.COMPLETED)
            .setContentTitle(rec.fileName)
            .setContentText(getString(R.string.download_complete))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setSilent(quiet)
            .addAction(android.R.drawable.ic_menu_view, getString(R.string.open_file), open)
            .addAction(android.R.drawable.ic_menu_share, getString(R.string.share), share)
            .build()
        notify(terminalId(), n)
    }

    private suspend fun showFailed(id: String, name: String, error: String?, retryAction: String) {
        val retry = serviceIntent(retryAction, id, id.hashCode())
        val n = NotificationCompat.Builder(this, DownloadChannels.FAILED)
            .setContentTitle(name)
            .setContentText(error ?: getString(R.string.download_failed))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setSilent(quietNow())
            .addAction(android.R.drawable.ic_menu_rotate, getString(R.string.retry), retry)
            .build()
        notify(terminalId(), n)
    }

    /** Quiet-hours check (same overnight-wrap window semantics as the scheduler). */
    private suspend fun quietNow(): Boolean {
        if (!settings.quietEnabled.first()) return false
        val start = settings.quietStartMin.first()
        val end = settings.quietEndMin.first()
        if (start !in 0..<1440 || end !in 0..1440 || start == end) return false
        val now = com.elejar.ZentraDL.data.nowMinuteOfDay()
        return if (start < end) now !in start..<end else !(now >= start || now < end)
    }

    private suspend fun updateSummary() {
        val map = repo.progress.value + trepo.tprogress.value
        val total = map.values.sumOf { it.bytesPerSecond }
        val count = map.size
        val text = if (count == 0) "" else "$count downloading · ${Format.speed(total, total > 0)}"
        if (text == lastSummary) return
        lastSummary = text
        val nm = NotificationManagerCompat.from(this)
        if (text.isEmpty()) {
            try {
                nm.cancel(SUMMARY_ID)
            } catch (e: SecurityException) {
                Unit
            }
            return
        }
        val n = NotificationCompat.Builder(this, DownloadChannels.ACTIVE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setContentIntent(contentIntent())
            .build()
        safeNotify(SUMMARY_ID, n)
    }

    private fun ensureFg(n: Notification) {
        if (!fgStarted) {
            ServiceCompat.startForeground(
                this, FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            fgStarted = true
        }
    }

    private fun postNotification(id: String, n: Notification) {
        val nid = notifIds.getOrPut(id) { nextId++ }
        ensureFg(n)
        safeNotify(nid, n)
    }

    private fun cancelNotification(id: String) {
        notifIds.remove(id)?.let {
            try {
                NotificationManagerCompat.from(this).cancel(it)
            } catch (e: SecurityException) {
                // Notifications disabled: nothing to cancel.
            }
        }
    }

    private fun notify(id: Int, n: Notification) {
        safeNotify(id, n)
    }

    private fun safeNotify(id: Int, n: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(id, n)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied: transfers continue silently.
        }
    }

    private fun serviceIntent(action: String, id: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, DownloadService::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RUN = "com.elejar.ZentraDL.action.RUN"
        const val ACTION_RUN_TORRENT = "com.elejar.ZentraDL.action.RUN_TORRENT"
        const val ACTION_STOP = "com.elejar.ZentraDL.action.STOP"
        const val ACTION_STOP_TASK = "com.elejar.ZentraDL.action.STOP_TASK"
        private const val EXTRA_ID = "task_id"
        private const val GROUP = "zentradl_downloads"
        private const val FG_ID = 1
        private const val SUMMARY_ID = 2
        private var terminalSeq = 1000
        private fun terminalId(): Int = terminalSeq++

        fun start(ctx: Context, taskId: String) {
            val i = Intent(ctx, DownloadService::class.java).setAction(ACTION_RUN).putExtra(EXTRA_ID, taskId)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }

        fun startTorrent(ctx: Context, taskId: String) {
            val i = Intent(ctx, DownloadService::class.java).setAction(ACTION_RUN_TORRENT).putExtra(EXTRA_ID, taskId)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }
    }
}
