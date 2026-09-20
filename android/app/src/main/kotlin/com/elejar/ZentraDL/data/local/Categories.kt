package com.elejar.ZentraDL.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Download category (P3a). A task belongs to exactly one category; the
 * category's [folder] (relative to the app download dir) is its save location.
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val folder: String,
    val createdAt: Long,
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY createdAt ASC")
    suspend fun allOnce(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): Category?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)

    /** Seeds the built-in set on first run; IGNORE keeps user edits intact. */
    suspend fun ensureDefaults(nowMs: Long = System.currentTimeMillis()) {
        DEFAULT_CATEGORIES.forEachIndexed { i, c -> insert(c.copy(createdAt = nowMs + i)) }
    }
}

/** Built-in categories (ids are stable — referenced by [Categorizer][com.elejar.ZentraDL.domain.Categorizer]). */
val DEFAULT_CATEGORIES = listOf(
    Category("videos", "Videos", "Videos", 0),
    Category("music", "Music", "Music", 0),
    Category("images", "Images", "Images", 0),
    Category("documents", "Documents", "Documents", 0),
    Category("apps", "Apps", "Apps", 0),
    Category("archives", "Archives", "Archives", 0),
    Category("other", "Other", "Other", 0),
)
