package com.ahad.macat.ui

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahad.macat.data.BUILT_IN_CATEGORIES
import com.ahad.macat.data.BackupManager
import com.ahad.macat.data.Category
import com.ahad.macat.data.Colour
import com.ahad.macat.data.CropRect
import com.ahad.macat.data.FilterState
import com.ahad.macat.data.FilterVisibility
import com.ahad.macat.data.Item
import com.ahad.macat.data.ItemRepository
import com.ahad.macat.data.SettingsStore
import com.ahad.macat.data.SortOrder
import com.ahad.macat.data.autoName
import com.ahad.macat.data.colourCounts
import com.ahad.macat.data.coloursPresent
import com.ahad.macat.data.filterAndSort
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One photo collected in the bulk-add flow, plus the info the user types for it. */
data class BulkEntry(
  /**
   * Identity that survives the list being rewritten. Splitting a photo replaces one entry with
   * several, so background work started for a photo cannot come back and find its entry by
   * position, nor by its Uri — the same gallery photo can be picked twice.
   */
  val id: Long,
  val photoUri: Uri,
  val name: String = "",
  val category: String,
  val colours: List<Colour> = emptyList(),
  val crop: CropRect? = null,
  /**
   * False once the user types a name of their own. Until then [name] holds the auto name and
   * follows the colours and the category, so the field can show it as editable text instead of
   * as a caption underneath saying what would be saved.
   */
  val nameIsAuto: Boolean = true,
  /**
   * False until the user files this photo themselves. Untouched entries follow whatever category
   * was last picked — see [bulkSetCategory].
   */
  val categoryChosen: Boolean = false,
  /**
   * What the segmenter made of the photo. Two or more boxes means it looks like several items and
   * the user has yet to be asked; see [CatalogueViewModel.pendingSplitReview].
   */
  val segments: List<CropRect> = emptyList(),
  /** True once the user has answered the "is this several items?" question for this photo. */
  val splitReviewed: Boolean = false,
)

/**
 * A one-shot message for the snackbar. [action] is what its button does — undoing a delete, in
 * practice — and is null for messages that are only telling the user something.
 */
data class UiMessage(
  val text: String,
  val actionLabel: String? = null,
  val action: (() -> Unit)? = null,
)

class CatalogueViewModel(
  private val repository: ItemRepository,
  private val backupManager: BackupManager,
  private val settings: SettingsStore,
) : ViewModel() {

  /** null = still loading (avoids flashing the empty state on launch). */
  val allItems: StateFlow<List<Item>?> =
    repository.items.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val categories: StateFlow<List<Category>> =
    repository.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val deletedItems: StateFlow<List<Item>> =
    repository.deletedItems.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val _filterState = MutableStateFlow(FilterState())
  val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

  val filterVisibility: StateFlow<FilterVisibility> = settings.filterVisibility

  val filteredItems: StateFlow<List<Item>?> =
    combine(allItems, _filterState) { items, state -> items?.filterAndSort(state) }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  /** Only the colours the catalogue actually contains — the swatch row shows nothing else. */
  val availableColours: StateFlow<List<Colour>> =
    allItems
      .map { it.orEmpty().coloursPresent() }
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  /** Colour totals for the census, largest share first. */
  val colourCensus: StateFlow<List<Pair<Colour, Int>>> =
    allItems
      .map { it.orEmpty().colourCounts() }
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  init {
    // Free the photos of anything that has sat in the bin past its retention window.
    viewModelScope.launch { repository.purgeExpired() }
    // A hidden filter must not go on narrowing the catalogue from off-screen: that is
    // indistinguishable from items having disappeared. Clearing belongs here rather than in an
    // effect beside the control, because the ViewModel outlives every screen that draws one.
    viewModelScope.launch {
      settings.filterVisibility.collect { visible ->
        _filterState.update { state ->
          state.copy(
            category = state.category?.takeIf { visible.categories },
            colour = state.colour?.takeIf { visible.colours },
            favouritesOnly = state.favouritesOnly && visible.favourites,
            query = if (visible.search) state.query else "",
            sort = if (visible.sort) state.sort else SortOrder.NEWEST,
          )
        }
      }
    }
  }

  // Filters.

  fun setCategoryFilter(category: String?) {
    _filterState.update { it.copy(category = category) }
  }

  fun setColourFilter(colour: Colour?) {
    _filterState.update { it.copy(colour = colour) }
  }

  fun toggleFavouritesOnly() {
    _filterState.update { it.copy(favouritesOnly = !it.favouritesOnly) }
  }

  fun setQuery(query: String) {
    _filterState.update { it.copy(query = query) }
  }

  /** Picking shuffle — or picking it again — deals a fresh order; the other sorts are stable. */
  fun setSort(sort: SortOrder) {
    _filterState.update {
      if (sort == SortOrder.SHUFFLE) {
        it.copy(sort = sort, shuffleSeed = Random.nextLong())
      } else {
        it.copy(sort = sort)
      }
    }
  }

  fun setFilterVisibility(visibility: FilterVisibility) {
    settings.setFilterVisibility(visibility)
  }

  /** What a new item is filed under when the user does not say: whatever they are looking at. */
  val defaultCategory: String
    get() =
      _filterState.value.category
        ?: categories.value.firstOrNull()?.name
        ?: BUILT_IN_CATEGORIES.first()

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

  /** The colour tags guessed from a photo, for the auto name and the colour chips. */
  suspend fun detectColours(uri: Uri): List<Colour> = repository.detectColours(uri)

  fun addItem(
    name: String,
    category: String,
    photoUri: Uri,
    colours: List<Colour>,
    crop: CropRect?,
  ) {
    viewModelScope.launch { repository.addItem(name, category, photoUri, colours, crop) }
  }

  fun updateItem(
    item: Item,
    name: String,
    category: String,
    newPhotoUri: Uri?,
    colours: List<Colour>,
    crop: CropRect?,
  ) {
    viewModelScope.launch {
      repository.updateItem(item, name, category, newPhotoUri, colours, crop)
    }
  }

  fun setFavourite(item: Item, favourite: Boolean) {
    viewModelScope.launch { repository.setFavourite(item, favourite) }
  }

  // Deleting. Nothing is destroyed here — items go to the bin and come back from it, and the
  // photo file outlives both so Undo and Restore have something to restore.

  fun deleteItem(item: Item) {
    viewModelScope.launch {
      repository.softDelete(item)
      send(
        UiMessage("Deleted “${item.displayName}”", "Undo") {
          viewModelScope.launch { repository.restore(item) }
        }
      )
    }
  }

  fun restoreItem(item: Item) {
    viewModelScope.launch { repository.restore(item) }
  }

  fun purgeItem(item: Item) {
    viewModelScope.launch { repository.purgeAll(listOf(item)) }
  }

  fun emptyBin() {
    val binned = deletedItems.value
    viewModelScope.launch {
      repository.purgeAll(binned)
      send(UiMessage("Bin emptied"))
    }
  }

  // Bulk actions on the grid selection.

  private val _selection = MutableStateFlow<Set<Long>>(emptySet())
  val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

  fun toggleSelected(itemId: Long) {
    _selection.update { if (itemId in it) it - itemId else it + itemId }
  }

  fun clearSelection() {
    _selection.value = emptySet()
  }

  private fun selectedItems(): List<Item> {
    val ids = _selection.value
    return allItems.value.orEmpty().filter { it.id in ids }
  }

  fun deleteSelected() {
    val items = selectedItems()
    if (items.isEmpty()) return
    clearSelection()
    viewModelScope.launch {
      repository.softDeleteAll(items)
      send(
        UiMessage("Deleted ${items.size.itemsLabel()}", "Undo") {
          viewModelScope.launch { repository.restoreAll(items) }
        }
      )
    }
  }

  fun setCategoryOfSelected(category: String) {
    val items = selectedItems()
    if (items.isEmpty()) return
    clearSelection()
    viewModelScope.launch {
      repository.setCategoryAll(items, category)
      send(UiMessage("Moved ${items.size.itemsLabel()} to $category"))
    }
  }

  fun setFavouriteOnSelected(favourite: Boolean) {
    val items = selectedItems()
    if (items.isEmpty()) return
    clearSelection()
    viewModelScope.launch { repository.setFavouriteAll(items, favourite) }
  }

  // Categories.

  fun addCategory(name: String) {
    viewModelScope.launch {
      if (!repository.addCategory(name)) send(UiMessage("That category already exists"))
    }
  }

  fun renameCategory(category: Category, newName: String) {
    viewModelScope.launch {
      val renamed = repository.renameCategory(category, newName)
      if (!renamed) {
        send(UiMessage("That category already exists"))
        return@launch
      }
      // The filter points at a name that no longer exists; follow the rename.
      _filterState.update {
        if (it.category == category.name) it.copy(category = newName.trim()) else it
      }
    }
  }

  fun moveCategory(category: Category, by: Int) {
    val ordered = categories.value.toMutableList()
    val index = ordered.indexOfFirst { it.id == category.id }
    val target = index + by
    if (index < 0 || target !in ordered.indices) return
    ordered.add(target, ordered.removeAt(index))
    viewModelScope.launch { repository.reorderCategories(ordered) }
  }

  /** How many live items would be stranded by deleting [category]. */
  suspend fun countInCategory(category: Category): Int = repository.countInCategory(category.name)

  fun deleteCategory(category: Category, replacement: String) {
    viewModelScope.launch {
      if (!repository.deleteCategory(category, replacement)) return@launch
      _filterState.update {
        if (it.category == category.name) it.copy(category = replacement) else it
      }
    }
  }

  // Bulk add state lives here so it survives configuration changes (fold/unfold, rotation).
  val bulkEntries = mutableStateListOf<BulkEntry>()

  private var nextBulkId = 0L

  fun bulkAddPhoto(uri: Uri) {
    val category = defaultCategory
    val id = nextBulkId++
    bulkEntries.add(
      BulkEntry(id = id, photoUri = uri, name = autoName(null, category), category = category)
    )
    // Tag it in the background so the details step already has colours and auto names waiting.
    viewModelScope.launch {
      val colours = repository.detectColours(uri).ifEmpty { return@launch }
      updateBulkEntry(id) { it.copy(colours = colours).autoNamed() }
    }
    // And look for more than one item in it, so a photo of the whole floor can be offered up as
    // the several things it actually shows rather than filed as one.
    viewModelScope.launch {
      val segments = repository.segments(uri)
      if (segments.size >= 2) updateBulkEntry(id) { it.copy(segments = segments) }
    }
  }

  /** Entries are found by id, not position: a split rewrites the list under any work in flight. */
  private fun updateBulkEntry(id: Long, change: (BulkEntry) -> BulkEntry) {
    val index = bulkEntries.indexOfFirst { it.id == id }
    if (index >= 0) bulkEntries[index] = change(bulkEntries[index])
  }

  /** The next photo that looks like several items and has not been asked about yet. */
  val pendingSplitReview: BulkEntry?
    get() = bulkEntries.firstOrNull { it.segments.size >= 2 && !it.splitReviewed }

  /** "No, it is one item" — the photo is left exactly as it was, and is not asked about again. */
  fun bulkKeepAsOne(id: Long) {
    updateBulkEntry(id) { it.copy(splitReviewed = true) }
  }

  /**
   * Replaces one photo with one photo per box, each cut out and added in the original's place.
   *
   * [aspectRatio] is the shape the feed shows photos at, so the cut-outs are grown to it and the
   * items are not chopped at the sides on display. The children keep only the category: everything
   * else is theirs to work out, and the colours are detected on the crop rather than inherited from
   * a photo of six different things.
   */
  fun bulkSplitEntry(id: Long, regions: List<CropRect>, aspectRatio: Float) {
    val entry = bulkEntries.firstOrNull { it.id == id } ?: return
    if (regions.isEmpty()) return
    // Answered up front: cutting the photo up takes long enough that the review would otherwise
    // reopen on the very entry being split.
    updateBulkEntry(id) { it.copy(splitReviewed = true) }
    viewModelScope.launch {
      val uris = repository.splitPhoto(entry.photoUri, regions, aspectRatio)
      if (uris.isEmpty()) return@launch
      val index = bulkEntries.indexOfFirst { it.id == id }
      if (index < 0) return@launch
      val cutOuts =
        uris.map { uri ->
          BulkEntry(
            id = nextBulkId++,
            photoUri = uri,
            name = autoName(null, entry.category),
            category = entry.category,
            categoryChosen = entry.categoryChosen,
            splitReviewed = true,
          )
        }
      bulkEntries.removeAt(index)
      bulkEntries.addAll(index, cutOuts)
      for (cutOut in cutOuts) {
        launch {
          val colours = repository.detectColours(cutOut.photoUri).ifEmpty { return@launch }
          updateBulkEntry(cutOut.id) { it.copy(colours = colours).autoNamed() }
        }
      }
    }
  }

  fun bulkUpdateEntry(index: Int, entry: BulkEntry) {
    if (index in bulkEntries.indices) bulkEntries[index] = entry
  }

  /** A typed name is the user's; it stops following the colours and the category from here on. */
  fun bulkSetName(index: Int, name: String) {
    val entry = bulkEntries.getOrNull(index) ?: return
    bulkEntries[index] = entry.copy(name = name, nameIsAuto = false)
  }

  fun bulkSetColours(index: Int, colours: List<Colour>) {
    val entry = bulkEntries.getOrNull(index) ?: return
    bulkEntries[index] = entry.copy(colours = colours).autoNamed()
  }

  /**
   * Files one photo under [category] — and every photo the user has not filed themselves along
   * with it.
   *
   * A batch is almost always one kind of thing: twelve photos of shoes should not cost twelve
   * taps on the same chip. Entries the user has picked a category for are left alone, so a batch
   * that really is mixed still ends up right.
   */
  fun bulkSetCategory(index: Int, category: String) {
    if (index !in bulkEntries.indices) return
    bulkEntries.forEachIndexed { i, entry ->
      if (i != index && entry.categoryChosen) return@forEachIndexed
      bulkEntries[i] =
        entry.copy(category = category, categoryChosen = entry.categoryChosen || i == index)
          .autoNamed()
    }
  }

  /** Keeps the prefilled name in step with the entry, until the user types one of their own. */
  private fun BulkEntry.autoNamed(): BulkEntry =
    if (nameIsAuto) copy(name = autoName(colours.firstOrNull(), category)) else this

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
      entries.forEach {
        // An untouched name is saved blank, not as the text the field was showing: a blank name
        // is still computed at display time, so the item follows a later category rename.
        val name = if (it.nameIsAuto) "" else it.name.trim()
        repository.addItem(name, it.category, it.photoUri, it.colours, it.crop)
      }
    }
  }

  // Backup / restore. Results are surfaced as one-shot messages the UI shows in the snackbar.
  private val _messages = Channel<UiMessage>(Channel.BUFFERED)
  val messages = _messages.receiveAsFlow()

  private suspend fun send(message: UiMessage) {
    _messages.send(message)
  }

  fun exportBackup(uri: Uri) {
    viewModelScope.launch {
      val message =
        try {
          "Backed up ${backupManager.export(uri).itemsLabel()}"
        } catch (e: Exception) {
          "Backup failed"
        }
      send(UiMessage(message))
    }
  }

  fun importBackup(uri: Uri) {
    viewModelScope.launch {
      val message =
        try {
          "Restored ${backupManager.import(uri).itemsLabel()}"
        } catch (e: Exception) {
          "Couldn’t read that backup file"
        }
      send(UiMessage(message))
    }
  }

  private fun Int.itemsLabel() = "$this item${if (this == 1) "" else "s"}"
}
