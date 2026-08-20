package com.ahad.macat

import android.app.Application
import androidx.room.Room
import com.ahad.macat.data.BackupManager
import com.ahad.macat.data.CatalogueDatabase
import com.ahad.macat.data.ColourDetector
import com.ahad.macat.data.ItemRepository
import com.ahad.macat.data.MIGRATION_1_2
import com.ahad.macat.data.MIGRATION_2_3
import com.ahad.macat.data.PhotoStore
import com.ahad.macat.data.SEED_CATEGORIES_CALLBACK
import com.ahad.macat.data.SettingsStore

class MaCatApp : Application() {
  private val database by lazy {
    Room.databaseBuilder(this, CatalogueDatabase::class.java, "catalogue.db")
      // Never fall back to a destructive migration: a schema change must migrate the catalogue,
      // not wipe it. 1.3 shipped schema v1, so upgrading from the published release runs both of
      // these in turn — registering only the newest would strand every existing install.
      .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
      .addCallback(SEED_CATEGORIES_CALLBACK)
      .build()
  }
  private val photoStore by lazy { PhotoStore(this) }

  val repository: ItemRepository by lazy { ItemRepository(database, photoStore, ColourDetector(this)) }

  val backupManager: BackupManager by lazy { BackupManager(this, database, photoStore) }

  val settings: SettingsStore by lazy { SettingsStore(this) }
}
