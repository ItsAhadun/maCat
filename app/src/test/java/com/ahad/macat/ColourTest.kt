package com.ahad.macat

import com.ahad.macat.data.Colour
import com.ahad.macat.data.Item
import com.ahad.macat.data.autoName
import org.junit.Assert.assertEquals
import org.junit.Test

class ColourTest {

  @Test
  fun `classifies wardrobe swatches`() {
    assertEquals(Colour.BLACK, Colour.classify(26, 26, 26))
    assertEquals(Colour.WHITE, Colour.classify(245, 245, 245))
    assertEquals(Colour.GREY, Colour.classify(158, 158, 158))
    assertEquals(Colour.BEIGE, Colour.classify(217, 199, 167))
    assertEquals(Colour.BROWN, Colour.classify(123, 75, 42))
    assertEquals(Colour.RED, Colour.classify(211, 47, 47))
    assertEquals(Colour.PINK, Colour.classify(236, 127, 166))
    assertEquals(Colour.ORANGE, Colour.classify(239, 124, 27))
    assertEquals(Colour.YELLOW, Colour.classify(242, 194, 0))
    assertEquals(Colour.GREEN, Colour.classify(60, 154, 70))
    assertEquals(Colour.BLUE, Colour.classify(47, 111, 208))
    assertEquals(Colour.PURPLE, Colour.classify(132, 73, 196))
  }

  @Test
  fun `separates the colours most easily confused`() {
    // Pale pink is not red, deep red is not pink.
    assertEquals(Colour.PINK, Colour.classify(255, 192, 203))
    assertEquals(Colour.RED, Colour.classify(160, 20, 20))
    // Dark orange is brown; bright brown-ish orange is not.
    assertEquals(Colour.BROWN, Colour.classify(100, 60, 20))
    assertEquals(Colour.ORANGE, Colour.classify(230, 140, 40))
    // Denim is blue even though it is washed out; navy is blue even though it is dark.
    assertEquals(Colour.BLUE, Colour.classify(90, 107, 133))
    assertEquals(Colour.BLUE, Colour.classify(27, 42, 74))
  }

  @Test
  fun `the backdrop is struck off even when it fills most of the crop`() {
    // A pink shoe on a cream floor: the floor still wins the crop by pixel count, but the border
    // says what the floor is, so the shoe's own colour is the only tag left.
    val cream = rgb(230, 220, 200)
    val crop = IntArray(100) { if (it < 30) rgb(236, 127, 166) else cream }
    assertEquals(listOf(Colour.PINK), Colour.tags(crop, IntArray(40) { cream }))
  }

  @Test
  fun `an item that really is the backdrop colour keeps it`() {
    // A black shoe on a dark floor: striking off black would leave the item with no colour at
    // all, which is the worse answer, so the plain winner stands.
    val dark = rgb(20, 20, 22)
    val crop = IntArray(100) { if (it < 5) rgb(211, 47, 47) else dark }
    assertEquals(listOf(Colour.BLACK), Colour.tags(crop, IntArray(40) { dark }))
  }

  @Test
  fun `a two-tone item comes back with both colours, most of it first`() {
    // A red-and-white trainer on a grey floor.
    val crop =
      IntArray(100) {
        when {
          it < 55 -> rgb(245, 245, 245)
          it < 90 -> rgb(211, 47, 47)
          else -> rgb(158, 158, 158)
        }
      }
    assertEquals(
      listOf(Colour.WHITE, Colour.RED),
      Colour.tags(crop, IntArray(40) { rgb(158, 158, 158) }),
    )
  }

  @Test
  fun `a colour too small a part of the item is not one of its colours`() {
    // A blue shirt with a small yellow logo: the logo is not what the shirt is.
    val crop = IntArray(100) { if (it < 8) rgb(242, 194, 0) else rgb(47, 111, 208) }
    assertEquals(listOf(Colour.BLUE), Colour.tags(crop, IntArray(40) { rgb(245, 245, 245) }))
  }

  @Test
  fun `a cool grey floor tile is grey, not blue`() {
    // Found on real photos: grey tile, black patent and silver glitter were all coming back BLUE.
    // A ratio-based saturation test calls (120, 124, 132) 9% saturated and lands it in the blue
    // band; twelve points of channel spread is not a colour.
    assertEquals(Colour.GREY, Colour.classify(120, 124, 132))
    assertEquals(Colour.GREY, Colour.classify(150, 152, 160))
    // A colour that really is blue, however washed out, still has to survive that.
    assertEquals(Colour.BLUE, Colour.classify(90, 107, 133))
  }

  @Test
  fun `an item keeps its own shade when the backdrop shares its colour family`() {
    // A white shoe on a grey floor. Both are achromatic, so striking the backdrop off by *colour*
    // would leave the shoe with nothing but noise — this is why the backdrop is removed pixel by
    // pixel instead. The floor holds most of the crop and must still not win.
    val floor = rgb(140, 140, 145)
    val crop = IntArray(100) { if (it < 40) rgb(245, 245, 245) else floor }
    assertEquals(listOf(Colour.WHITE), Colour.tags(crop, IntArray(40) { floor }))
  }

  @Test
  fun `grey has to earn a tag, where a colour only has to show up`() {
    // Grey, white and black are where every washed-out pixel lands, so they clear a higher bar.
    // Here grey covers *more* of the item than green does and is still not one of its colours,
    // because a third of an item being grey is shadow, while a fifth being green is a green item.
    val backdrop = IntArray(40) { rgb(230, 220, 200) }
    val shadowed =
      IntArray(100) {
        when {
          it < 20 -> rgb(60, 154, 70)
          it < 50 -> rgb(120, 122, 128)
          else -> rgb(47, 111, 208)
        }
      }
    assertEquals(listOf(Colour.BLUE, Colour.GREEN), Colour.tags(shadowed, backdrop))
    // An item that really is grey still gets it, which is the point of a bar rather than a ban.
    val mostlyGrey = IntArray(100) { if (it < 10) rgb(60, 154, 70) else rgb(120, 122, 128) }
    assertEquals(listOf(Colour.GREY), Colour.tags(mostlyGrey, backdrop))
  }

  @Test
  fun `no pixels means no guess`() {
    assertEquals(emptyList<Colour>(), Colour.tags(IntArray(0), IntArray(0)))
  }

  @Test
  fun `auto name pairs the colour with the category`() {
    assertEquals("Pink shoes", autoName(Colour.PINK, "Shoes"))
    assertEquals("Gold jewellery", autoName(Colour.GOLD, "Jewellery"))
    // Nothing detected: the category alone still beats an empty label.
    assertEquals("Clothes", autoName(null, "Clothes"))
  }

  @Test
  fun `display name falls back to the auto name only while the name is blank`() {
    val item =
      Item(
        name = "",
        category = "Shoes",
        photoFileName = "a.jpg",
        colours = listOf(Colour.PINK, Colour.WHITE),
      )
    // The auto name uses the colour most of the item is, not every colour it carries.
    assertEquals("Pink shoes", item.displayName)
    assertEquals("Wedding heels", item.copy(name = "Wedding heels").displayName)
  }

  private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
