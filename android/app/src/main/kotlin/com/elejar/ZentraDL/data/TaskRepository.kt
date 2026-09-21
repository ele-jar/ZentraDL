package com.elejar.ZentraDL.data

import com.elejar.ZentraDL.data.local.Category
import com.elejar.ZentraDL.data.local.CategoryDao
import com.elejar.ZentraDL.data.local.TaskDao
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.domain.Categorizer
import com.elejar.ZentraDL.domain.GateBlock
import com.elejar.ZentraDL.domain.GatePolicy
import com.elejar.ZentraDL.domain.GatePolicyCheck
import com.elejar.ZentraDL.domain.NetState
import com.elejar.ZentraDL.engine.http.HlsDownloader
import com.elejar.ZentraDL.engine.http.HlsSpec
import com.elejar.ZentraDL.engine.model.DownloadProgress
import com.elejar.ZentraDL.engine.model.DownloadSpec
import com.elejar.ZentraDL.engine.model.Downloader
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * App-level task coordinator (P1 skeleton).
 *
 * Owns: metadata (Room), one live progress snapshot per running task
 * (in-memory StateFlow — progress is NEVER persisted, per ARCHITECTURE.md).
 * Transfer bytes belong to :engine.
 */@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val downloader: Downloader,
    private val connections: Flow<Int>,
    private val maxRunning: Flow<Int>,
    private val appScope: CoroutineScope,
    val defaultDir: File,
    private val categoryDao: CategoryDao? = null,
    policy: Flow<GatePolicy> = flowOf(GatePolicy()),
    netStates: Flow<NetState> = flowOf(NetState(connected = true, unmetered = true, charging = false)),
    private val hls: HlsDownloader = HlsDownloader(),
) {
    val records: Flow<List<TaskRecord>> = dao.observeAll()
    val categories: Flow<List<Category>> = categoryDao?.observeAll() ?: flowOf(emptyList())

    /** Current queue-gate hold (null = clear). Drives the "Waiting for…" banner. */
    private val _gateBlock = MutableStateFlow<GateBlock?>(null)
    val gateBlock: StateFlow<GateBlock?> = _gateBlock.asStateFlow()

    fun observe(id: String): Flow<TaskRecord?> = dao.observe(id)

    private val _progress = MutableStateFlow(mapOf<String, DownloadProgress>())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val queueMutex = Mutex()
    private val waiters = ArrayDeque<Pair<String, CompletableDeferred<Unit>>>()

    init {
        // AFTER all fields: init publishes this via appScope.launch, and a pool
        // worker (or Unconfined) may run the body before the constructor returns.
        // Seed built-ins once; IGNORE keeps user edits. Null in unit tests.
        appScope.launch { categoryDao?.ensureDefaults() }
        // Re-evaluate the gate on every policy/device change; pump when cleared.
        appScope.launch {
            combine(policy, netStates) { p, n -> GatePolicyCheck.check(p, n, nowMinuteOfDay()) }
                .collect { block ->
                    _gateBlock.value = block
                    if (block == null) pump()
                }
        }
    }
    suspend fun get(id: String): TaskRecord? = dao.get(id)

    suspend fun allUrls(): List<String> = dao.allOnce().map { it.url }

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

    suspend fun enqueue(
        url: String,
        name: String? = null,
        categoryId: String? = null,
        allowDuplicate: Boolean = false,
        kind: String = "http",
        extra: String = "",
    ): String {
        val clean = url.trim()
        if (!allowDuplicate) {
            dao.findByUrl(clean)?.let { throw DuplicateTask(it) }
        }
        val id = UUID.randomUUID().toString()
        val guess = name?.takeIf { it.isNotBlank() }
            ?: clean.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
        val cat = categoryId ?: Categorizer.categorize(guess, null, clean).categoryId
        val folder = categoryDao?.get(cat)?.folder ?: "Other"
        dao.insert(
            TaskRecord(
                id = id, url = clean, fileName = guess,
                destPath = File(defaultDir, folder).absolutePath, status = "queued",
                totalBytes = -1, createdAt = System.currentTimeMillis(),
                categoryId = cat, kind = kind, extra = extra,
            ),
        )
        return id
    }

    suspend fun setCategory(id: String, categoryId: String) {
        val rec = dao.get(id) ?: return
        if (rec.status == "completed") {
            // Keep the file with its category folder when already downloaded.
            val folder = categoryDao?.get(categoryId)?.folder
            if (folder != null) {
                val src = File(rec.destPath, rec.fileName)
                val destDir = File(defaultDir, folder).apply { mkdirs() }
                val dest = File(destDir, rec.fileName)
                if (src.exists() && src.absolutePath != dest.absolutePath) src.renameTo(dest)
                dao.updateMeta(id, rec.fileName, rec.totalBytes, destDir.absolutePath)
            }
        }
        dao.updateCategory(id, categoryId)
    }

    suspend fun addCategory(name: String): String {
        val clean = name.trim().take(32).ifBlank { return "other" }
        val id = clean.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "custom" }
        val dao = categoryDao ?: return "other"
        if (dao.get(id) == null) {
            dao.insert(Category(id, clean, clean, System.currentTimeMillis()))
        }
        return id
    }

    suspend fun deleteCategory(id: String) {
        if (id == "other") return
        dao.clearCategory(id)
        categoryDao?.delete(id)
    }

    /** Rename a task (and its file when already downloaded). False = invalid/blocked. */
    /** HLS run: no probe (playlist is not the media); bandwidth comes from [TaskRecord.extra]. */
    private suspend fun runHls(id: String, rec: TaskRecord) {
        try {
            dao.updateStatus(id, "downloading")
            dao.updateError(id, null)
            val bw = rec.extra.substringAfter("bw=", "").substringBefore(';').toLongOrNull() ?: 0L
            val destDir = File(rec.destPath).apply { mkdirs() }
            hls.download(HlsSpec(rec.url, File(destDir, rec.fileName), bw)).collect { p ->
                _progress.update { it + (id to p) }
            }
            dao.updateStatus(id, "completed")
        } catch (e: CancellationException) {
            dao.updateStatus(id, "paused")
            throw e
        } catch (e: Exception) {
            dao.updateStatus(id, "failed")
            dao.updateError(id, e.message)
        } finally {
            jobs.remove(id)
            _progress.update { it - id }
            pump()
        }
    }

    /** Refresh an expired link in place (H5): new URL, re-queued, partial kept for resume. */
    suspend fun refreshUrl(id: String, url: String): Boolean {
        val clean = url.trim()
        if (clean.isBlank() || jobs.containsKey(id)) return false
        val rec = dao.get(id) ?: return false
        if (rec.kind != "http") return false
        dao.updateUrl(id, clean, "")
        dao.updateMeta(id, rec.fileName, -1, rec.destPath)
        dao.updateError(id, null)
        dao.updateStatus(id, "queued")
        return true
    }

    suspend fun rename(id: String, newName: String): Boolean {
        val clean = newName.trim()
        if (clean.isBlank() || clean.contains('/') || clean.contains('\\') || clean == "." || clean == "..") {
            return false
        }
        val rec = dao.get(id) ?: return false
        if (rec.fileName == clean) return true
        if (rec.status == "completed" && !jobs.containsKey(id)) {
            val src = File(rec.destPath, rec.fileName)
            val dest = File(rec.destPath, clean)
            if (src.exists() && (dest.exists() || !src.renameTo(dest))) return false
        }
        dao.updateMeta(id, clean, rec.totalBytes, rec.destPath)
        return true
    }

    /** SHA-256 of the downloaded file (null when missing). IO-bound; call sparingly. */
    suspend fun sha256Of(id: String): String? = withContext(Dispatchers.IO) {
        val rec = dao.get(id) ?: return@withContext null
        val f = File(rec.destPath, rec.fileName)
        if (!f.exists()) return@withContext null
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun setExpectedSha(id: String, hex: String?) {
        dao.updateExpectedSha(id, hex?.trim()?.lowercase()?.takeIf { it.isNotBlank() })
    }

    /**
     * Move a task's file into/out of the private vault (app-private + .nomedia).
     * Never moves a running transfer. False = blocked (running or name clash).
     */
    suspend fun setVaulted(id: String, vaulted: Boolean): Boolean {
        val rec = dao.get(id) ?: return false
        if (rec.vaulted == vaulted) return true
        if (jobs.containsKey(id)) return false
        val vault = File(defaultDir.parentFile, "vault").apply { mkdirs() }
        File(vault, ".nomedia").takeIf { !it.exists() }?.createNewFile()
        val folder = categoryDao?.get(rec.categoryId)?.folder ?: "Other"
        val destDir = (if (vaulted) vault else File(defaultDir, folder)).apply { mkdirs() }
        val src = File(rec.destPath, rec.fileName)
        val dest = File(destDir, rec.fileName)
        if (src.exists() && src.absolutePath != dest.absolutePath) {
            if (dest.exists() || !src.renameTo(dest)) return false
        }
        dao.updateMeta(id, rec.fileName, rec.totalBytes, destDir.absolutePath)
        dao.updateVaulted(id, vaulted)
        return true
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

    /** Release waiting tasks into free slots (never while the gate is held). */
    private suspend fun pump() {
        if (_gateBlock.value != null) return
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
        // Admitted while clear, blocked since: park back in queue (banner explains).
        if (_gateBlock.value != null) {
            dao.updateStatus(id, "queued")
            return
        }
        if (rec.kind == "hls") {
            runHls(id, rec)
            return
        }
        try {
            dao.updateStatus(id, "downloading")
            dao.updateError(id, null)
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
            dao.updateError(id, e.message)
        } finally {
            jobs.remove(id)
            _progress.update { it - id }
            pump()
        }
    }
}

/** Thrown by [TaskRepository.enqueue] when the URL is already in the list. */
class DuplicateTask(val record: TaskRecord) : Exception("already in list")

/** Local minute-of-day for the schedule window. */
fun nowMinuteOfDay(): Int {
    val cal = java.util.Calendar.getInstance()
    return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
}
