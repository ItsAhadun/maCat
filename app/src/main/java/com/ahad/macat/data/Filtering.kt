package com.ahad.macat.data

import kotlin.random.Random

enum class SortOrder(val label: String) {
  NEWEST("Newest"),
  OLDEST("Oldest"),
  COLOUR("By colour"),
  SHUFFLE("Shuffle"),
}

/**
 * Every axis the catalogue can be narrowed and ordered by, in one value.
 *
 * One object rather than a flow per axis: the views combine all of them at once, and the number
 * of axes has already outgrown what `combine` takes without ceremony.
 */
data class FilterState(
  val category: String? = null,
  val colour: Colour? = null,
  val favouritesOnly: Boolean = false,
  val query: String = "",
  val sort: SortOrder = SortOrder.NEWEST,
  /**
   * Fixes the order [SortOrder.SHUFFLE] produces. It has to live in the state rather than being
   * drawn inside the sort, or every recomposition would deal a new order and the feed would
   * rearrange itself under a finger that is mid-scroll. A new seed is dealt when — and only
   * when — the user asks to shuffle again.
   */
  val shuffleSeed: Long = 0L,
) {
  /** Whether anything is currently narrowing the catalogue, for "no matches" copy. */
  val isNarrowed: Boolean
    get() = category != null || colour != null || favouritesOnly || query.isNotBlank()
}

/**
 * The one place the catalogue is narrowed and ordered. A pure function over a list so it can be
 * unit-tested without a database, a device, or a composition — see `FilteringTest`.
 *
 * Ordering is computed here rather than leaned on from the DAO's `ORDER BY` for the same reason:
 * a function whose result depends on the order it was handed cannot be tested on its own.
 */
fun List<Item>.filterAndSort(state: FilterState): List<Item> {
  val query = state.query.trim()
  val matches =
    filter { item ->
      (state.category == null || item.category == state.category) &&
        (state.colour == null || state.colour in item.colours.orEmpty()) &&
        (!state.favouritesOnly || item.favourite) &&
        (query.isEmpty() || item.displayName.contains(query, ignoreCase = true))
    }
  return when (state.sort) {
    SortOrder.NEWEST ->
      matches.sortedWith(compareByDescending<Item> { it.createdAt }.thenByDescending { it.id })
    SortOrder.OLDEST -> matches.sortedWith(compareBy<Item> { it.createdAt }.thenBy { it.id })
    // Palette order, which runs dark to light and then through the hues, so the result reads as
    // a gradient rather than an alphabet. An item sorts under the colour most of it is — the
    // first of its tags. Untagged items collect at the end.
    SortOrder.COLOUR ->
      matches.sortedWith(
        compareBy<Item> { it.colours?.firstOrNull()?.ordinal ?: Int.MAX_VALUE }
          .thenByDescending { it.createdAt }
      )
    SortOrder.SHUFFLE -> matches.shuffled(Random(state.shuffleSeed))
  }
}

/** The colours the catalogue actually contains, in palette order — the swatch row's contents. */
fun List<Item>.coloursPresent(): List<Colour> {
  val present = flatMapTo(HashSet()) { it.colours.orEmpty() }
  return Colour.entries.filter { it in present }
}

/**
 * How many items carry each colour, for the census bar. Palette order, empties dropped.
 *
 * An item with more than one colour is counted under each of them, so these add up to more than
 * the size of the catalogue — the census counts tags, not items, and says so.
 */
fun List<Item>.colourCounts(): List<Pair<Colour, Int>> {
  val counts = flatMap { it.colours.orEmpty() }.groupingBy { it }.eachCount()
  return Colour.entries.mapNotNull { colour ->
    val count = counts[colour] ?: return@mapNotNull null
    colour to count
  }
}
