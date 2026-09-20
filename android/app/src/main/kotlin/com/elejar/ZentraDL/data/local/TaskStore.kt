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
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskRecord>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskRecord)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE tasks SET fileName = :name, totalBytes = :total, destPath = :dest WHERE id = :id")
    suspend fun updateMeta(id: String, name: String, total: Long, dest: String)
}
