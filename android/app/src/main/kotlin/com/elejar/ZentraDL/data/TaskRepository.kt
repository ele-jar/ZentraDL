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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val maxRunning: Flow<Int>,
    private val appScope: CoroutineScope,
    private val defaultDir: File,
) {
    val records: Flow<List<TaskRecord>> = dao.observeAll()

    private val _progress = MutableStateFlow(mapOf<String, DownloadProgress>())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val queueMutex = Mutex()
    private val waiters = ArrayDeque<Pair<String, CompletableDeferred<Unit>>>()

    suspend fun get(id: String): TaskRecord? = dao.get(id)

    suspend fun probe(url: String) = downloader.probe(url, emptyMap())

    suspend fun restore(record: TaskRecord) {
        dao.insert(record)
    }

    /** Pause one task: running -> paused via cancel; waiting -> paused explicitly. */
    suspend fun pause(id: String) {
        cancel(id)
        if (dao.get(id)?.status == "queued") dao.updateStatus(id, "paused")
    }

    fun hasRunning(): Boolean = jobs.isNotEmpty()

    suspend fun enqueue(url: String, name: String? = null): String {
        val id = UUID.randomUUID().toString()
        val guess = name?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
        dao.insert(
            TaskRecord(
                id = id, url = url, fileName = guess,
                destPath = defaultDir.absolutePath, status = "queued",
                totalBytes = -1, createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    /** Starts (or joins) the single execution of [id]; waits for a queue slot first. */
    suspend fun run(id: String) {
        queueMutex.withLock { jobs[id] }?.join()?.let { return }
        // Every start goes through the gate: pump() admits atomically, so even
        // a burst of concurrent run() calls (resumeAll) can never overshoot maxRunning.
        val gate = queueMutex.withLock {
            val d = CompletableDeferred<Unit>()
            waiters.addLast(id to d)
            if (dao.get(id)?.status != "downloading") dao.updateStatus(id, "queued")
            d
        }
        pump()
        try {
            gate.await()
        } catch (e: CancellationException) {
            queueMutex.withLock { waiters.removeIf { it.first == id } }
            pump()
            throw e
        }
        jobs.getOrPut(id) { appScope.launch { runInternal(id) } }.join()
    }

    /** Cancel a running task (-> paused) or drop a queued waiter (-> stays queued). */
    suspend fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        queueMutex.withLock {
            val i = waiters.indexOfFirst { it.first == id }
            if (i >= 0) {
                waiters.removeAt(i).second.completeExceptionally(CancellationException("cancelled while queued"))
            }
        }
        pump()
    }

    suspend fun cancelAll() {
        jobs.keys.toList().forEach { jobs.remove(it)?.cancel() }
        queueMutex.withLock {
            waiters.removeAll { (_, gate) ->
                gate.completeExceptionally(CancellationException("cancelled while queued"))
                true
            }
        }
        pump()
    }

    /** Delete record (and file when [deleteFile]). Queued waiters are released first. */
    suspend fun delete(id: String, deleteFile: Boolean) {
        cancel(id)
        queueMutex.withLock { waiters.removeIf { it.first == id } }
        val rec = dao.get(id)
        dao.delete(id)
        if (deleteFile && rec != null) {
            File(rec.destPath, rec.fileName).takeIf { it.exists() }?.delete()
        }
        pump()
    }

    /** Pause everything: waiting tasks are dropped back to paused, running ones cancel. */
    suspend fun pauseAll() {
        val waiting = queueMutex.withLock {
            val ids = waiters.map { it.first }
            waiters.removeAll { (_, gate) ->
                gate.completeExceptionally(CancellationException("paused"))
                true
            }
            ids
        }
        waiting.forEach { dao.updateStatus(it, "paused") }
        jobs.keys.toList().forEach { cancel(it) }
    }

    /** Re-run everything resumable (paused, queued, failed) through the queue. */
    suspend fun resumeAll() {
        val ids = dao.allOnce()
            .filter { it.status == "paused" || it.status == "queued" || it.status == "failed" }
            .map { it.id }
        ids.forEach { id -> appScope.launch { run(id) } }
    }

    /** Release waiting tasks into free slots. */
    private suspend fun pump() {
        val max = maxRunning.first()
        queueMutex.withLock {
            while (jobs.size < max && waiters.isNotEmpty()) {
                val (_, gate) = waiters.removeFirst()
                gate.complete(Unit)
            }
        }
    }

    private suspend fun runInternal(id: String) {
        val rec = dao.get(id) ?: return
        try {
            dao.updateStatus(id, "downloading")
            val conns = connections.first()
            // Probe for accurate name/size (download() probes again internally; P2 dedups).
            val info = downloader.probe(rec.url, emptyMap())
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
            pump()
        }
    }
}
