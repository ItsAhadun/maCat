package com.ahad.macat.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which filter controls the feed and grid put on screen. All on by default: this exists to let
 * someone who never sorts or never favourites reclaim the space, not to hide things by surprise.
 *
 * Hiding a control does not silently keep its selection — see `CatalogueViewModel`, which clears
 * the matching axis when one is switched off. A hidden filter that still narrowed the feed would
 * be indistinguishable from a bug.
 */
data class FilterVisibility(
  val categories: Boolean = true,
  val colours: Boolean = true,
  val favourites: Boolean = true,
  val sort: Boolean = true,
  val search: Boolean = true,
)

/**
 * The handful of on/off preferences the app keeps. Plain [android.content.SharedPreferences]:
 * five booleans do not justify a DataStore dependency, and a smaller dependency list is one less
 * thing for F-Droid to review.
 */
class SettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("macat_settings", Context.MODE_PRIVATE)

  private val _filterVisibility = MutableStateFlow(read())
  val filterVisibility: StateFlow<FilterVisibility> = _filterVisibility.asStateFlow()

  fun setFilterVisibility(visibility: FilterVisibility) {
    prefs
      .edit()
      .putBoolean(KEY_CATEGORIES, visibility.categories)
      .putBoolean(KEY_COLOURS, visibility.colours)
      .putBoolean(KEY_FAVOURITES, visibility.favourites)
      .putBoolean(KEY_SORT, visibility.sort)
      .putBoolean(KEY_SEARCH, visibility.search)
      .apply()
    _filterVisibility.value = visibility
  }

  private fun read() =
    FilterVisibility(
      categories = prefs.getBoolean(KEY_CATEGORIES, true),
      colours = prefs.getBoolean(KEY_COLOURS, true),
      favourites = prefs.getBoolean(KEY_FAVOURITES, true),
      sort = prefs.getBoolean(KEY_SORT, true),
      search = prefs.getBoolean(KEY_SEARCH, true),
    )

  private companion object {
    const val KEY_CATEGORIES = "show_categories"
    const val KEY_COLOURS = "show_colours"
    const val KEY_FAVOURITES = "show_favourites"
    const val KEY_SORT = "show_sort"
    const val KEY_SEARCH = "show_search"
  }
}
