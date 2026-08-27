package com.ahad.macat.data

import android.content.Context
import android.net.Uri
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports and imports the whole catalogue as a single .zip that the user saves through the
 * system file picker (SAF). Internal app storage is wiped on uninstall, so this is how a
 * catalogue survives reinstalls and moves to a new phone. Offline, no extra permissions.
 *
 * Format: [MANIFEST] holds the item and category metadata; [PHOTOS_DIR] holds the photo files by
 * name. Binned items are not exported.
 *
 * Both directions have to survive a version gap. Reading is additive: every field added since
 * format 2 is read through `opt*` with a fallback, so an older backup restores fine — including
 * format 3's single `colour` string, which format 4 replaced with a `colours` list. Writing
 * degrades on the other side, because a build that still had the fixed category enum skips items
 * whose category it cannot parse rather than failing the whole restore.
 */
class BackupManager(
  private val context: Context,
  private val db: CatalogueDatabase,
  private val photoStore: PhotoStore,
) {
  private val dao = db.itemDao()
  private val categoryDao = db.categoryDao()

  /** Writes metadata + photos into [uri]. Returns the number of items backed up. */
  suspend fun export(uri: Uri): Int =
    withContext(Dispatchers.IO) {
      val items = dao.snapshot()
      val categories = categoryDao.snapshot()
      val output =
        checkNotNull(context.contentResolver.openOutputStream(uri)) { "Cannot write $uri" }
      ZipOutputStream(output.buffered()).use { zip ->
        val categoryEntries = JSONArray()
        for (category in categories) {
          categoryEntries.put(
            JSONObject()
              .put("name", category.name)
              .put("sortOrder", category.sortOrder)
              .put("isBuiltIn", category.isBuiltIn)
          )
        }

        val entries = JSONArray()
        for (item in items) {
          val entry =
            JSONObject()
              .put("name", item.name)
              .put("category", item.category)
              .put("photoFileName", item.photoFileName)
              .put("createdAt", item.createdAt)
          item.colours?.takeIf { it.isNotEmpty() }?.let { colours ->
            entry.put("colours", JSONArray(colours.map { it.name }))
          }
          item.isFavourite?.let { entry.put("isFavourite", it) }
          item.crop?.let {
            entry
              .put("cropLeft", it.left.toDouble())
              .put("cropTop", it.top.toDouble())
              .put("cropRight", it.right.toDouble())
              .put("cropBottom", it.bottom.toDouble())
          }
          entries.put(entry)
        }
        val manifest =
          JSONObject()
            .put("version", FORMAT_VERSION)
            .put("categories", categoryEntries)
            .put("items", entries)
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(manifest.toString().toByteArray())
        zip.closeEntry()

        for (item in items) {
          val photo = photoStore.file(item.photoFileName)
          if (!photo.exists()) continue
          zip.putNextEntry(ZipEntry("$PHOTOS_DIR/${item.photoFileName}"))
          photo.inputStream().use { it.copyTo(zip) }
          zip.closeEntry()
        }
      }
      items.size
    }

  /**
   * Restores photos into internal storage and inserts the backed-up items. Returns how many were
   * actually restored. Items are appended, so importing the same file twice duplicates them.
   * Throws if [uri] is not a valid maCat backup.
   */
  suspend fun import(uri: Uri): Int =
    withContext(Dispatchers.IO) {
      val input = checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot read $uri" }
      var manifestJson: String? = null
      ZipInputStream(input.buffered()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
          val name = entry.name
          when {
            name == MANIFEST -> manifestJson = zip.readBytes().decodeToString()
            name.startsWith("$PHOTOS_DIR/") && !entry.isDirectory -> {
              val fileName = name.substringAfterLast('/')
              if (fileName.isNotEmpty()) {
                photoStore.file(fileName).outputStream().use { zip.copyTo(it) }
              }
            }
          }
          zip.closeEntry()
          entry = zip.nextEntry
        }
      }
      val manifest = JSONObject(requireNotNull(manifestJson) { "Not a maCat backup: no $MANIFEST" })

      // Categories arrived in format 3. A format 2 backup simply has none, and its items land on
      // the categories this catalogue already has.
      mergeCategories(manifest.optJSONArray("categories"))

      val items = manifest.getJSONArray("items")
      var restored = 0
      for (i in 0 until items.length()) {
        val obj = items.getJSONObject(i)
        val stored = obj.getString("category")
        val category = LEGACY_CATEGORY_NAMES[stored] ?: stored
        // An item can name a category the backup's own list left out: an older format, or a
        // hand-edited manifest. Create it rather than dropping the item.
        ensureCategory(category)
        // Colour and crop arrived in format 2; backups without them restore as untagged and
        // uncropped rather than failing. Format 4 turned the one colour into a list, so a backup
        // written before it carries a single "colour" string instead — read whichever is there.
        val colours = readColours(obj)
        val crop =
          if (obj.has("cropLeft")) {
            CropRect(
              left = obj.getDouble("cropLeft").toFloat(),
              top = obj.getDouble("cropTop").toFloat(),
              right = obj.getDouble("cropRight").toFloat(),
              bottom = obj.getDouble("cropBottom").toFloat(),
            )
          } else {
            null
          }
        dao.insert(
          Item(
              name = obj.getString("name"),
              category = category,
              photoFileName = obj.getString("photoFileName"),
              createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
              colours = colours,
              isFavourite = if (obj.has("isFavourite")) obj.getBoolean("isFavourite") else null,
            )
            .withCrop(crop)
        )
        restored++
      }
      restored
    }

  /** An item's colours from either shape of manifest: the format 4 list, or the older string. */
  private fun readColours(obj: JSONObject): List<Colour> {
    val list = obj.optJSONArray("colours")
    if (list != null) {
      return (0 until list.length()).mapNotNull { i -> parseColour(list.optString(i)) }
    }
    return listOfNotNull(parseColour(obj.optString("colour")))
  }

  private fun parseColour(name: String?): Colour? =
    name?.takeIf { it.isNotEmpty() }?.let { runCatching { Colour.valueOf(it) }.getOrNull() }

  /** Adds the categories the backup has and this catalogue does not, matching on name. */
  private suspend fun mergeCategories(categories: JSONArray?) {
    if (categories == null) return
    for (i in 0 until categories.length()) {
      val obj = categories.optJSONObject(i) ?: continue
      val stored = obj.optString("name").takeIf { it.isNotEmpty() } ?: continue
      val name = LEGACY_CATEGORY_NAMES[stored] ?: stored
      if (categoryDao.byName(name) != null) continue
      categoryDao.insert(
        Category(
          name = name,
          sortOrder = categoryDao.nextSortOrder(),
          isBuiltIn = obj.optBoolean("isBuiltIn", false),
        )
      )
    }
  }

  private suspend fun ensureCategory(name: String) {
    if (categoryDao.byName(name) != null) return
    categoryDao.insert(Category(name = name, sortOrder = categoryDao.nextSortOrder()))
  }

  private companion object {
    const val FORMAT_VERSION = 4
    const val MANIFEST = "manifest.json"
    const val PHOTOS_DIR = "photos"
  }
}
