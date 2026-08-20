package com.ahad.macat.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
  /** The catalogue. Binned items are excluded here and everywhere else that reads live items. */
  @Query("SELECT * FROM items WHERE deletedAt IS NULL ORDER BY createdAt DESC, id DESC")
  fun observeAll(): Flow<List<Item>>

  /** The bin, most recently deleted first. */
  @Query("SELECT * FROM items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, id DESC")
  fun observeDeleted(): Flow<List<Item>>

  /** One-shot read for backup export. The bin is deliberately not backed up. */
  @Query("SELECT * FROM items WHERE deletedAt IS NULL ORDER BY createdAt DESC, id DESC")
  suspend fun snapshot(): List<Item>

  /** Items binned before [cutoff]: what a purge removes for good, photo files included. */
  @Query("SELECT * FROM items WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
  suspend fun deletedBefore(cutoff: Long): List<Item>

  /** How many live items a category holds — what the "category is not empty" prompt reports. */
  @Query("SELECT COUNT(*) FROM items WHERE category = :name AND deletedAt IS NULL")
  suspend fun countInCategory(name: String): Int

  /**
   * Moves every item from one category name to another. Items carry the name rather than a row
   * id, so this is what a rename costs — and what emptying a category into another one does.
   * Binned items move too: they keep a valid category for when they are restored.
   */
  @Query("UPDATE items SET category = :to WHERE category = :from")
  suspend fun reassignCategory(from: String, to: String)

  @Insert suspend fun insert(item: Item): Long

  @Update suspend fun update(item: Item)

  @Update suspend fun updateAll(items: List<Item>)

  @Delete suspend fun delete(item: Item)
}
