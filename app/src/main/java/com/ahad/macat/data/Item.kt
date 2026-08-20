package com.ahad.macat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class Item(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  /** The [Category] *name*, not its id — see [Category] for why. */
  val category: String,
  val photoFileName: String,
  val createdAt: Long = System.currentTimeMillis(),
  val colour: Colour? = null,
  // The chosen framing, split into columns Room can store. Read it through [crop].
  val cropLeft: Float? = null,
  val cropTop: Float? = null,
  val cropRight: Float? = null,
  val cropBottom: Float? = null,
  /** Null on every row that predates v3, which is why this is nullable rather than false. */
  val isFavourite: Boolean? = null,
  /** When the item was moved to the bin; null means it is in the catalogue. */
  val deletedAt: Long? = null,
) {
  /** What the screens show: the name that was typed, or one made from the photo's colour. */
  val displayName: String
    get() = name.ifBlank { autoName(colour, category) }

  /** The part of the photo to show, or null to show all of it. */
  val crop: CropRect?
    get() =
      if (cropLeft != null && cropTop != null && cropRight != null && cropBottom != null) {
        CropRect(cropLeft, cropTop, cropRight, cropBottom)
      } else {
        null
      }

  val favourite: Boolean
    get() = isFavourite == true
}

fun Item.withCrop(crop: CropRect?): Item =
  copy(
    cropLeft = crop?.left,
    cropTop = crop?.top,
    cropRight = crop?.right,
    cropBottom = crop?.bottom,
  )

/** The stand-in name for an item nobody named: "Pink shoes". */
fun autoName(colour: Colour?, category: String): String =
  if (colour == null) category else "${colour.label} ${category.lowercase()}"
