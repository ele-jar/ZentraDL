package com.elejar.ZentraDL.data

import com.elejar.ZentraDL.data.local.TaskDao
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.engine.model.DownloadProgress
import com.elejar.ZentraDL.engine.model.DownloadSpec
import com.elejar.ZentraDL.engine.model.Downloader
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App-level task coordinator (P1 skeleton).
 *
 * Owns: metadata (Room), one live progress snapshot per running task
 * (in-memory StateFlow — progress is NEVER persisted, per ARCHITECTURE.md).
 * Transfer bytes belong to :engine.
 */
@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val downloader: Downloader,
    private val connections: Flow<Int>,
    private val appScope: CoroutineScope,
    private val defaultDir: File,
) {
    val records: Flow<List<TaskRecord>> = dao.observeAll()

    private val _progress = MutableStateFlow(mapOf<String, DownloadProgress>())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    suspend fun get(id: String): TaskRecord? = dao.get(id)

    fun hasRunning(): Boolean = jobs.isNotEmpty()

    suspend fun enqueue(url: String): String {
        val id = UUID.randomUUID().toString()
        val guess = url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
        dao.insert(
            TaskRecord(
                id = id, url = url, fileName = guess,
                destPath = defaultDir.absolutePath, status = "queued",
                totalBytes = -1, createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    /** Starts (or attaches to) the single execution of [id]; suspends until it ends. */
    suspend fun run(id: String) {
        jobs.getOrPut(id) { appScope.launch { runInternal(id) } }.join()
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    private suspend fun runInternal(id: String) {
        val rec = dao.get(id) ?: return
        try {
            dao.updateStatus(id, "downloading")
            val conns = connections.first()
            // Probe for accurate name/size (download() probes again internally; P2 dedups).
            val info = downloader.probe(rec.url)
            val destDir = File(rec.destPath).apply { mkdirs() }
            dao.updateMeta(id, info.fileName, info.totalBytes, destDir.absolutePath)
            val spec = DownloadSpec(rec.url, File(destDir, info.fileName), connections = conns)
            downloader.download(spec).collect { p ->
                _progress.update { it + (id to p) }
            }
            dao.updateStatus(id, "completed")
        } catch (e: CancellationException) {
            dao.updateStatus(id, "paused")
            throw e
        } catch (e: Exception) {
            dao.updateStatus(id, "failed")
        } finally {
            jobs.remove(id)
            _progress.update { it - id }
        }
    }
}
