package com.ahad.macat.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

  fun file(fileName: String): File = File(photosDir, fileName)

  suspend fun delete(fileName: String) {
    withContext(Dispatchers.IO) { File(photosDir, fileName).delete() }
  }
}
