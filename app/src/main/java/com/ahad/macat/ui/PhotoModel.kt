package com.ahad.macat.ui

import android.content.Context
import android.graphics.Bitmap
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import com.ahad.macat.data.CropRect
import kotlin.math.roundToInt

/** Ceiling on the decode size behind a zoomed-in framing, to keep one photo from eating the heap. */
private const val MAX_DECODE_PX = 2560

/**
 * What to hand `AsyncImage` for an item photo, so every screen shows the framing the user chose.
 *
 * Without a framing rect this is just the photo itself — no bitmap copy, as before. With one,
 * the photo is decoded large enough that the kept region still has about [basePx] pixels to show,
 * then cropped to that region; the original file is never touched, so the framing stays editable.
 */
fun croppedPhotoModel(context: Context, photo: Any, crop: CropRect?, basePx: Int): Any {
  if (crop == null) return photo
  return ImageRequest.Builder(context)
    .data(photo)
    .size(decodeSizeFor(basePx, crop))
    .transformations(CropTransformation(crop))
    .build()
}

/**
 * How big to decode a photo so that the [crop] region still has about [basePx] pixels to show.
 * Capped to keep one photo from eating the heap — but never below [basePx], which on a tall phone
 * can be larger than the cap on its own.
 */
internal fun decodeSizeFor(basePx: Int, crop: CropRect): Int =
  (basePx / minOf(crop.width, crop.height))
    .roundToInt()
    .coerceAtMost(maxOf(MAX_DECODE_PX, basePx))

private class CropTransformation(private val crop: CropRect) : Transformation() {
  override val cacheKey = "crop(${crop.left},${crop.top},${crop.right},${crop.bottom})"

  override suspend fun transform(input: Bitmap, size: Size): Bitmap {
    val left = (crop.left * input.width).roundToInt().coerceIn(0, input.width - 1)
    val top = (crop.top * input.height).roundToInt().coerceIn(0, input.height - 1)
    val width = (crop.width * input.width).roundToInt().coerceIn(1, input.width - left)
    val height = (crop.height * input.height).roundToInt().coerceIn(1, input.height - top)
    return Bitmap.createBitmap(input, left, top, width, height)
  }
}
