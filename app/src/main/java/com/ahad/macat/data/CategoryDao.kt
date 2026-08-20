package com.ahad.macat.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
  @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
  fun observeAll(): Flow<List<Category>>

  /** One-shot read, for backup export and for the reorder/rename edits. */
  @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
  suspend fun snapshot(): List<Category>

  @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
  suspend fun byName(name: String): Category?

  /** Ignores a name that already exists, which is what merging a backup wants. */
  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(category: Category): Long

  @Update suspend fun update(category: Category)

  @Update suspend fun updateAll(categories: List<Category>)

  @Delete suspend fun delete(category: Category)

  @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories") suspend fun nextSortOrder(): Int
}
