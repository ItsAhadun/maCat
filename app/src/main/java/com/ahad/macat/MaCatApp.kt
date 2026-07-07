package com.ahad.macat

import android.app.Application
import androidx.room.Room
import com.ahad.macat.data.CatalogueDatabase
import com.ahad.macat.data.ItemRepository
import com.ahad.macat.data.PhotoStore

class MaCatApp : Application() {
  val repository: ItemRepository by lazy {
    val db = Room.databaseBuilder(this, CatalogueDatabase::class.java, "catalogue.db").build()
    ItemRepository(db.itemDao(), PhotoStore(this))
  }
}
