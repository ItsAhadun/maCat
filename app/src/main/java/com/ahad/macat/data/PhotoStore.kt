package com.ahad.macat.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decode budget for a photo about to be cut up, matching what the app is ever willing to show. */
private const val CROP_DECODE_PX = 2560

/** JPEG quality for a baked crop. High enough that re-encoding once is not visible. */
private const val CROP_QUALITY = 92

/** Owns the photo files: camera capture targets in cache, imported photos in internal storage. */
class PhotoStore(private val context: Context) {
  private val photosDir: File
    get() = File(context.filesDir, "photos").apply { mkdirs() }

  private val capturesDir: File
    get() = File(context.cacheDir, "captures").apply { mkdirs() }

  /** A fresh cache file for the in-app camera to write a capture into. */
  fun newCaptureFile(): File = File(capturesDir, "capture_${UUID.randomUUID()}.jpg")

  /** Copies the photo behind [uri] (camera capture or gallery pick) into internal storage. */
  suspend fun import(uri: Uri): String =
    withContext(Dispatchers.IO) {
      val fileName = "${UUID.randomUUID()}.jpg"
      checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open $uri" }.use { input ->
        File(photosDir, fileName).outputStream().use { output -> input.copyTo(output) }
      }
      fileName
    }

  /**
   * Cuts one photo into a file per region, each grown to [aspectRatio] first so the feed shows the
   * whole of what was framed rather than the middle of it. Returns a Uri per region written.
   *
   * The crops are real files rather than regions of a shared original on purpose: an item that owns
   * its photo outright behaves like any other item everywhere else — deleting one cannot take its
   * neighbours' photo with it, and backup, restore and the framing editor need to know nothing
   * about splitting. The source is decoded once for all of them.
   */
  suspend fun writeCrops(uri: Uri, regions: List<CropRect>, aspectRatio: Float): List<Uri> =
    withContext(Dispatchers.IO) {
      if (regions.isEmpty()) return@withContext emptyList()
      val source = decodeUpright(context, uri, CROP_DECODE_PX) ?: return@withContext emptyList()
      try {
        regions.mapNotNull { region ->
          writeCrop(source, region.expandedToAspect(aspectRatio, source.width, source.height))
        }
      } finally {
        source.recycle()
      }
    }

  /** Clamped the same way the display-time crop is, so a baked crop frames what a shown one would. */
  private fun writeCrop(source: Bitmap, region: CropRect): Uri? {
    val left = (region.left * source.width).roundToInt().coerceIn(0, source.width - 1)
    val top = (region.top * source.height).roundToInt().coerceIn(0, source.height - 1)
    val width = (region.width * source.width).roundToInt().coerceIn(1, source.width - left)
    val height = (region.height * source.height).roundToInt().coerceIn(1, source.height - top)
    val crop =
      runCatching { Bitmap.createBitmap(source, left, top, width, height) }.getOrNull() ?: return null
    val file = newCaptureFile()
    return try {
      file.outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, CROP_QUALITY, it) }
      Uri.fromFile(file)
    } catch (e: IOException) {
      file.delete()
      null
    } finally {
      crop.recycle()
    }
  }

  fun file(fileName: String): File = File(photosDir, fileName)

  suspend fun delete(fileName: String) {
    withContext(Dispatchers.IO) { File(photosDir, fileName).delete() }
  }
}
