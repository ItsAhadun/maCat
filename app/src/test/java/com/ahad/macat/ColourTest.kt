package com.ahad.macat

import com.ahad.macat.data.Colour
import com.ahad.macat.data.Item
import com.ahad.macat.data.autoName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  fun `a real colour outvotes a pale background`() {
    // A pink item on a cream floor: a quarter of the frame is enough.
    val pixels = IntArray(100) { if (it < 30) rgb(236, 127, 166) else rgb(230, 220, 200) }
    assertEquals(Colour.PINK, Colour.dominant(pixels))
  }

  @Test
  fun `a plain majority wins when nothing chromatic stands out`() {
    // A black shoe on a dark floor, with a few stray colourful pixels.
    val pixels = IntArray(100) { if (it < 5) rgb(211, 47, 47) else rgb(20, 20, 22) }
    assertEquals(Colour.BLACK, Colour.dominant(pixels))
  }

  @Test
  fun `no pixels means no guess`() {
    assertNull(Colour.dominant(IntArray(0)))
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
    val item = Item(name = "", category = "Shoes", photoFileName = "a.jpg", colour = Colour.PINK)
    assertEquals("Pink shoes", item.displayName)
    assertEquals("Wedding heels", item.copy(name = "Wedding heels").displayName)
  }

  private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
