package com.ahad.macat.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Guesses the colours of a photo: decode it small, then read two regions of it separately — the
 * middle, which is the item, and the frame around it, which is whatever the item was put down on.
 *
 * Reading the backdrop is the point. Cropping in alone still leaves floor in every corner around
 * a shoe, and that floor used to end up as the item's colour; knowing what the floor *is* lets
 * those pixels be dropped before anything votes. See [Colour.tags].
 */
class ColourDetector(private val context: Context) {

  /** Empty when the photo can't be read; the caller just goes without tags. */
  suspend fun detect(uri: Uri): List<Colour> =
    withContext(Dispatchers.IO) {
      val bitmap =
        runCatching { decodeUpright(context, uri, SAMPLE_PX) }.getOrNull()
          ?: return@withContext emptyList()
      try {
        Colour.tags(item = centrePixels(bitmap), backdrop = borderPixels(bitmap))
      } finally {
        bitmap.recycle()
      }
    }

  /** The middle of the frame: the item, plus as little of what it is lying on as possible. */
  private fun centrePixels(bitmap: Bitmap): IntArray {
    val width = (bitmap.width * CENTRE_FRACTION).toInt().coerceAtLeast(1)
    val height = (bitmap.height * CENTRE_FRACTION).toInt().coerceAtLeast(1)
    val left = (bitmap.width - width) / 2
    val top = (bitmap.height - height) / 2
    return IntArray(width * height).also {
      bitmap.getPixels(it, 0, width, left, top, width, height)
    }
  }

  /** The bands down all four edges: near enough the backdrop, whatever the item's shape. */
  private fun borderPixels(bitmap: Bitmap): IntArray {
    val bandWidth = (bitmap.width * BORDER_FRACTION).toInt().coerceAtLeast(1)
    val bandHeight = (bitmap.height * BORDER_FRACTION).toInt().coerceAtLeast(1)
    val top = band(bitmap, 0, 0, bitmap.width, bandHeight)
    val bottom = band(bitmap, 0, bitmap.height - bandHeight, bitmap.width, bandHeight)
    val left = band(bitmap, 0, 0, bandWidth, bitmap.height)
    val right = band(bitmap, bitmap.width - bandWidth, 0, bandWidth, bitmap.height)
    return top + bottom + left + right
  }

  private fun band(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): IntArray =
    IntArray(width * height).also { bitmap.getPixels(it, 0, width, x, y, width, height) }

  private companion object {
    /** Roughly the longest side to decode to — plenty for a colour, cheap enough for bulk add. */
    const val SAMPLE_PX = 128

    /** How much of the frame counts as "the item". Tight, so the backdrop barely gets a vote. */
    const val CENTRE_FRACTION = 0.5f

    /** How far in from each edge counts as "not the item". */
    const val BORDER_FRACTION = 0.12f
  }
}
