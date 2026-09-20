package com.elejar.ZentraDL.data

import com.elejar.ZentraDL.data.local.CategoryDao
import com.elejar.ZentraDL.data.local.TaskDao
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.data.local.TorrentTask
import com.elejar.ZentraDL.data.local.TorrentTaskDao
import com.elejar.ZentraDL.domain.Categorizer
import com.elejar.ZentraDL.engine.model.DownloadProgress
import com.elejar.ZentraDL.engine.torrent.BtEngine
import com.elejar.ZentraDL.engine.torrent.BtSession
import com.elejar.ZentraDL.engine.torrent.FetchedMeta
import com.elejar.ZentraDL.engine.torrent.TorrentDownloadSpec
import com.elejar.ZentraDL.engine.torrent.TorrentMeta
import com.elejar.ZentraDL.engine.torrent.TorrentPeer
import com.elejar.ZentraDL.engine.torrent.TorrentPieceMap
import com.elejar.ZentraDL.engine.torrent.TorrentRunState
import com.elejar.ZentraDL.engine.torrent.TorrentStats
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * App-level torrent coordinator (P4). Metadata in Room ([TorrentTask]),
 * transfers in [BtSession] (one live client each). Finished sessions keep
 * seeding until paused; pause/resume rebuilds (re-verify is automatic).
 *
 * Takes [filesDir] (not Context) so unit tests run on plain JVM, no Robolectric.
 */
class TorrentRepository(
    private val tasks: TaskDao,
    private val torrents: TorrentTaskDao,
    private val categories: CategoryDao,
    private val engine: BtEngine,
    private val appScope: CoroutineScope,
    private val filesDir: File,
) {
    private val sessions = ConcurrentHashMap<String, BtSession>()
    private val metas = ConcurrentHashMap<String, TorrentMeta>()

    private val _tprogress = MutableStateFlow(mapOf<String, DownloadProgress>())
    val tprogress: StateFlow<Map<String, DownloadProgress>> = _tprogress.asStateFlow()

    init {
        // Stuck "downloading" rows from a dead process park as paused.
        appScope.launch {
            tasks.allOnce()
                .filter { it.kind == "torrent" && (it.status == "downloading" || it.status == "seeding") }
                .forEach { tasks.updateStatus(it.id, "paused") }
        }
    }

    private fun torrentsDir(): File = File(filesDir, "torrents").apply { mkdirs() }
    private fun metaFile(id: String): File = File(torrentsDir(), "$id.torrent")

    fun observeTorrent(id: String): Flow<TorrentTask?> = torrents.observe(id)
    suspend fun torrentRow(id: String): TorrentTask? = torrents.get(id)

    suspend fun fetchMeta(magnet: String): FetchedMeta {
        engine.start()
        return engine.fetchMetadata(magnet)
    }

    fun parseFile(bytes: ByteArray): TorrentMeta = engine.parseTorrentBytes(bytes)

    suspend fun meta(id: String): TorrentMeta? {
        metas[id]?.let { return it }
        val row = torrents.get(id) ?: return null
        val bytes = row.torrentPath?.let { File(it).takeIf { f -> f.exists() }?.readBytes() }
            ?: return null
        return engine.parseTorrentBytes(bytes).also { metas[id] = it }
    }

    /** Add by magnet (fetches metadata first, ~60s timeout). Returns info-hash. */
    suspend fun addMagnet(magnet: String, categoryId: String?): String {
        val fetched = fetchMeta(magnet)
        fetched.rawBytes?.let { metaFile(fetched.meta.idHex).writeBytes(it) }
        return insertTorrent(fetched.meta, magnet, categoryId)
    }

    /** Add by .torrent bytes. Returns info-hash. */
    suspend fun addFile(bytes: ByteArray, categoryId: String?): String {
        val meta = engine.parseTorrentBytes(bytes)
        metaFile(meta.idHex).writeBytes(bytes)
        return insertTorrent(meta, null, categoryId)
    }

    private suspend fun insertTorrent(meta: TorrentMeta, magnet: String?, categoryId: String?): String {
        val cat = categoryId ?: Categorizer.categorize(meta.name, null, null).categoryId
        val folder = categories.get(cat)?.folder ?: "Other"
        val destDir = File(filesDir.resolve("downloads"), folder)
        tasks.insert(
            TaskRecord(
                id = meta.idHex, url = magnet ?: "torrent:${meta.idHex}", fileName = meta.name,
                destPath = destDir.absolutePath, status = "queued",
                totalBytes = meta.sizeBytes, createdAt = System.currentTimeMillis(),
                categoryId = cat, kind = "torrent",
            ),
        )
        torrents.insert(
            TorrentTask(
                id = meta.idHex, magnet = magnet,
                torrentPath = metaFile(meta.idHex).takeIf { it.exists() }?.absolutePath,
                name = meta.name, sizeBytes = meta.sizeBytes,
            ),
        )
        metas[meta.idHex] = meta
        return meta.idHex
    }

    /**
     * Attach the session and mirror stats until paused/failed. Seeding parks
     * here (service holds the coroutine = FGS while seeding).
     */
    suspend fun runTorrent(id: String) {
        engine.start()
        val row = torrents.get(id) ?: return
        val rec = tasks.get(id) ?: return
        val meta = meta(id) ?: run {
            tasks.updateStatus(id, "failed")
            tasks.updateError(id, "Missing metadata")
            return
        }
        stopSession(id)
        // The stopped session unregisters async (TorrentStopped event); a new
        // client registered too soon dies with IllegalStateException.
        try {
            withTimeout(30_000) {
                while (engine.hasDescriptor(id)) delay(100)
            }
        } catch (e: TimeoutCancellationException) {
            tasks.updateStatus(id, "failed")
            tasks.updateError(id, "Still shutting down — try again")
            return
        }
        val bytes = row.torrentPath?.let { File(it).takeIf { f -> f.exists() }?.readBytes() }
        val selected = row.selectedPaths.split(",").filter { it.isNotEmpty() }.toSet().ifEmpty { null }
        val session = engine.download(
            TorrentDownloadSpec(row.magnet, bytes, File(rec.destPath), selected, row.sequential, meta.pieceLength),
        )
        // Cancelled while building: park, don't start.
        if (tasks.get(id)?.status == "paused") {
            session.stop()
            return
        }
        sessions[id] = session
        try {
            session.stats.onEach { s ->
                val total = rec.totalBytes.takeIf { it > 0 } ?: meta.sizeBytes
                _tprogress.update { it + (id to DownloadProgress(s.downloadedBytes, total, s.downRate)) }
                tasks.updateStatus(
                    id,
                    when (s.state) {
                        TorrentRunState.SEEDING -> "seeding"
                        TorrentRunState.DOWNLOADING, TorrentRunState.FETCHING -> "downloading"
                        TorrentRunState.PAUSED -> "paused"
                        TorrentRunState.FAILED -> "failed"
                    },
                )
                if (s.state == TorrentRunState.FAILED) tasks.updateError(id, session.error.value)
            }.first { it.state == TorrentRunState.PAUSED || it.state == TorrentRunState.FAILED }
        } finally {
            sessions.remove(id)
            _tprogress.update { it - id }
        }
    }

    suspend fun cancelTorrent(id: String) {
        sessions[id]?.stop()
        if (tasks.get(id)?.status == "queued") tasks.updateStatus(id, "paused")
    }

    fun pauseAllTorrents() {
        sessions.keys.toList().forEach { sessions[it]?.stop() }
    }

    suspend fun deleteTorrent(id: String, deleteFiles: Boolean) {
        sessions.remove(id)?.stop()
        val rec = tasks.get(id)
        torrents.delete(id)
        tasks.delete(id)
        metaFile(id).delete()
        if (deleteFiles && rec != null) {
            File(rec.destPath, rec.fileName).takeIf { it.exists() }?.deleteRecursively()
        }
    }

    suspend fun restoreTorrent(record: TaskRecord, row: TorrentTask) {
        tasks.insert(record)
        torrents.insert(row)
    }

    /** Change file selection (applies on next start; bt can't unskip mid-run). */
    suspend fun updateSelection(id: String, paths: Set<String>): Boolean {
        if (sessions.containsKey(id)) return false
        torrents.updateSelection(id, paths.joinToString(","))
        return true
    }

    fun hasActive(): Boolean = sessions.isNotEmpty()
    fun statsOf(id: String): TorrentStats? = sessions[id]?.stats?.value
    fun errorOf(id: String): String? = sessions[id]?.error?.value
    fun pieceMapOf(id: String): TorrentPieceMap? = sessions[id]?.pieceMap()
    fun peersOf(id: String): List<TorrentPeer> = sessions[id]?.peers().orEmpty()

    suspend fun resumeAllTorrents() {
        val ids = tasks.allOnce()
            .filter { it.kind == "torrent" && (it.status == "paused" || it.status == "queued" || it.status == "failed") }
            .map { it.id }
        ids.forEach { id -> appScope.launch { runTorrent(id) } }
    }

    private fun stopSession(id: String) {
        sessions.remove(id)?.stop()
    }
}
