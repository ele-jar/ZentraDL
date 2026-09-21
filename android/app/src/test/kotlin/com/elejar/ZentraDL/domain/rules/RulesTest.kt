package com.elejar.ZentraDL.domain.rules

import com.elejar.ZentraDL.data.local.Category
import com.elejar.ZentraDL.data.local.CategoryDao
import com.elejar.ZentraDL.data.local.RuleDao
import com.elejar.ZentraDL.data.local.RuleEntity
import com.elejar.ZentraDL.data.local.RuleLogDao
import com.elejar.ZentraDL.data.local.RuleLogEntry
import com.elejar.ZentraDL.data.local.TaskDao
import com.elejar.ZentraDL.data.local.TaskRecord
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Test

private fun rec(id: String, name: String, url: String = "https://x/$name", total: Long = 100) =
    TaskRecord(id, url, name, "/d", "completed", total, 1000L)

private class FakeTasks : TaskDao {
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
    }
    override suspend fun updateError(id: String, error: String?) = Unit
    override suspend fun updateMeta(id: String, name: String, total: Long, dest: String) {
        records[id] = records[id]!!.copy(fileName = name, totalBytes = total, destPath = dest)
    }
    override suspend fun updateCategory(id: String, categoryId: String) {
        records[id] = records[id]!!.copy(categoryId = categoryId)
    }
    override suspend fun clearCategory(categoryId: String) = Unit
    override suspend fun updateVaulted(id: String, vaulted: Boolean) = Unit
    override suspend fun updateExpectedSha(id: String, sha256: String?) = Unit
    override suspend fun updateUrl(id: String, url: String, extra: String) {
        records[id] = records[id]!!.copy(url = url, extra = extra)
    }
}

private class FakeRules : RuleDao {
    val rows = mutableMapOf<String, RuleEntity>()
    override fun observeAll(): Flow<List<RuleEntity>> = flowOf(rows.values.toList())
    override suspend fun allOnce(): List<RuleEntity> = rows.values.toList()
    override suspend fun insert(rule: RuleEntity) {
        rows[rule.id] = rule
    }
    override suspend fun updateEnabled(id: String, enabled: Boolean) {
        rows[id] = rows[id]!!.copy(enabled = enabled)
    }
    override suspend fun delete(id: String) {
        rows.remove(id)
    }
}

private class FakeLog : RuleLogDao {
    val rows = mutableListOf<RuleLogEntry>()
    var seq = 0L
    override suspend fun recent(limit: Int): List<RuleLogEntry> = rows.takeLast(limit).reversed()
    override suspend fun insert(entry: RuleLogEntry) {
        rows += entry.copy(id = ++seq)
    }
    override suspend fun get(id: Long): RuleLogEntry? = rows.firstOrNull { it.id == id }
    override suspend fun delete(id: Long) {
        rows.removeIf { it.id == id }
    }
    override suspend fun trim(keep: Int) {
        while (rows.size > keep) rows.removeFirst()
    }
    override suspend fun clear() {
        rows.clear()
    }
}

private class FakeCats(private val folders: Map<String, String> = mapOf("videos" to "Videos")) : CategoryDao {
    override fun observeAll(): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun allOnce(): List<Category> = emptyList()
    override suspend fun get(id: String): Category? = folders[id]?.let { Category(id, id, it, 0) }
    override suspend fun insert(category: Category) = Unit
    override suspend fun delete(id: String) = Unit
}

class RulesTest {

    @Test fun codec_roundTrip() {
        val conds = listOf(
            RuleCondition.Ext(setOf("mp4", "mkv")),
            RuleCondition.MinSize(100),
            RuleCondition.Host("Example.COM"),
            RuleCondition.NameRe(".*1080p.*"),
        )
        val decoded = conds.map { RuleCodec.decodeCondition(RuleCodec.encodeCondition(it)) }
        assertThat(decoded).containsExactlyElementsIn(conds).inOrder()
        assertThat(RuleCodec.decodeCondition("ext:")).isNull()
        assertThat(RuleCodec.decodeCondition("minSize:abc")).isNull()
        assertThat(RuleCodec.decodeCondition("nope:x")).isNull()
        assertThat(RuleCodec.decodeCondition("nameRe:([")).isNull()
        val move = RuleAction.MoveCategory("videos")
        assertThat(RuleCodec.decodeAction(RuleCodec.encodeAction(move))).isEqualTo(move)
        val ren = RuleAction.Rename("{date} {name}", true)
        assertThat(RuleCodec.decodeAction(RuleCodec.encodeAction(ren))).isEqualTo(ren)
        assertThat(RuleCodec.decodeAction("moveCat:")).isNull()
    }

    @Test fun match_conditions() {
        val r = rec("a", "show.1080p.mkv", "https://cdn.example.com/f", total = 200)
        assertThat(RuleMatch.matchesAll(r, listOf(RuleCondition.Ext(setOf("mkv"))))).isTrue()
        assertThat(RuleMatch.matchesAll(r, listOf(RuleCondition.MinSize(201)))).isFalse()
        assertThat(RuleMatch.matchesAll(r, listOf(RuleCondition.Host("example.com")))).isTrue()
        assertThat(RuleMatch.matchesAll(r, listOf(RuleCondition.Host("other.com")))).isFalse()
        assertThat(RuleMatch.matchesAll(r, listOf(RuleCondition.NameRe(".*1080p.*")))).isTrue()
    }

    private fun engine(tasks: FakeTasks, dir: File, log: FakeLog = FakeLog()): RuleEngine =
        RuleEngine(tasks, FakeRules(), log, FakeCats(), filesDir = dir)

    @Test fun onComplete_movesFile_and_undoRestores(): Unit = runBlocking {
        val dir = Files.createTempDirectory("rules").toFile()
        val tasks = FakeTasks()
        val log = FakeLog()
        val eng = engine(tasks, dir, log)
        val srcDir = File(dir, "downloads/Other").apply { mkdirs() }
        File(srcDir, "m.mkv").writeText("x")
        val rec = rec("a", "m.mkv").copy(destPath = srcDir.absolutePath)
        tasks.insert(rec)
        val rule = Rule("r1", "vids", true, Trigger.ON_COMPLETE, listOf(RuleCondition.Ext(setOf("mkv"))), listOf(RuleAction.MoveCategory("videos")))
        eng.addRule(rule)
        eng.onComplete(rec)
        val moved = tasks.get("a")!!
        assertThat(moved.destPath).isEqualTo(File(dir, "downloads/Videos").absolutePath)
        assertThat(File(moved.destPath, "m.mkv").exists()).isTrue()
        assertThat(log.rows).hasSize(1)
        assertThat(eng.undo(log.rows.first().id)).isTrue()
        val back = tasks.get("a")!!
        assertThat(back.destPath).isEqualTo(srcDir.absolutePath)
        assertThat(File(srcDir, "m.mkv").exists()).isTrue()
    }

    @Test fun disabledOrMismatchOrTorrent_skipped(): Unit = runBlocking {
        val dir = Files.createTempDirectory("rules2").toFile()
        val tasks = FakeTasks()
        val log = FakeLog()
        val eng = engine(tasks, dir, log)
        val rec = rec("a", "m.mkv")
        tasks.insert(rec)
        eng.addRule(Rule("r1", "off", false, Trigger.ON_COMPLETE, emptyList(), listOf(RuleAction.MoveCategory("videos"))))
        eng.addRule(Rule("r2", "nomatch", true, Trigger.ON_COMPLETE, listOf(RuleCondition.Ext(setOf("zip"))), listOf(RuleAction.MoveCategory("videos"))))
        eng.onComplete(rec)
        eng.onComplete(rec.copy(id = "t", kind = "torrent"))
        assertThat(tasks.get("a")!!.categoryId).isEqualTo("other")
        assertThat(log.rows).isEmpty()
    }

    @Test fun dryRun_listsWithoutChanging(): Unit = runBlocking {
        val dir = Files.createTempDirectory("rules3").toFile()
        val tasks = FakeTasks()
        val eng = engine(tasks, dir)
        tasks.insert(rec("a", "m.mkv"))
        val rule = Rule("r1", "vids", true, Trigger.ON_COMPLETE, listOf(RuleCondition.Ext(setOf("mkv"))), listOf(RuleAction.MoveCategory("videos")))
        val preview = eng.dryRun(rule)
        assertThat(preview).containsExactly("m.mkv → Videos/")
        assertThat(tasks.get("a")!!.categoryId).isEqualTo("other")
    }

    @Test fun rename_appliesTemplate(): Unit = runBlocking {
        val dir = Files.createTempDirectory("rules4").toFile()
        val tasks = FakeTasks()
        val eng = engine(tasks, dir)
        val srcDir = File(dir, "downloads/Other").apply { mkdirs() }
        File(srcDir, "Show.Name.S01E02.mkv").writeText("x")
        val rec = rec("a", "Show.Name.S01E02.mkv").copy(destPath = srcDir.absolutePath)
        tasks.insert(rec)
        eng.addRule(
            Rule(
                "r1", "clean", true, Trigger.ON_COMPLETE, emptyList(),
                listOf(RuleAction.Rename("{name}", true)),
            ),
        )
        eng.onComplete(rec)
        assertThat(tasks.get("a")!!.fileName).isEqualTo("Show Name S01E02.mkv")
        assertThat(File(srcDir, "Show Name S01E02.mkv").exists()).isTrue()
    }
}

class RenameRulesTest {

    @Test fun cleanup_stripsJunk() {
        assertThat(RenameRules.cleanup("Show.Name.S01E02.1080p.[YTS].mkv"))
            .isEqualTo("Show Name S01E02 1080p.mkv")
        assertThat(RenameRules.cleanup("a_b__c.mp3")).isEqualTo("a b c.mp3")
    }

    @Test fun template_tokens() {
        val d = java.time.LocalDate.of(2026, 9, 21)
        assertThat(RenameRules.apply("a.mkv", "{date} {name}", false, d)).isEqualTo("2026-09-21 a.mkv")
        assertThat(RenameRules.apply("a", "{name}!", false, d)).isEqualTo("a!")
    }
}
