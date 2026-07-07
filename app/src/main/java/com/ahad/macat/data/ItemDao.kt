package com.ahad.macat.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
  @Query("SELECT * FROM items ORDER BY createdAt DESC, id DESC") fun observeAll(): Flow<List<Item>>

  @Insert suspend fun insert(item: Item): Long

  @Update suspend fun update(item: Item)

  @Delete suspend fun delete(item: Item)
}
