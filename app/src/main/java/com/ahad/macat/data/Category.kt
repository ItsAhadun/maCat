package com.ahad.macat.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A catalogue category.
 *
 * Items store the category's **name**, not this row's id, so the TEXT column items have carried
 * since v1 keeps working untouched and the v3 migration rewrites no rows. The trade is that
 * renaming a category has to rewrite the items that carry the old name — see
 * [ItemDao.reassignCategory] — and that names are unique, which the index enforces.
 */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val sortOrder: Int,
  /** Seeded with the app. Can be renamed and reordered like any other, but never deleted. */
  val isBuiltIn: Boolean = false,
)

/**
 * The categories a catalogue starts with. The first three are the ones that were the `Category`
 * enum up to v1.3, so their names have to stay exactly as they were spelled — existing items
 * carry those strings.
 */
val BUILT_IN_CATEGORIES =
  listOf("Clothes", "Shoes", "Jewellery", "Bags", "Outerwear", "Accessories")

/**
 * What the old `Category` enum left in the database, mapped to what it displayed.
 *
 * Room stores an enum by its **constant name**, so every row written before v3 carries `CLOTHES`,
 * not `Clothes` — and the old backup format exported `category.name`, so backup files carry the
 * same. Categories are now free text the user can edit, and shouting is not a good default, so
 * both the migration and the importer translate these three on the way in. Any name not listed
 * here is already user-facing text and passes through untouched.
 */
val LEGACY_CATEGORY_NAMES =
  mapOf("CLOTHES" to "Clothes", "SHOES" to "Shoes", "JEWELLERY" to "Jewellery")

/**
 * Puts the built-ins in an empty table. Runs from two places that never both apply: the v3
 * migration for a catalogue that already exists, and the database callback for a fresh install.
 * `INSERT OR IGNORE` keeps it harmless if it ever runs twice.
 */
internal fun seedBuiltInCategories(db: SupportSQLiteDatabase) {
  BUILT_IN_CATEGORIES.forEachIndexed { index, name ->
    db.execSQL(
      "INSERT OR IGNORE INTO categories (name, sortOrder, isBuiltIn) VALUES (?, ?, 1)",
      arrayOf<Any>(name, index),
    )
  }
}
