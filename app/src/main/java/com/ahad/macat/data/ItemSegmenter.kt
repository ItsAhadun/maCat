package com.ahad.macat.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds the separate items in one photo, so a picture of six pairs of shoes laid out on the floor
 * can become six catalogue entries instead of one.
 *
 * This is the same idea [Colour.tags] already works on — the border of the frame says what the
 * backdrop is, and everything far enough from that colour is item — kept as a *mask* rather than
 * tallied into a colour. Where the mask falls into separate lumps, the photo holds separate things.
 *
 * There is deliberately no model behind it. The app ships on F-Droid, whose scanner rejects the
 * prebuilt `.tflite` and `.so` blobs that every on-device classifier ships inside its AAR, so
 * detection here is pixels and arithmetic or it is nothing.
 */
class ItemSegmenter(private val context: Context) {

  /** Empty when the photo can't be read, or when it doesn't look like separate items. */
  suspend fun detect(uri: Uri): List<CropRect> =
    withContext(Dispatchers.IO) {
      val bitmap =
        runCatching { decodeUpright(context, uri, MASK_PX) }.getOrNull()
          ?: return@withContext emptyList()
      try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        segment(pixels, bitmap.width, bitmap.height)
      } finally {
        bitmap.recycle()
      }
    }

  private companion object {
    /** Longest side to decode to: quick to scan, fine enough to keep touching items apart. */
    const val MASK_PX = 256
  }
}

/** How far in from each edge is taken to be backdrop, as in [ColourDetector]. */
private const val BORDER_FRACTION = 0.12f

/**
 * How far a pixel must sit from the backdrop's own colour to count as part of an item — squared RGB
 * distance, so no square roots per pixel. The same threshold [Colour.tags] settled on against real
 * wardrobe photos, and for the same reason: half a wardrobe and half a floor are both grey, so the
 * backdrop has to be excluded pixel by pixel rather than by colour.
 */
private const val FOREGROUND_DISTANCE_SQ = 60 * 60

/** Share of the frame a lump must fill to be an item rather than a shadow, a grout line or a tag. */
private const val MIN_BLOB_SHARE = 0.01f

/**
 * Above this share of the frame reading as foreground, the border was not backdrop after all — one
 * item filling the frame, or a photo taken so close there is no floor in it. Nothing is offered,
 * because splitting on a mask that covers everything only ever cuts an item in half.
 */
private const val MAX_FOREGROUND_SHARE = 0.85f

/** Margin left around each item, as a share of its own size, so nothing is cropped flush. */
private const val PADDING_SHARE = 0.08f

/** Enough for a floor full of shoes; past this the photo is not really a catalogue shot. */
private const val MAX_SEGMENTS = 8

/**
 * The regions of a photo that look like separate items, largest first.
 *
 * Fewer than two means the photo is one item — or is unreadable as items at all — and the caller
 * should leave it alone. Takes pixels rather than a `Bitmap` so the whole thing is testable off a
 * device, as [Colour.tags] is.
 */
fun segment(pixels: IntArray, width: Int, height: Int): List<CropRect> {
  if (width <= 0 || height <= 0 || pixels.size < width * height) return emptyList()

  val mask = foregroundMask(pixels, width, height)
  val foreground = mask.count { it }
  if (foreground == 0 || foreground > width * height * MAX_FOREGROUND_SHARE) return emptyList()

  // Opening first drops the speckle that grout lines and shadow edges leave behind; closing then
  // joins what belongs together — the sole and the straps of one sandal arrive as separate lumps.
  val cleaned = mask.opened(width, height).closed(width, height)

  val minArea = width * height * MIN_BLOB_SHARE
  val blobs = cleaned.blobs(width, height).filter { it.area >= minArea }
  if (blobs.isEmpty()) return emptyList()

  return blobs
    .map { it.padded(width, height) }
    .merged()
    .sortedByDescending { it.area }
    .take(MAX_SEGMENTS)
    .map { it.toCropRect(width, height) }
}

/** One lump of foreground: the box around it, and how many pixels of it there were. */
private data class Blob(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
  val area: Int,
) {
  fun overlaps(other: Blob): Boolean =
    left <= other.right && other.left <= right && top <= other.bottom && other.top <= bottom

  fun union(other: Blob) =
    Blob(
      left = minOf(left, other.left),
      top = minOf(top, other.top),
      right = maxOf(right, other.right),
      bottom = maxOf(bottom, other.bottom),
      area = area + other.area,
    )

  fun padded(width: Int, height: Int): Blob {
    val padX = ((right - left + 1) * PADDING_SHARE).toInt().coerceAtLeast(1)
    val padY = ((bottom - top + 1) * PADDING_SHARE).toInt().coerceAtLeast(1)
    return copy(
      left = (left - padX).coerceAtLeast(0),
      top = (top - padY).coerceAtLeast(0),
      right = (right + padX).coerceAtMost(width - 1),
      bottom = (bottom + padY).coerceAtMost(height - 1),
    )
  }

  fun toCropRect(width: Int, height: Int) =
    CropRect(
      left = left.toFloat() / width,
      top = top.toFloat() / height,
      right = (right + 1).toFloat() / width,
      bottom = (bottom + 1).toFloat() / height,
    )
}

/** True where the pixel is far enough from the backdrop's colour to be part of something. */
private fun foregroundMask(pixels: IntArray, width: Int, height: Int): BooleanArray {
  val (backdropR, backdropG, backdropB) = backdropColour(pixels, width, height)
  return BooleanArray(width * height) { i ->
    val pixel = pixels[i]
    val dr = ((pixel shr 16) and 0xFF) - backdropR
    val dg = ((pixel shr 8) and 0xFF) - backdropG
    val db = (pixel and 0xFF) - backdropB
    dr * dr + dg * dg + db * db > FOREGROUND_DISTANCE_SQ
  }
}

/**
 * The backdrop's colour, per channel, from the bands down all four edges. Median rather than mean,
 * for the same reason [Colour.tags] uses one: grout lines and shadows drag a mean around, and an
 * item overhanging one edge should not get to redefine what the floor is.
 */
private fun backdropColour(pixels: IntArray, width: Int, height: Int): Triple<Int, Int, Int> {
  val bandWidth = (width * BORDER_FRACTION).toInt().coerceAtLeast(1)
  val bandHeight = (height * BORDER_FRACTION).toInt().coerceAtLeast(1)
  val border = ArrayList<Int>()
  for (y in 0 until height) {
    val edgeRow = y < bandHeight || y >= height - bandHeight
    for (x in 0 until width) {
      if (edgeRow || x < bandWidth || x >= width - bandWidth) border.add(pixels[y * width + x])
    }
  }
  if (border.isEmpty()) return Triple(0, 0, 0)
  fun median(shift: Int): Int {
    val channel = IntArray(border.size) { (border[it] shr shift) and 0xFF }
    channel.sort()
    return channel[channel.size / 2]
  }
  return Triple(median(16), median(8), median(0))
}

/** Erode then dilate: anything thinner than the brush disappears, the rest keeps its size. */
private fun BooleanArray.opened(width: Int, height: Int): BooleanArray =
  eroded(width, height).dilated(width, height)

/**
 * Dilate then erode, twice over: gaps narrower than the brush are filled in, the rest keeps its
 * size. Two passes because the gap between a sandal's sole and its strap is wider than one pixel at
 * this scale, and leaving it open files one sandal as two items.
 */
private fun BooleanArray.closed(width: Int, height: Int): BooleanArray =
  dilated(width, height)
    .dilated(width, height)
    .eroded(width, height)
    .eroded(width, height)

/** True only where all four neighbours are. Off the frame counts as true: items run off edges. */
private fun BooleanArray.eroded(width: Int, height: Int): BooleanArray {
  val out = BooleanArray(size)
  for (y in 0 until height) {
    for (x in 0 until width) {
      val i = y * width + x
      if (!this[i]) continue
      out[i] =
        (x == 0 || this[i - 1]) &&
          (x == width - 1 || this[i + 1]) &&
          (y == 0 || this[i - width]) &&
          (y == height - 1 || this[i + width])
    }
  }
  return out
}

/** True wherever any of the four neighbours is. */
private fun BooleanArray.dilated(width: Int, height: Int): BooleanArray {
  val out = BooleanArray(size)
  for (y in 0 until height) {
    for (x in 0 until width) {
      val i = y * width + x
      if (this[i]) {
        out[i] = true
        continue
      }
      out[i] =
        (x > 0 && this[i - 1]) ||
          (x < width - 1 && this[i + 1]) ||
          (y > 0 && this[i - width]) ||
          (y < height - 1 && this[i + width])
    }
  }
  return out
}

/**
 * Every connected lump of true, 4-connected. Flood filled from an explicit stack rather than by
 * recursion — a frame this size would be tens of thousands of calls deep in the worst case.
 */
private fun BooleanArray.blobs(width: Int, height: Int): List<Blob> {
  val seen = BooleanArray(size)
  val stack = IntArray(size)
  val found = mutableListOf<Blob>()

  for (start in indices) {
    if (!this[start] || seen[start]) continue
    var depth = 0
    stack[depth++] = start
    seen[start] = true
    var minX = width
    var maxX = -1
    var minY = height
    var maxY = -1
    var area = 0

    while (depth > 0) {
      val i = stack[--depth]
      val x = i % width
      val y = i / width
      area++
      if (x < minX) minX = x
      if (x > maxX) maxX = x
      if (y < minY) minY = y
      if (y > maxY) maxY = y

      if (x > 0 && this[i - 1] && !seen[i - 1]) {
        seen[i - 1] = true
        stack[depth++] = i - 1
      }
      if (x < width - 1 && this[i + 1] && !seen[i + 1]) {
        seen[i + 1] = true
        stack[depth++] = i + 1
      }
      if (y > 0 && this[i - width] && !seen[i - width]) {
        seen[i - width] = true
        stack[depth++] = i - width
      }
      if (y < height - 1 && this[i + width] && !seen[i + width]) {
        seen[i + width] = true
        stack[depth++] = i + width
      }
    }
    found.add(Blob(minX, minY, maxX, maxY, area))
  }
  return found
}

/**
 * Folds boxes that overlap into one, until none do. One item routinely survives as two lumps whose
 * boxes sit inside one another — a shoe and the buckle photographed just off it — and two boxes
 * over one shoe is a worse answer than one slightly loose box.
 */
private fun List<Blob>.merged(): List<Blob> {
  var current = this
  while (true) {
    val next = mutableListOf<Blob>()
    var mergedAny = false
    for (blob in current) {
      val hit = next.indexOfFirst { it.overlaps(blob) }
      if (hit >= 0) {
        next[hit] = next[hit].union(blob)
        mergedAny = true
      } else {
        next.add(blob)
      }
    }
    current = next
    if (!mergedAny) return current
  }
}
