package com.ahad.macat.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Item::class], version = 1, exportSchema = false)
abstract class CatalogueDatabase : RoomDatabase() {
  abstract fun itemDao(): ItemDao
}
