package com.elejar.ZentraDL.domain.rules

import com.elejar.ZentraDL.data.local.CategoryDao
import com.elejar.ZentraDL.data.local.RuleLogDao
import com.elejar.ZentraDL.data.local.RuleLogEntry
import com.elejar.ZentraDL.data.local.RuleDao
import com.elejar.ZentraDL.data.local.RuleEntity
import com.elejar.ZentraDL.data.local.TaskDao
import com.elejar.ZentraDL.data.local.TaskRecord
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject

/**
 * Automation engine (R1): evaluates [Rule]s on triggers, applies actions,
 * writes an undoable log. Moves/renames run only via explicit rules
 * (OFF by default — the catalogue ships empty).
 */
class RuleEngine(
    private val tasks: TaskDao,
    private val rules: RuleDao,
    private val log: RuleLogDao,
    private val categories: CategoryDao,
    private val master: Flow<Boolean> = flowOf(true),
    private val filesDir: File,
) {
    suspend fun allRules(): List<Rule> = rules.allOnce().mapNotNull { it.toRule() }

    suspend fun addRule(rule: Rule) {
        rules.insert(rule.toEntity())
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        rules.updateEnabled(id, enabled)
    }

    suspend fun deleteRule(id: String) {
        rules.delete(id)
    }

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun onComplete(rec: TaskRecord) {
        if (!master.first()) return
        if (rec.kind == "torrent") return // torrents keep seeding in place (S2)
        fire(rec, Trigger.ON_COMPLETE)
    }

    suspend fun onAdd(rec: TaskRecord) {
        if (!master.first()) return
        fire(rec, Trigger.ON_ADD)
    }

    private suspend fun fire(rec: TaskRecord, trigger: Trigger) {
        val fresh = tasks.get(rec.id) ?: return
        allRules()
            .filter { it.enabled && it.trigger == trigger && RuleMatch.matchesAll(fresh, it.conditions) }
            .forEach { apply(it, fresh) }
    }

    private suspend fun apply(rule: Rule, rec: TaskRecord) {
        var r = tasks.get(rec.id) ?: return
        rule.actions.forEach { a ->
            r = when (a) {
                is RuleAction.MoveCategory -> applyMove(rule, r, a.categoryId) ?: r
                is RuleAction.Rename -> applyRename(rule, r, a.template, a.cleanup) ?: r
            }
        }
    }

    private suspend fun applyMove(rule: Rule, rec: TaskRecord, categoryId: String): TaskRecord? {
        val folder = categories.get(categoryId)?.folder ?: return null
        val destDir = File(filesDir.resolve("downloads"), folder).apply { mkdirs() }
        if (rec.destPath == destDir.absolutePath) {
            tasks.updateCategory(rec.id, categoryId)
            return tasks.get(rec.id)
        }
        val src = File(rec.destPath, rec.fileName)
        val dest = File(destDir, rec.fileName)
        if (src.exists()) {
            if (dest.exists() || !src.renameTo(dest)) {
                writeLog("Not moved (${rule.name}): ${rec.fileName} — name clash")
                return null
            }
        }
        tasks.updateMeta(rec.id, rec.fileName, rec.totalBytes, destDir.absolutePath)
        tasks.updateCategory(rec.id, categoryId)
        writeLog("Moved to $folder (${rule.name}): ${rec.fileName}", undo(rec.id, rec.destPath, rec.fileName))
        return tasks.get(rec.id)
    }

    private suspend fun applyRename(rule: Rule, rec: TaskRecord, template: String, cleanup: Boolean): TaskRecord? {
        val next = RenameRules.apply(rec.fileName, template, cleanup)
        if (next.isBlank() || next == rec.fileName || next.contains('/')) return tasks.get(rec.id)
        val src = File(rec.destPath, rec.fileName)
        val dest = File(rec.destPath, next)
        if (src.exists()) {
            if (dest.exists() || !src.renameTo(dest)) {
                writeLog("Not renamed (${rule.name}): ${rec.fileName} — name clash")
                return null
            }
        }
        tasks.updateMeta(rec.id, next, rec.totalBytes, rec.destPath)
        writeLog("Renamed (${rule.name}): ${rec.fileName} → $next", undo(rec.id, rec.destPath, rec.fileName))
        return tasks.get(rec.id)
    }

    /** Preview a rule against completed HTTP tasks (no changes). */
    suspend fun dryRun(rule: Rule): List<String> {
        val out = mutableListOf<String>()
        tasks.allOnce()
            .filter { it.kind == "http" && it.status == "completed" && RuleMatch.matchesAll(it, rule.conditions) }
            .forEach { r ->
                rule.actions.forEach { a ->
                    when (a) {
                        is RuleAction.MoveCategory -> {
                            val folder = categories.get(a.categoryId)?.folder ?: return@forEach
                            if (File(r.destPath).absolutePath != File(filesDir.resolve("downloads"), folder).absolutePath) {
                                out += "${r.fileName} → $folder/"
                            }
                        }
                        is RuleAction.Rename -> {
                            val next = RenameRules.apply(r.fileName, a.template, a.cleanup)
                            if (next.isNotBlank() && next != r.fileName) out += "${r.fileName} → $next"
                        }
                    }
                }
            }
        return out
    }

    /** Undo a logged move/rename. False = files changed since. */
    suspend fun undo(logId: Long): Boolean {
        val entry = log.get(logId) ?: return false
        val undo = entry.undoJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        val taskId = undo.optString("taskId")
        val fromDir = undo.optString("fromDir")
        val fromName = undo.optString("fromName")
        val rec = tasks.get(taskId) ?: return false
        val src = File(rec.destPath, rec.fileName)
        val destDir = File(fromDir).apply { if (!exists()) return false }
        val dest = File(destDir, fromName)
        if (src.exists()) {
            if (dest.exists() || !src.renameTo(dest)) return false
        }
        tasks.updateMeta(taskId, fromName, rec.totalBytes, destDir.absolutePath)
        log.delete(logId)
        return true
    }

    suspend fun recentLog(limit: Int = 50) = log.recent(limit)
    suspend fun clearLog() = log.clear()

    private suspend fun writeLog(text: String, undoJson: String? = null) {
        log.insert(RuleLogEntry(timeMs = System.currentTimeMillis(), text = text, undoJson = undoJson))
        log.trim(200)
    }

    private fun undo(taskId: String, fromDir: String, fromName: String): String =
        JSONObject().put("taskId", taskId).put("fromDir", fromDir).put("fromName", fromName).toString()

    private fun RuleEntity.toRule(): Rule? {
        val trigger = runCatching { Trigger.valueOf(trigger) }.getOrNull() ?: return null
        return Rule(
            id = id, name = name, enabled = enabled, trigger = trigger,
            conditions = conditionsJson.lines().filter { it.isNotBlank() }.mapNotNull { RuleCodec.decodeCondition(it) },
            actions = actionsJson.lines().filter { it.isNotBlank() }.mapNotNull { RuleCodec.decodeAction(it) },
        )
    }

    private fun Rule.toEntity(): RuleEntity = RuleEntity(
        id = id, name = name, enabled = enabled, trigger = trigger.name,
        conditionsJson = conditions.joinToString("\n") { RuleCodec.encodeCondition(it) },
        actionsJson = actions.joinToString("\n") { RuleCodec.encodeAction(it) },
        createdAt = System.currentTimeMillis(),
    )
}
