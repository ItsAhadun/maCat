package com.ahad.macat

import com.ahad.macat.data.CropRect
import com.ahad.macat.data.MIN_BOX
import com.ahad.macat.data.expandedToAspect
import com.ahad.macat.data.movedBy
import com.ahad.macat.data.resizedBy
import com.ahad.macat.data.segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The segmenter runs on plain pixels rather than a Bitmap, so the whole of it is exercised here
 * instead of on a device — the same trick [ColourTest] plays on the colour tagger.
 */
class SegmenterTest {

  private val floor = rgb(200, 198, 195)
  private val shoe = rgb(40, 40, 45)

  /** A [width] x [height] photo of [floor], with [items] painted onto it in item colour. */
  private fun photo(
    width: Int,
    height: Int,
    vararg items: IntArray,
  ): IntArray {
    val pixels = IntArray(width * height) { floor }
    for (item in items) {
      val (left, top, right, bottom) = item
      for (y in top until bottom) {
        for (x in left until right) pixels[y * width + x] = shoe
      }
    }
    return pixels
  }

  @Test
  fun `two things on a floor come back as two boxes`() {
    val pixels = photo(100, 100, intArrayOf(10, 30, 35, 70), intArrayOf(60, 30, 88, 70))
    val boxes = segment(pixels, 100, 100)

    assertEquals(2, boxes.size)
    // One down each side, and neither box swallows the other's half of the photo.
    val sorted = boxes.sortedBy { it.left }
    assertTrue(sorted[0].right < 0.55f)
    assertTrue(sorted[1].left > 0.45f)
  }

  @Test
  fun `one thing on a floor is not a split`() {
    val boxes = segment(photo(100, 100, intArrayOf(30, 30, 70, 70)), 100, 100)
    assertEquals(1, boxes.size)
  }

  @Test
  fun `an empty floor holds nothing`() {
    assertEquals(emptyList<CropRect>(), segment(photo(100, 100), 100, 100))
  }

  @Test
  fun `speckle is not an item`() {
    // Single stray pixels — grout, a shadow edge — are below the minimum share and are opened away.
    val pixels = IntArray(100 * 100) { floor }
    pixels[15 * 100 + 15] = shoe
    pixels[80 * 100 + 20] = shoe
    pixels[44 * 100 + 91] = shoe
    assertEquals(emptyList<CropRect>(), segment(pixels, 100, 100))
  }

  @Test
  fun `a photo filling with item is left alone rather than cut in half`() {
    // Nothing that reads as backdrop: the border estimate is the item itself, so no split is safe.
    val boxes = segment(IntArray(100 * 100) { shoe }, 100, 100)
    assertEquals(emptyList<CropRect>(), boxes)
  }

  @Test
  fun `the biggest thing is offered first`() {
    val pixels = photo(120, 100, intArrayOf(10, 20, 30, 45), intArrayOf(55, 20, 110, 80))
    val boxes = segment(pixels, 120, 100)

    assertEquals(2, boxes.size)
    assertTrue("biggest first", boxes[0].width * boxes[0].height > boxes[1].width * boxes[1].height)
  }

  @Test
  fun `boxes stay inside the photo`() {
    // Items running off two edges: the padding must not push a box outside 0..1.
    val pixels = photo(100, 100, intArrayOf(0, 0, 30, 30), intArrayOf(70, 70, 100, 100))
    for (box in segment(pixels, 100, 100)) {
      assertTrue(box.left >= 0f && box.top >= 0f)
      assertTrue(box.right <= 1f && box.bottom <= 1f)
    }
  }

  @Test
  fun `a wide box grows taller to reach a tall shape`() {
    // A sandal lying sideways, 600x100 in a photo with room above and below it, shown in a 9:19.5
    // feed slot.
    val tight = CropRect(0.2f, 0.45f, 0.8f, 0.4833f)
    val grown = tight.expandedToAspect(9f / 19.5f, 1000, 3000)

    assertTrue("nothing drawn is cut into", grown.left <= tight.left && grown.right >= tight.right)
    assertTrue(grown.top <= tight.top && grown.bottom >= tight.bottom)
    assertEquals(9f / 19.5f, (grown.width * 1000) / (grown.height * 3000), 0.01f)
  }

  @Test
  fun `a box too wide for the photo to frame grows as far as the photo allows`() {
    // 600x100 in a square photo cannot reach 9:19.5 — 1300px of height is not there to take. It
    // takes all the height there is rather than cutting the box back down to shape.
    val tight = CropRect(0.2f, 0.45f, 0.8f, 0.55f)
    val grown = tight.expandedToAspect(9f / 19.5f, 1000, 1000)

    assertEquals(0f, grown.top, 0.001f)
    assertEquals(1f, grown.bottom, 0.001f)
    assertTrue(grown.left <= tight.left && grown.right >= tight.right)
  }

  @Test
  fun `growing at the edge slides inwards instead of overflowing`() {
    val corner = CropRect(0f, 0f, 0.3f, 0.3f)
    val grown = corner.expandedToAspect(2f, 1000, 1000)

    assertTrue(grown.left >= 0f && grown.top >= 0f)
    assertTrue(grown.right <= 1f && grown.bottom <= 1f)
    assertTrue("still covers what it was given", grown.right >= corner.right)
  }

  @Test
  fun `a photo too narrow to reach the shape gives all it has`() {
    val box = CropRect(0.1f, 0.1f, 0.9f, 0.2f)
    val grown = box.expandedToAspect(10f, 100, 1000)

    assertEquals(0f, grown.left, 0.001f)
    assertEquals(1f, grown.right, 0.001f)
  }

  @Test
  fun `dragging a box keeps its size and stops at the edge`() {
    val box = CropRect(0.4f, 0.4f, 0.6f, 0.6f)

    val moved = box.movedBy(0.1f, -0.2f)
    assertEquals(0.5f, moved.left, 0.001f)
    assertEquals(0.2f, moved.top, 0.001f)
    assertEquals(box.width, moved.width, 0.001f)
    assertEquals(box.height, moved.height, 0.001f)

    // Shoved well past the corner: it stops flush, still the same size, never inverted.
    val shoved = box.movedBy(5f, 5f)
    assertEquals(1f, shoved.right, 0.001f)
    assertEquals(1f, shoved.bottom, 0.001f)
    assertEquals(box.width, shoved.width, 0.001f)
  }

  @Test
  fun `a corner drag moves only that corner`() {
    val box = CropRect(0.4f, 0.4f, 0.6f, 0.6f)

    val topLeft = box.resizedBy(dLeft = -0.1f, dTop = -0.1f)
    assertEquals(0.3f, topLeft.left, 0.001f)
    assertEquals(0.3f, topLeft.top, 0.001f)
    assertEquals(box.right, topLeft.right, 0.001f)
    assertEquals(box.bottom, topLeft.bottom, 0.001f)

    val bottomRight = box.resizedBy(dRight = 0.15f, dBottom = 0.15f)
    assertEquals(box.left, bottomRight.left, 0.001f)
    assertEquals(0.75f, bottomRight.right, 0.001f)
  }

  @Test
  fun `a box cannot be shrunk past grabbing`() {
    val box = CropRect(0.4f, 0.4f, 0.6f, 0.6f)
    val crushed = box.resizedBy(dLeft = 0.5f, dTop = 0.5f)

    assertTrue(crushed.width >= MIN_BOX - 0.001f)
    assertTrue(crushed.height >= MIN_BOX - 0.001f)
    assertTrue("never inverted", crushed.right > crushed.left && crushed.bottom > crushed.top)
  }

  @Test
  fun `a box already thinner than the minimum can still be dragged`() {
    // A sliver of a strap comes back this narrow, and a clamp whose bounds cross throws.
    val sliver = CropRect(0f, 0.2f, 0.02f, 0.8f)
    val dragged = sliver.resizedBy(dLeft = 0.01f, dRight = 0.2f)

    assertTrue(dragged.right > dragged.left)
    assertTrue(dragged.left >= 0f && dragged.right <= 1f)
  }

  private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
