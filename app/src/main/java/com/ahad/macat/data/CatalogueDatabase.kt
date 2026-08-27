package com.ahad.macat.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The schema is still v3: an item's colours are a list now, but they are stored in the TEXT
 * column a single colour already used, so no table changed shape. See [ColourListConverter].
 */
@Database(entities = [Item::class, Category::class], version = 3, exportSchema = false)
@TypeConverters(ColourListConverter::class)
abstract class CatalogueDatabase : RoomDatabase() {
  abstract fun itemDao(): ItemDao

  abstract fun categoryDao(): CategoryDao
}

/**
 * v2 adds the colour tag and the framing rect. Every column is nullable with no default, so
 * existing rows keep their data untouched and simply read as "untagged, uncropped" — installing a
 * new version over an old one must never cost anyone their catalogue.
 */
val MIGRATION_1_2 =
  object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE items ADD COLUMN colour TEXT")
      db.execSQL("ALTER TABLE items ADD COLUMN cropLeft REAL")
      db.execSQL("ALTER TABLE items ADD COLUMN cropTop REAL")
      db.execSQL("ALTER TABLE items ADD COLUMN cropRight REAL")
      db.execSQL("ALTER TABLE items ADD COLUMN cropBottom REAL")
    }
  }

/**
 * v3 adds the favourite flag, the bin, and user-editable categories.
 *
 * Categories become a table, but items keep the TEXT column they have carried since v1 and keep
 * storing the category *name*, so the column itself is untouched.
 *
 * Its contents are not. Room stores an enum by its constant name, so every existing row says
 * `CLOTHES` where the app displayed `Clothes`. Seeding the table with the displayed spelling
 * while items keep shouting would leave every item on a category no chip matches — they would
 * simply vanish from the filtered views. So the three legacy values are rewritten here, once,
 * to the names the seeded rows use. See [LEGACY_CATEGORY_NAMES].
 *
 * The `CREATE TABLE` has to match what Room generates for [Category] exactly, quoting included:
 * Room compares the real schema against the entities on open and refuses to start on a mismatch.
 */
val MIGRATION_2_3 =
  object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE items ADD COLUMN isFavourite INTEGER")
      db.execSQL("ALTER TABLE items ADD COLUMN deletedAt INTEGER")
      for ((stored, displayed) in LEGACY_CATEGORY_NAMES) {
        db.execSQL(
          "UPDATE items SET category = ? WHERE category = ?",
          arrayOf<Any>(displayed, stored),
        )
      }
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `categories` (" +
          "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
          "`name` TEXT NOT NULL, " +
          "`sortOrder` INTEGER NOT NULL, " +
          "`isBuiltIn` INTEGER NOT NULL)"
      )
      db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)"
      )
      seedBuiltInCategories(db)
    }
  }

/**
 * A fresh install builds the schema straight from the entities, so [MIGRATION_2_3] never runs and
 * the categories table would come up empty. Seed it on create instead.
 */
val SEED_CATEGORIES_CALLBACK =
  object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
      seedBuiltInCategories(db)
    }
  }
