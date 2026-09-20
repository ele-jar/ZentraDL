package com.elejar.ZentraDL.data

import com.elejar.ZentraDL.data.local.TaskDao
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.engine.model.DownloadProgress
import com.elejar.ZentraDL.engine.model.DownloadSpec
import com.elejar.ZentraDL.engine.model.Downloader
import com.elejar.ZentraDL.engine.model.ResourceInfo
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test

private class FakeDao : TaskDao {
    val records = mutableMapOf<String, TaskRecord>()
    private val flow = MutableStateFlow(emptyList<TaskRecord>())
    override fun observeAll(): Flow<List<TaskRecord>> = flow
    override fun observe(id: String): Flow<TaskRecord?> = flow.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun allOnce(): List<TaskRecord> = records.values.toList()
    override suspend fun get(id: String): TaskRecord? = records[id]
    override suspend fun insert(task: TaskRecord) {
        records[task.id] = task
        emit()
    }
    override suspend fun delete(id: String) {
        records.remove(id)
        emit()
    }
    override suspend fun updateStatus(id: String, status: String) {
        records[id] = records[id]!!.copy(status = status)
        emit()
    }
    override suspend fun updateError(id: String, error: String?) {
        records[id] = records[id]!!.copy(error = error)
        emit()
    }
    override suspend fun updateMeta(id: String, name: String, total: Long, dest: String) {
        records[id] = records[id]!!.copy(fileName = name, totalBytes = total, destPath = dest)
        emit()
    }
    override suspend fun updateCategory(id: String, categoryId: String) {
        records[id] = records[id]!!.copy(categoryId = categoryId)
        emit()
    }
    override suspend fun clearCategory(categoryId: String) {
        records.replaceAll { (_, r) -> if (r.categoryId == categoryId) r.copy(categoryId = "other") else r }
        emit()
    }
    override suspend fun updateVaulted(id: String, vaulted: Boolean) {
        records[id] = records[id]!!.copy(vaulted = vaulted)
        emit()
    }
    override suspend fun updateExpectedSha(id: String, sha256: String?) {
        records[id] = records[id]!!.copy(expectedSha256 = sha256)
        emit()
    }
    private fun emit() {
        flow.value = records.values.sortedByDescending { it.createdAt }
    }
}

private class FakeDownloader(private val info: ResourceInfo, private val hang: Boolean = false) : Downloader {
    var lastSpec: DownloadSpec? = null
    override suspend fun probe(url: String, headers: Map<String, String>): ResourceInfo = info
    override fun download(spec: DownloadSpec): Flow<DownloadProgress> = flow {
        lastSpec = spec
        if (hang) awaitCancellation()
        emit(DownloadProgress(50, 100, 10))
        emit(DownloadProgress(100, 100, 10))
    }
}

private class FailingDownloader : Downloader {
    override suspend fun probe(url: String, headers: Map<String, String>): ResourceInfo =
        throw IOException("nope")
    override fun download(spec: DownloadSpec): Flow<DownloadProgress> = flow { }
}

class TaskRepositoryTest {

    private fun repo(
        downloader: Downloader,
        dao: FakeDao = FakeDao(),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        maxRunning: Flow<Int> = flowOf(4),
    ): TaskRepository {
        val dir = java.nio.file.Files.createTempDirectory("repo-test").toFile()
        return TaskRepository(dao, downloader, flowOf(4), maxRunning, scope, dir)
    }

    @Test fun run_completesAndUpdatesRecord(): Unit = runBlocking {
        val dao = FakeDao()
        val fake = FakeDownloader(ResourceInfo("https://x/f.bin", "f.bin", 100, "application/octet-stream", true))
        val r = repo(fake, dao)
        val id = r.enqueue("https://x/f.bin")
        assertThat(dao.get(id)!!.status).isEqualTo("queued")
        r.run(id)
        val rec = dao.get(id)!!
        assertThat(rec.status).isEqualTo("completed")
        assertThat(rec.fileName).isEqualTo("f.bin")
        assertThat(rec.totalBytes).isEqualTo(100L)
        assertThat(fake.lastSpec!!.connections).isEqualTo(4)
        assertThat(r.progress.value[id]).isNull()
    }

    @Test fun run_failureMarksFailed(): Unit = runBlocking {
        val dao = FakeDao()
        val r = repo(FailingDownloader(), dao)
        val id = r.enqueue("https://x/f.bin")
        r.run(id)
        assertThat(dao.get(id)!!.status).isEqualTo("failed")
    }

    @Test fun cancel_marksPaused(): Unit = runBlocking {        val dao = FakeDao()
        // Unconfined: the download body runs deterministically on this thread up to
        // the hanging collect; no pool thread can starve or reorder the sequence.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val hanging = FakeDownloader(
            info = ResourceInfo("https://x/f", "f", 100, null, true),
            hang = true,
        )
        val r = repo(hanging, dao, scope)
        val id = r.enqueue("https://x/f")
        val job = launch { r.run(id) }
        yield()
        yield()
        assertThat(dao.get(id)!!.status).isEqualTo("downloading")
        r.cancel(id)
        job.join()
        assertThat(dao.get(id)!!.status).isEqualTo("paused")
        scope.cancel()
    }

    @Test fun queue_secondTaskWaitsForSlot(): Unit = runBlocking {
        val dao = FakeDao()
        val errors = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, e -> synchronized(errors) { errors += e } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
        val hanging = FakeDownloader(
            info = ResourceInfo("https://x/f", "f", 100, null, true),
            hang = true,
        )
        val r = repo(hanging, dao, scope, maxRunning = flowOf(1))
        val a = r.enqueue("https://x/a")
        val b = r.enqueue("https://x/b")
        val jobA = launch(handler) { r.run(a) }
        awaitStatus(dao, a, "downloading")
        val jobB = launch(handler) { r.run(b) }
        delay(500)
        assertThat(dao.get(a)!!.status).isEqualTo("downloading")
        assertThat(dao.get(b)!!.status).isEqualTo("queued")
        r.cancel(a)
        jobA.join()
        assertThat(dao.get(a)!!.status).isEqualTo("paused")
        awaitStatus(dao, b, "downloading")
        r.cancel(b)
        jobB.join()
        assertThat(dao.get(b)!!.status).isEqualTo("paused")
        assertThat(errors).isEmpty()
        scope.cancel()
    }

    private suspend fun awaitStatus(dao: FakeDao, id: String, want: String, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (dao.get(id)?.status != want && System.currentTimeMillis() < deadline) {
            delay(100)
        }
    }

    @Test fun delete_removesRecordAndFile(): Unit = runBlocking {
        val dao = FakeDao()
        val r = repo(FakeDownloader(ResourceInfo("https://x/f", "f", 1, null, false)), dao)
        val id = r.enqueue("https://x/f")
        val rec = dao.get(id)!!
        val f = File(rec.destPath, rec.fileName).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        assertThat(f.exists()).isTrue()
        r.delete(id, deleteFile = true)
        assertThat(dao.get(id)).isNull()
        assertThat(f.exists()).isFalse()
    }
}
