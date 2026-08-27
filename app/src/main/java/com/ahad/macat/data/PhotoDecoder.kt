package com.ahad.macat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/**
 * Decodes [uri] downsampled to roughly [maxPx] on its longest side, turned the right way up.
 *
 * The right way up is the point. `BitmapFactory` hands back the pixels exactly as stored, and phone
 * cameras — CameraX included — record which way up the photo was taken as an EXIF tag instead of
 * rotating them. Coil applies that tag when it *displays* a photo, so anything that reads the
 * pixels itself and wants to agree with what the user is looking at has to apply it too. Reading a
 * portrait photo without it puts everything found in it 90 degrees away from where it is on screen.
 */
internal fun decodeUpright(context: Context, uri: Uri, maxPx: Int): Bitmap? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
  val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
  if (longestSide <= 0) return null
  val options =
    BitmapFactory.Options().apply {
      inSampleSize = Integer.highestOneBit(longestSide / maxPx).coerceAtLeast(1)
    }
  val decoded =
    context.contentResolver.openInputStream(uri)?.use {
      BitmapFactory.decodeStream(it, null, options)
    } ?: return null

  val matrix = uprightMatrix(context, uri) ?: return decoded
  val upright =
    runCatching {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
      }
      .getOrNull() ?: return decoded
  if (upright !== decoded) decoded.recycle()
  return upright
}

/** The turn and flip [uri]'s EXIF asks for, or null when it asks for nothing (much the commonest). */
private fun uprightMatrix(context: Context, uri: Uri): Matrix? {
  val orientation =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use {
          ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
          )
        }
      }
      .getOrNull() ?: return null
  val matrix = Matrix()
  when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
    ExifInterface.ORIENTATION_TRANSPOSE -> {
      matrix.postRotate(90f)
      matrix.postScale(-1f, 1f)
    }
    ExifInterface.ORIENTATION_TRANSVERSE -> {
      matrix.postRotate(270f)
      matrix.postScale(-1f, 1f)
    }
    else -> return null
  }
  return matrix
}
