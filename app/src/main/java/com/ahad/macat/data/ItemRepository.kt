package com.ahad.macat.data

import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.Flow

class ItemRepository(private val dao: ItemDao, private val photoStore: PhotoStore) {
  val items: Flow<List<Item>> = dao.observeAll()

  fun newCaptureFile(): File = photoStore.newCaptureFile()

  fun photoFile(item: Item): File = photoStore.file(item.photoFileName)

  suspend fun addItem(name: String, category: Category, photoUri: Uri) {
    val fileName = photoStore.import(photoUri)
    dao.insert(Item(name = name, category = category, photoFileName = fileName))
  }

  suspend fun updateItem(item: Item, name: String, category: Category, newPhotoUri: Uri?) {
    if (newPhotoUri != null) {
      val fileName = photoStore.import(newPhotoUri)
      dao.update(item.copy(name = name, category = category, photoFileName = fileName))
      photoStore.delete(item.photoFileName)
    } else {
      dao.update(item.copy(name = name, category = category))
    }
  }

  suspend fun deleteItem(item: Item) {
    dao.delete(item)
    photoStore.delete(item.photoFileName)
  }
}
