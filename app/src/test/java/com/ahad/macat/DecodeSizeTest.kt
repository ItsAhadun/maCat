package com.ahad.macat

import com.ahad.macat.data.CropRect
import com.ahad.macat.ui.decodeSizeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeSizeTest {

  @Test
  fun `a zoomed crop is decoded larger so it stays sharp`() {
    val half = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
    assertEquals(2048, decodeSizeFor(1024, half))
  }

  @Test
  fun `a deep crop stops at the memory cap`() {
    val quarter = CropRect(0.25f, 0.25f, 0.5f, 0.5f)
    assertEquals(2560, decodeSizeFor(1024, quarter))
  }

  @Test
  fun `a full-frame crop asks for no more than the display size`() {
    assertEquals(1024, decodeSizeFor(1024, CropRect(0f, 0f, 1f, 1f)))
  }

  @Test
  fun `a tall phone never asks for less than it will show`() {
    // A 1440x3200 phone: the base size alone is over the memory cap, and asking for less than
    // the display size would be a downgrade (this used to throw).
    val basePx = 3200
    val decoded = decodeSizeFor(basePx, CropRect(0.2f, 0.1f, 0.8f, 0.9f))
    assertTrue("decoded $decoded should be at least $basePx", decoded >= basePx)
  }
}
