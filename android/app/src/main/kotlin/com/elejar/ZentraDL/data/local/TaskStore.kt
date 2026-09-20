package com.elejar.ZentraDL.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** App-level task metadata. Transfer state lives in :engine, never duplicated here. */
@Entity(tableName = "tasks")
data class TaskRecord(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val destPath: String,
    /** queued | downloading | paused | completed | failed */
    val status: String,
    val totalBytes: Long,
    val createdAt: Long,
    /** Last user-facing failure reason (null when never failed / cleared on start). */
    val error: String? = null,
    /** Category id (see [Category]); "other" when uncategorized. */
    val categoryId: String = "other",
    /** "http" or "torrent" (P4). */
    val kind: String = "http",
    /** Kind extras (P5: hls bandwidth "bw=1200000"). */
    val extra: String = "",
    /** True when moved to the private vault (P3e; hidden from the main list). */
    val vaulted: Boolean = false,
    /** Expected SHA-256 hex for manual verification (null = not set). */
    val expectedSha256: String? = null,
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskRecord>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<TaskRecord?>

    @Query("SELECT * FROM tasks")
    suspend fun allOnce(): List<TaskRecord>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskRecord?

    @Query("SELECT * FROM tasks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): TaskRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskRecord)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE tasks SET error = :error WHERE id = :id")
    suspend fun updateError(id: String, error: String?)

    @Query("UPDATE tasks SET fileName = :name, totalBytes = :total, destPath = :dest WHERE id = :id")
    suspend fun updateMeta(id: String, name: String, total: Long, dest: String)

    @Query("UPDATE tasks SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategory(id: String, categoryId: String)

    @Query("UPDATE tasks SET categoryId = 'other' WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: String)

    @Query("UPDATE tasks SET vaulted = :vaulted WHERE id = :id")
    suspend fun updateVaulted(id: String, vaulted: Boolean)

    @Query("UPDATE tasks SET expectedSha256 = :sha256 WHERE id = :id")
    suspend fun updateExpectedSha(id: String, sha256: String?)

    @Query("UPDATE tasks SET url = :url, extra = :extra WHERE id = :id")
    suspend fun updateUrl(id: String, url: String, extra: String)
}

/** Browser bookmark (P5a). */
@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long,
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): Bookmark?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

/** Browser history entry (P5a; capped by DAO trim). */
@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500")
    fun observeRecent(): Flow<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE visitedAt < :before")
    suspend fun trimOlderThan(before: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

/**
 * Torrent extras keyed by info-hash (== [TaskRecord.id]). Transfer state
 * lives in [BtSession][com.elejar.ZentraDL.engine.torrent.BtSession].
 */
@Entity(tableName = "torrent_tasks")
data class TorrentTask(
    @PrimaryKey val id: String,
    val magnet: String?,
    /** Persisted .torrent bytes (app-private torrents/); null when never had them. */
    val torrentPath: String?,
    /** CSV of wanted file paths; empty = all. */
    val selectedPaths: String = "",
    val sequential: Boolean = false,
    val name: String = "",
    val sizeBytes: Long = -1,
)

@Dao
interface TorrentTaskDao {
    @Query("SELECT * FROM torrent_tasks WHERE id = :id")
    suspend fun get(id: String): TorrentTask?

    @Query("SELECT * FROM torrent_tasks WHERE id = :id")
    fun observe(id: String): Flow<TorrentTask?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TorrentTask)

    @Query("UPDATE torrent_tasks SET selectedPaths = :paths WHERE id = :id")
    suspend fun updateSelection(id: String, paths: String)

    @Query("DELETE FROM torrent_tasks WHERE id = :id")
    suspend fun delete(id: String)
}
