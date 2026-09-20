package com.elejar.ZentraDL.data

import com.elejar.ZentraDL.data.local.Category
import com.elejar.ZentraDL.data.local.CategoryDao
import com.elejar.ZentraDL.data.local.TaskDao
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.data.local.TorrentTask
import com.elejar.ZentraDL.data.local.TorrentTaskDao
import com.elejar.ZentraDL.engine.torrent.BtEngine
import com.elejar.ZentraDL.engine.torrent.BtOptions
import com.elejar.ZentraDL.engine.torrent.TorrentDownloadSpec
import com.elejar.ZentraDL.engine.torrent.TorrentRunState
import com.google.common.truth.Truth.assertThat
import bt.torrent.maker.TorrentBuilder
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

private class FakeTaskDao : TaskDao {
    val records = mutableMapOf<String, TaskRecord>()
    private val flow = MutableStateFlow(emptyList<TaskRecord>())
    override fun observeAll(): Flow<List<TaskRecord>> = flow
    override fun observe(id: String): Flow<TaskRecord?> = flow.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun allOnce(): List<TaskRecord> = records.values.toList()
    override suspend fun get(id: String): TaskRecord? = records[id]
    override suspend fun findByUrl(url: String): TaskRecord? = records.values.firstOrNull { it.url == url }
    override suspend fun insert(task: TaskRecord) {
        records[task.id] = task
        flow.value = records.values.toList()
    }
    override suspend fun delete(id: String) {
        records.remove(id)
        flow.value = records.values.toList()
    }
    override suspend fun updateStatus(id: String, status: String) {
        records[id] = records[id]!!.copy(status = status)
        flow.value = records.values.toList()
    }
    override suspend fun updateError(id: String, error: String?) {
        records[id] = records[id]!!.copy(error = error)
    }
    override suspend fun updateMeta(id: String, name: String, total: Long, dest: String) {
        records[id] = records[id]!!.copy(fileName = name, totalBytes = total, destPath = dest)
    }
    override suspend fun updateCategory(id: String, categoryId: String) {
        records[id] = records[id]!!.copy(categoryId = categoryId)
    }
    override suspend fun clearCategory(categoryId: String) = Unit
    override suspend fun updateVaulted(id: String, vaulted: Boolean) = Unit
    override suspend fun updateExpectedSha(id: String, sha256: String?) = Unit
}

private class FakeTorrentDao : TorrentTaskDao {
    val rows = mutableMapOf<String, TorrentTask>()
    override suspend fun get(id: String): TorrentTask? = rows[id]
    override fun observe(id: String): Flow<TorrentTask?> = flowOf(rows[id])
    override suspend fun insert(task: TorrentTask) {
        rows[task.id] = task
    }
    override suspend fun updateSelection(id: String, paths: String) {
        rows[id] = rows[id]!!.copy(selectedPaths = paths)
    }
    override suspend fun delete(id: String) {
        rows.remove(id)
    }
}

private class FakeCategoryDao : CategoryDao {
    override fun observeAll(): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun allOnce(): List<Category> = emptyList()
    override suspend fun get(id: String): Category? = null
    override suspend fun insert(category: Category) = Unit
    override suspend fun delete(id: String) = Unit
}

/** TorrentRepository over a loopback seeder (plain JVM — filesDir is a temp dir). */
class TorrentRepositoryTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test fun addMagnet_downloads_pauses_resumes(): Unit = runBlocking {
        val filesDir = Files.createTempDirectory("trepo").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // Seed data + torrent (maker, 16 KiB pieces).
            val root = Files.createTempDirectory("tswarm").toFile()
            val data = File(root, "data").apply { mkdirs() }
            File(data, "f.bin").writeBytes(ByteArray(30_000) { (it % 251).toByte() })
            val bytes = TorrentBuilder().rootPath(data.toPath())
                .addFile(File(data, "f.bin").toPath()).pieceSize(1 shl 14).build()

            val seedPort = freePort()
            val seeder = BtEngine(BtOptions(acceptorPort = seedPort, bindHost = "127.0.0.1", enableDht = false))
            seeder.start()
            val seedSession = seeder.download(TorrentDownloadSpec(null, bytes, root))
            // Seeder must verify + seed before anyone leeches (isolates seeder-side stalls).
            withTimeout(60_000) {
                while (true) {
                    val s = seedSession.stats.value
                    if (s.state == TorrentRunState.SEEDING) break
                    if (s.state == TorrentRunState.FAILED) {
                        throw AssertionError("seeder failed: ${seedSession.error.value}")
                    }
                    delay(200)
                }
            }

            val engine = BtEngine(
                BtOptions(acceptorPort = freePort(), bindHost = "127.0.0.1", enableDht = false),
            )
            val dao = FakeTaskDao()
            val trepo = TorrentRepository(dao, FakeTorrentDao(), FakeCategoryDao(), engine, scope, filesDir)
            val idHex = engine.parseTorrentBytes(bytes).idHex
            val magnet = "magnet:?xt=urn:btih:$idHex&x.pe=127.0.0.1:$seedPort"
            val id = trepo.addMagnet(magnet, null)
            assertThat(id).isEqualTo(idHex)
            assertThat(dao.get(id)!!.kind).isEqualTo("torrent")

            val job = launch { trepo.runTorrent(id) }
            try {
                withTimeout(120_000) {
                    while (dao.get(id)?.status != "seeding") {
                        trepo.errorOf(id)?.let { throw AssertionError("leecher failed: $it") }
                        if (dao.get(id)?.status == "failed") {
                            throw AssertionError("leecher failed: ${dao.get(id)?.error}")
                        }
                        delay(500)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                throw AssertionError(
                    "stuck: status=${dao.get(id)?.status} stats=${trepo.statsOf(id)} " +
                        "peers=${trepo.peersOf(id)} map=${trepo.pieceMapOf(id)}",
                )
            }
            val stats = trepo.statsOf(id)
            assertThat(stats?.state).isEqualTo(TorrentRunState.SEEDING)
            assertThat(stats?.piecesTotal).isEqualTo(2) // 30kB / 16KiB pieces
            assertThat(trepo.pieceMapOf(id)?.complete).isEqualTo(2)
            assertThat(trepo.peersOf(id)).isNotNull()

            trepo.cancelTorrent(id)
            job.join()
            assertThat(dao.get(id)?.status).isEqualTo("paused")

            val job2 = launch { trepo.runTorrent(id) }
            withTimeout(60_000) {
                while (dao.get(id)?.status != "seeding") delay(200)
            }
            trepo.cancelTorrent(id)
            job2.join()

            trepo.deleteTorrent(id, deleteFiles = true)
            assertThat(dao.get(id)).isNull()
            seedSession.stop()
            seeder.shutdown()
            engine.shutdown()
        } finally {
            scope.cancel()
        }
    }
}
