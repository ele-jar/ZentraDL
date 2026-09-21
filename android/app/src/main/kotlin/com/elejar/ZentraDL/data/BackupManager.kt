package com.elejar.ZentraDL.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elejar.ZentraDL.data.local.AppDatabase
import com.elejar.ZentraDL.data.local.Bookmark
import com.elejar.ZentraDL.data.local.Category
import com.elejar.ZentraDL.data.local.RuleEntity
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.data.local.TorrentTask
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Backup DTOs (X2; secrets excluded — the app stores none outside Keystore-less prefs). */
@Serializable
data class BackupCategory(val id: String, val name: String, val folder: String)

@Serializable
data class BackupBookmark(val title: String, val url: String)

@Serializable
data class BackupRule(
    val name: String,
    val enabled: Boolean,
    val trigger: String,
    val conditions: List<String>,
    val actions: List<String>,
)

@Serializable
data class BackupTask(
    val id: String,
    val url: String,
    val fileName: String,
    val destPath: String,
    val status: String,
    val totalBytes: Long,
    val createdAt: Long,
    val categoryId: String,
    val kind: String,
    val extra: String,
)

@Serializable
data class BackupTorrent(
    val id: String,
    val magnet: String?,
    val selectedPaths: String,
    val sequential: Boolean,
    val name: String,
    val sizeBytes: Long,
)

@Serializable
data class BackupPayload(
    val version: Int = 1,
    /** Settings as `type:value` (`int:`/`bool:`/`str:`). */
    val settings: Map<String, String> = emptyMap(),
    val categories: List<BackupCategory> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
    val rules: List<BackupRule> = emptyList(),
    val tasks: List<BackupTask> = emptyList(),
    val torrents: List<BackupTorrent> = emptyList(),
)

private val backupJson = Json { ignoreUnknownKeys = true }

/** Plaintext JSON backup (user-warned; password encryption is a gap). */
@Singleton
class BackupManager @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsStore,
) {
    suspend fun export(): ByteArray {
        val settingsMap = settings.data.first().asMap().entries.associate { (k, v) ->
            k.name to when (v) {
                is Int -> "int:$v"
                is Boolean -> "bool:$v"
                else -> "str:$v"
            }
        }
        val payload = BackupPayload(
            settings = settingsMap,
            categories = db.categoryDao().allOnce().map { BackupCategory(it.id, it.name, it.folder) },
            bookmarks = db.bookmarkDao().allOnce().map { BackupBookmark(it.title, it.url) },
            rules = db.ruleDao().allOnce().map {
                BackupRule(it.name, it.enabled, it.trigger, it.conditionsJson.lines(), it.actionsJson.lines())
            },
            tasks = db.taskDao().allOnce().map {
                BackupTask(it.id, it.url, it.fileName, it.destPath, it.status, it.totalBytes, it.createdAt, it.categoryId, it.kind, it.extra)
            },
            torrents = db.torrentTaskDao().allOnce().map {
                BackupTorrent(it.id, it.magnet, it.selectedPaths, it.sequential, it.name, it.sizeBytes)
            },
        )
        return backupJson.encodeToString(BackupPayload.serializer(), payload).toByteArray()
    }

    /** Restore everything (records land paused unless completed; missing files surface on use). */
    suspend fun import(bytes: ByteArray): String {
        val payload = runCatching {
            backupJson.decodeFromString(BackupPayload.serializer(), bytes.decodeToString())
        }.getOrNull() ?: return "Not a ZentraDL backup"
        if (payload.version != 1) return "Unsupported backup version"
        payload.settings.forEach { (name, tv) ->
            val value = tv.substringAfter(':')
            when {
                tv.startsWith("int:") -> value.toIntOrNull()?.let { v ->
                    settings.editRaw(intPreferencesKey(name), v)
                }
                tv.startsWith("bool:") -> settings.editRaw(booleanPreferencesKey(name), value.toBooleanStrictOrNull() ?: false)
                tv.startsWith("str:") -> settings.editRaw(stringPreferencesKey(name), value)
            }
        }
        payload.categories.forEach {
            db.categoryDao().insert(Category(it.id, it.name, it.folder, System.currentTimeMillis()))
        }
        payload.bookmarks.forEach {
            if (db.bookmarkDao().findByUrl(it.url) == null) {
                db.bookmarkDao().insert(Bookmark(title = it.title, url = it.url, createdAt = System.currentTimeMillis()))
            }
        }
        payload.rules.forEach {
            db.ruleDao().insert(
                RuleEntity(
                    id = UUID.randomUUID().toString(), name = it.name, enabled = it.enabled,
                    trigger = it.trigger, conditionsJson = it.conditions.joinToString("\n"),
                    actionsJson = it.actions.joinToString("\n"), createdAt = System.currentTimeMillis(),
                ),
            )
        }
        payload.tasks.forEach {
            db.taskDao().insert(
                TaskRecord(
                    id = it.id, url = it.url, fileName = it.fileName, destPath = it.destPath,
                    status = if (it.status == "completed") "completed" else "paused",
                    totalBytes = it.totalBytes, createdAt = it.createdAt,
                    categoryId = it.categoryId, kind = it.kind, extra = it.extra,
                ),
            )
        }
        payload.torrents.forEach {
            db.torrentTaskDao().insert(
                TorrentTask(
                    id = it.id, magnet = it.magnet, torrentPath = null,
                    selectedPaths = it.selectedPaths, sequential = it.sequential,
                    name = it.name, sizeBytes = it.sizeBytes,
                ),
            )
        }
        return "Restored ${payload.tasks.size} tasks, ${payload.rules.size} rules"
    }

    companion object {
        fun encode(payload: BackupPayload): ByteArray =
            backupJson.encodeToString(BackupPayload.serializer(), payload).toByteArray()

        fun decode(bytes: ByteArray): BackupPayload? = runCatching {
            backupJson.decodeFromString(BackupPayload.serializer(), bytes.decodeToString())
        }.getOrNull()
    }
}
