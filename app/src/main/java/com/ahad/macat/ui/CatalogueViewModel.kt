package com.ahad.macat.ui

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahad.macat.data.Category
import com.ahad.macat.data.Item
import com.ahad.macat.data.ItemRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One photo collected in the bulk-add flow, plus the info the user types for it. */
data class BulkEntry(
  val photoUri: Uri,
  val name: String = "",
  val category: Category = Category.CLOTHES,
)

class CatalogueViewModel(private val repository: ItemRepository) : ViewModel() {

  /** null = still loading (avoids flashing the empty state on launch). */
  val allItems: StateFlow<List<Item>?> =
    repository.items.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  private val _filter = MutableStateFlow<Category?>(null)
  val filter: StateFlow<Category?> = _filter.asStateFlow()

  val filteredItems: StateFlow<List<Item>?> =
    combine(allItems, _filter) { items, filter ->
        if (filter == null) items else items?.filter { it.category == filter }
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  fun setFilter(category: Category?) {
    _filter.value = category
  }

  /** Set by the grid so the feed can scroll to the tapped item. */
  private val _scrollToItemId = MutableStateFlow<Long?>(null)
  val scrollToItemId: StateFlow<Long?> = _scrollToItemId.asStateFlow()

  fun requestScrollTo(itemId: Long) {
    _scrollToItemId.value = itemId
  }

  fun clearScrollRequest() {
    _scrollToItemId.value = null
  }

  fun itemById(id: Long): Item? = allItems.value?.find { it.id == id }

  fun photoFile(item: Item): File = repository.photoFile(item)

  fun newCaptureFile(): File = repository.newCaptureFile()

  fun addItem(name: String, category: Category, photoUri: Uri) {
    viewModelScope.launch { repository.addItem(name, category, photoUri) }
  }

  fun updateItem(item: Item, name: String, category: Category, newPhotoUri: Uri?) {
    viewModelScope.launch { repository.updateItem(item, name, category, newPhotoUri) }
  }

  fun deleteItem(item: Item) {
    viewModelScope.launch { repository.deleteItem(item) }
  }

  // Bulk add state lives here so it survives configuration changes (fold/unfold, rotation).
  val bulkEntries = mutableStateListOf<BulkEntry>()

  fun bulkAddPhoto(uri: Uri) {
    bulkEntries.add(BulkEntry(photoUri = uri, category = _filter.value ?: Category.CLOTHES))
  }

  fun bulkUpdateEntry(index: Int, entry: BulkEntry) {
    if (index in bulkEntries.indices) bulkEntries[index] = entry
  }

  fun bulkRemoveEntry(index: Int) {
    if (index in bulkEntries.indices) bulkEntries.removeAt(index)
  }

  fun bulkClear() {
    bulkEntries.clear()
  }

  fun saveBulkEntries() {
    val entries = bulkEntries.toList()
    bulkClear()
    viewModelScope.launch {
      entries.forEach { repository.addItem(it.name.trim(), it.category, it.photoUri) }
    }
  }
}
