package com.ahad.macat

import com.ahad.macat.data.Colour
import com.ahad.macat.data.FilterState
import com.ahad.macat.data.Item
import com.ahad.macat.data.SortOrder
import com.ahad.macat.data.colourCounts
import com.ahad.macat.data.coloursPresent
import com.ahad.macat.data.filterAndSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Filtering and sorting is the one place several independent axes meet, and it is a pure function
 * over a list, so it gets tested here rather than by scrolling a device.
 */
class FilteringTest {

  private var nextId = 1L

  private fun item(
    name: String = "",
    category: String = "Clothes",
    colour: Colour? = null,
    favourite: Boolean? = null,
    createdAt: Long = nextId * 1000L,
  ) = Item(
    id = nextId++,
    name = name,
    category = category,
    photoFileName = "photo.jpg",
    createdAt = createdAt,
    colour = colour,
    isFavourite = favourite,
  )

  // The catalogue every test below narrows.
  private val blackShoes = item(category = "Shoes", colour = Colour.BLACK, favourite = true)
  private val pinkShoes = item(category = "Shoes", colour = Colour.PINK)
  private val blackCoat = item(name = "Winter coat", category = "Outerwear", colour = Colour.BLACK)
  private val blueShirt = item(name = "Work shirt", category = "Clothes", colour = Colour.BLUE, favourite = true)
  private val catalogue = listOf(blackShoes, pinkShoes, blackCoat, blueShirt)

  @Test
  fun `an empty catalogue survives every axis at once`() {
    val everything =
      FilterState(
        category = "Shoes",
        colour = Colour.BLACK,
        favouritesOnly = true,
        query = "boot",
        sort = SortOrder.SHUFFLE,
      )
    assertEquals(emptyList<Item>(), emptyList<Item>().filterAndSort(everything))
    assertEquals(emptyList<Colour>(), emptyList<Item>().coloursPresent())
  }

  @Test
  fun `no filter keeps everything`() {
    assertEquals(catalogue.size, catalogue.filterAndSort(FilterState()).size)
  }

  @Test
  fun `category alone`() {
    val shoes = catalogue.filterAndSort(FilterState(category = "Shoes"))
    assertEquals(setOf(blackShoes.id, pinkShoes.id), shoes.map { it.id }.toSet())
  }

  @Test
  fun `colour alone`() {
    val black = catalogue.filterAndSort(FilterState(colour = Colour.BLACK))
    assertEquals(setOf(blackShoes.id, blackCoat.id), black.map { it.id }.toSet())
  }

  @Test
  fun `favourites alone, and a null flag is not a favourite`() {
    val favourites = catalogue.filterAndSort(FilterState(favouritesOnly = true))
    assertEquals(setOf(blackShoes.id, blueShirt.id), favourites.map { it.id }.toSet())
  }

  @Test
  fun `search alone matches the typed name`() {
    val hits = catalogue.filterAndSort(FilterState(query = "winter"))
    assertEquals(listOf(blackCoat.id), hits.map { it.id })
  }

  @Test
  fun `search matches the auto name of an item nobody named`() {
    // pinkShoes has no name of its own; it is only findable through "Pink shoes".
    val hits = catalogue.filterAndSort(FilterState(query = "pink"))
    assertEquals(listOf(pinkShoes.id), hits.map { it.id })
  }

  @Test
  fun `two axes combined narrow further than either alone`() {
    val blackShoesOnly =
      catalogue.filterAndSort(FilterState(category = "Shoes", colour = Colour.BLACK))
    assertEquals(listOf(blackShoes.id), blackShoesOnly.map { it.id })

    val favouriteBlack =
      catalogue.filterAndSort(FilterState(colour = Colour.BLACK, favouritesOnly = true))
    assertEquals(listOf(blackShoes.id), favouriteBlack.map { it.id })
  }

  @Test
  fun `two axes that cannot both hold give nothing`() {
    val impossible = catalogue.filterAndSort(FilterState(category = "Shoes", colour = Colour.BLUE))
    assertEquals(emptyList<Item>(), impossible)
  }

  @Test
  fun `newest and oldest are exact opposites`() {
    val newest = catalogue.filterAndSort(FilterState(sort = SortOrder.NEWEST)).map { it.id }
    val oldest = catalogue.filterAndSort(FilterState(sort = SortOrder.OLDEST)).map { it.id }
    assertEquals(newest, oldest.reversed())
    assertEquals(blueShirt.id, newest.first())
  }

  @Test
  fun `sorting by colour follows the palette and puts untagged items last`() {
    val untagged = item(colour = null)
    val sorted = (catalogue + untagged).filterAndSort(FilterState(sort = SortOrder.COLOUR))
    // The palette runs dark to light and then through the hues, so PINK comes before BLUE.
    // Untagged items have no place in it and collect at the end.
    assertEquals(
      listOf(Colour.BLACK, Colour.BLACK, Colour.PINK, Colour.BLUE, null),
      sorted.map { it.colour },
    )
  }

  @Test
  fun `the same shuffle seed always deals the same order`() {
    val state = FilterState(sort = SortOrder.SHUFFLE, shuffleSeed = 42L)
    assertEquals(
      catalogue.filterAndSort(state).map { it.id },
      catalogue.filterAndSort(state).map { it.id },
    )
  }

  @Test
  fun `a different shuffle seed can deal a different order, and never loses an item`() {
    val first = catalogue.filterAndSort(FilterState(sort = SortOrder.SHUFFLE, shuffleSeed = 1L))
    val second = catalogue.filterAndSort(FilterState(sort = SortOrder.SHUFFLE, shuffleSeed = 9L))
    assertEquals(catalogue.map { it.id }.toSet(), first.map { it.id }.toSet())
    assertEquals(catalogue.map { it.id }.toSet(), second.map { it.id }.toSet())
  }

  @Test
  fun `shuffling still respects the filters`() {
    val shoes =
      catalogue.filterAndSort(
        FilterState(category = "Shoes", sort = SortOrder.SHUFFLE, shuffleSeed = 7L)
      )
    assertEquals(setOf(blackShoes.id, pinkShoes.id), shoes.map { it.id }.toSet())
  }

  @Test
  fun `the swatch row offers only colours the catalogue holds, in palette order`() {
    assertEquals(listOf(Colour.BLACK, Colour.PINK, Colour.BLUE).sortedBy { it.ordinal },
      catalogue.coloursPresent())
    assertTrue(Colour.GOLD !in catalogue.coloursPresent())
  }

  @Test
  fun `the census counts each colour and skips the ones nobody owns`() {
    val counts = catalogue.colourCounts().toMap()
    assertEquals(2, counts[Colour.BLACK])
    assertEquals(1, counts[Colour.PINK])
    assertEquals(1, counts[Colour.BLUE])
    assertEquals(null, counts[Colour.GREEN])
  }

  @Test
  fun `a query is trimmed and case does not matter`() {
    assertEquals(
      catalogue.filterAndSort(FilterState(query = "WINTER")).map { it.id },
      catalogue.filterAndSort(FilterState(query = "  winter  ")).map { it.id },
    )
  }
}
