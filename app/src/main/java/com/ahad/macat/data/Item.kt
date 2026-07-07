package com.ahad.macat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Category(val label: String) {
  CLOTHES("Clothes"),
  SHOES("Shoes"),
  JEWELLERY("Jewellery"),
}

@Entity(tableName = "items")
data class Item(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val category: Category,
  val photoFileName: String,
  val createdAt: Long = System.currentTimeMillis(),
)
