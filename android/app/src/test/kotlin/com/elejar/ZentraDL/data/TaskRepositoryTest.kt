package com.elejar.ZentraDL.data

import com.elejar.ZentraDL.data.local.TaskDao
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.engine.model.DownloadProgress
import com.elejar.ZentraDL.engine.model.DownloadSpec
import com.elejar.ZentraDL.engine.model.Downloader
import com.elejar.ZentraDL.engine.model.ResourceInfo
import com.google.common.truth.Truth.assertThat
import java.io.IOException
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

private class FakeDao : TaskDao {
    val records = mutableMapOf<String, TaskRecord>()
    private val flow = MutableStateFlow(emptyList<TaskRecord>())
    override fun observeAll(): Flow<List<TaskRecord>> = flow
    override suspend fun get(id: String): TaskRecord? = records[id]
    override suspend fun insert(task: TaskRecord) {
        records[task.id] = task
        emit()
    }
    override suspend fun updateStatus(id: String, status: String) {
        records[id] = records[id]!!.copy(status = status)
        emit()
    }
    override suspend fun updateMeta(id: String, name: String, total: Long, dest: String) {
        records[id] = records[id]!!.copy(fileName = name, totalBytes = total, destPath = dest)
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
    ): TaskRepository {
        val dir = java.nio.file.Files.createTempDirectory("repo-test").toFile()
        return TaskRepository(dao, downloader, flowOf(4), scope, dir)
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

    @Test fun cancel_marksPaused(): Unit = runBlocking {
        val dao = FakeDao()
        // Unconfined: the download body runs deterministically on this thread up to
        // the hanging collect; no pool thread can starve or reorder the sequence.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val hanging = FakeDownloader(ResourceInfo("https://x/f", "f", 100, null, true))
        val r = repo(hanging, dao, scope)
        val id = r.enqueue("https://x/f")
        val job = launch { r.run(id) }
        // Wait until runInternal provably started (all remaining steps are
        // suspension-free until the hanging collect, so cancel always lands inside try).
        var waited = 0
        while (dao.get(id)!!.status != "downloading" && waited < 100) {
            delay(100)
            waited++
        }
        assertThat(dao.get(id)!!.status).isEqualTo("downloading")
        r.cancel(id)
        job.join()
        assertThat(dao.get(id)!!.status).isEqualTo("paused")
        scope.cancel()
    }
}
