package com.ahad.macat.data

import android.net.Uri
import androidx.room.withTransaction
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow

/** How long a binned item is kept before [purgeExpired] removes it and its photo for good. */
val BIN_RETENTION_MS: Long = TimeUnit.DAYS.toMillis(30)

class ItemRepository(
  private val db: CatalogueDatabase,
  private val photoStore: PhotoStore,
  private val colourDetector: ColourDetector,
  private val itemSegmenter: ItemSegmenter,
) {
  private val dao = db.itemDao()
  private val categoryDao = db.categoryDao()

  val items: Flow<List<Item>> = dao.observeAll()
  val deletedItems: Flow<List<Item>> = dao.observeDeleted()
  val categories: Flow<List<Category>> = categoryDao.observeAll()

  fun newCaptureFile(): File = photoStore.newCaptureFile()

  fun photoFile(item: Item): File = photoStore.file(item.photoFileName)

  suspend fun detectColours(uri: Uri): List<Colour> = colourDetector.detect(uri)

  /** The separate items a photo looks like it holds; fewer than two means leave it alone. */
  suspend fun segments(uri: Uri): List<CropRect> = itemSegmenter.detect(uri)

  /** Cuts one photo into a photo per region, ready to be added as items in their own right. */
  suspend fun splitPhoto(uri: Uri, regions: List<CropRect>, aspectRatio: Float): List<Uri> =
    photoStore.writeCrops(uri, regions, aspectRatio)

  suspend fun addItem(
    name: String,
    category: String,
    photoUri: Uri,
    colours: List<Colour>,
    crop: CropRect?,
  ) {
    val fileName = photoStore.import(photoUri)
    dao.insert(
      Item(name = name, category = category, photoFileName = fileName, colours = colours)
        .withCrop(crop)
    )
  }

  suspend fun updateItem(
    item: Item,
    name: String,
    category: String,
    newPhotoUri: Uri?,
    colours: List<Colour>,
    crop: CropRect?,
  ) {
    val updated = item.copy(name = name, category = category, colours = colours).withCrop(crop)
    if (newPhotoUri != null) {
      val fileName = photoStore.import(newPhotoUri)
      dao.update(updated.copy(photoFileName = fileName))
      photoStore.delete(item.photoFileName)
    } else {
      dao.update(updated)
    }
  }

  // Deleting. Nothing here touches a photo file: the file outlives the bin so that Undo and
  // Restore have something to bring back. Files are freed in [purgeExpired] and nowhere else.

  suspend fun softDelete(item: Item) {
    dao.update(item.copy(deletedAt = System.currentTimeMillis()))
  }

  suspend fun softDeleteAll(items: List<Item>) {
    val now = System.currentTimeMillis()
    dao.updateAll(items.map { it.copy(deletedAt = now) })
  }

  suspend fun restore(item: Item) {
    dao.update(item.copy(deletedAt = null))
  }

  suspend fun restoreAll(items: List<Item>) {
    dao.updateAll(items.map { it.copy(deletedAt = null) })
  }

  /** Removes binned items past [BIN_RETENTION_MS], photo files included. Runs on app start. */
  suspend fun purgeExpired() {
    val cutoff = System.currentTimeMillis() - BIN_RETENTION_MS
    for (item in dao.deletedBefore(cutoff)) {
      dao.delete(item)
      photoStore.delete(item.photoFileName)
    }
  }

  /** Empties the bin now, rather than waiting out the retention window. */
  suspend fun purgeAll(items: List<Item>) {
    for (item in items) {
      dao.delete(item)
      photoStore.delete(item.photoFileName)
    }
  }

  suspend fun setFavourite(item: Item, favourite: Boolean) {
    dao.update(item.copy(isFavourite = favourite))
  }

  suspend fun setFavouriteAll(items: List<Item>, favourite: Boolean) {
    dao.updateAll(items.map { it.copy(isFavourite = favourite) })
  }

  suspend fun setCategoryAll(items: List<Item>, category: String) {
    dao.updateAll(items.map { it.copy(category = category) })
  }

  // Categories.

  suspend fun countInCategory(name: String): Int = dao.countInCategory(name)

  /** False when the name is blank or already taken — names are the identity items refer to. */
  suspend fun addCategory(name: String): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || categoryDao.byName(trimmed) != null) return false
    categoryDao.insert(Category(name = trimmed, sortOrder = categoryDao.nextSortOrder()))
    return true
  }

  /**
   * Renames a category and carries its items across in one transaction — items store the name,
   * so a rename that only touched the categories table would strand every item on it.
   */
  suspend fun renameCategory(category: Category, newName: String): Boolean {
    val trimmed = newName.trim()
    if (trimmed.isEmpty() || trimmed == category.name) return false
    if (categoryDao.byName(trimmed) != null) return false
    db.withTransaction {
      categoryDao.update(category.copy(name = trimmed))
      dao.reassignCategory(category.name, trimmed)
    }
    return true
  }

  /**
   * Deletes a user category, moving anything still on it to [replacement] first so no item is
   * left pointing at a category that no longer exists. Built-ins are never deleted.
   */
  suspend fun deleteCategory(category: Category, replacement: String): Boolean {
    if (category.isBuiltIn) return false
    db.withTransaction {
      dao.reassignCategory(category.name, replacement)
      categoryDao.delete(category)
    }
    return true
  }

  suspend fun reorderCategories(ordered: List<Category>) {
    categoryDao.updateAll(ordered.mapIndexed { index, category -> category.copy(sortOrder = index) })
  }
}
