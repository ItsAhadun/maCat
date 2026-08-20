package com.ahad.macat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Guesses the colour tag of a photo: decode it small, then look only at the middle of the frame —
 * where the item is, rather than the floor it is lying on — and take the dominant colour there.
 */
class ColourDetector(private val context: Context) {

  /** Null when the photo can't be read; the caller just goes without a tag. */
  suspend fun detect(uri: Uri): Colour? =
    withContext(Dispatchers.IO) {
      val bitmap = runCatching { decodeSmall(uri) }.getOrNull() ?: return@withContext null
      try {
        Colour.dominant(centrePixels(bitmap))
      } finally {
        bitmap.recycle()
      }
    }

  private fun decodeSmall(uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
    if (longestSide <= 0) return null
    val options =
      BitmapFactory.Options().apply {
        inSampleSize = Integer.highestOneBit(longestSide / SAMPLE_PX).coerceAtLeast(1)
      }
    return context.contentResolver.openInputStream(uri)?.use {
      BitmapFactory.decodeStream(it, null, options)
    }
  }

  private fun centrePixels(bitmap: Bitmap): IntArray {
    val width = (bitmap.width * CENTRE_FRACTION).toInt().coerceAtLeast(1)
    val height = (bitmap.height * CENTRE_FRACTION).toInt().coerceAtLeast(1)
    val left = (bitmap.width - width) / 2
    val top = (bitmap.height - height) / 2
    return IntArray(width * height).also {
      bitmap.getPixels(it, 0, width, left, top, width, height)
    }
  }

  private companion object {
    /** Roughly the longest side to decode to — plenty for a colour, cheap enough for bulk add. */
    const val SAMPLE_PX = 128

    /** How much of the frame counts as "the item". */
    const val CENTRE_FRACTION = 0.6f
  }
}
