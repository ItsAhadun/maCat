package com.ahad.macat

import com.ahad.macat.data.CropRect
import com.ahad.macat.data.clampZoomPan
import com.ahad.macat.data.cropRectFor
import com.ahad.macat.data.zoomPanFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The framing editor is a phone-shaped frame (1080x2400) over a portrait photo (3000x4000),
 * which is the case that matters: the photo is much wider than the frame, so it is the sides
 * that get cropped.
 */
class CropRectTest {
  private val frameWidth = 1080f
  private val frameHeight = 2400f
  private val imageWidth = 3000f
  private val imageHeight = 4000f

  @Test
  fun `untouched framing keeps the full height and centres the width`() {
    val crop = cropRect(zoom = 1f, panX = 0f, panY = 0f)
    // The frame is taller than the photo, so the photo is scaled to the frame height and only
    // its sides are lost — evenly, because it has not been panned.
    assertEquals(0f, crop.top, TOLERANCE)
    assertEquals(1f, crop.bottom, TOLERANCE)
    assertEquals(0.5f, (crop.left + crop.right) / 2f, TOLERANCE)
    assertEquals(frameWidth / frameHeight, crop.width * imageWidth / (crop.height * imageHeight), TOLERANCE)
  }

  @Test
  fun `zooming in keeps less of the photo`() {
    val crop = cropRect(zoom = 2f, panX = 0f, panY = 0f)
    assertEquals(0.25f, crop.top, TOLERANCE)
    assertEquals(0.75f, crop.bottom, TOLERANCE)
    assertEquals(0.5f, (crop.left + crop.right) / 2f, TOLERANCE)
  }

  @Test
  fun `panning moves the kept region the other way`() {
    // Dragging the photo right shows more of its left side.
    val panned = cropRect(zoom = 2f, panX = 200f, panY = 0f)
    val centred = cropRect(zoom = 2f, panX = 0f, panY = 0f)
    assert(panned.left < centred.left) { "panning right should uncover the left of the photo" }
    assertEquals(centred.width, panned.width, TOLERANCE)
  }

  @Test
  fun `the photo can never be dragged off the frame`() {
    val crop = cropRect(zoom = 1.5f, panX = 100_000f, panY = 100_000f)
    assertEquals(0f, crop.left, TOLERANCE)
    assertEquals(0f, crop.top, TOLERANCE)
  }

  @Test
  fun `clamping holds zoom and pan inside their limits`() {
    val clamped =
      clampZoomPan(0.2f, 5000f, 5000f, frameWidth, frameHeight, imageWidth, imageHeight)
    assertEquals(1f, clamped.zoom, TOLERANCE)
    // At zoom 1 the photo covers the frame vertically exactly, so it cannot move up or down.
    assertEquals(0f, clamped.panY, TOLERANCE)
    assert(clamped.panX < 5000f) { "pan should have been pulled back to the photo edge" }
  }

  @Test
  fun `reopening a saved framing restores it`() {
    val original = cropRect(zoom = 2.5f, panX = -120f, panY = 340f)
    val zoomPan = zoomPanFor(original, frameWidth, frameHeight, imageWidth, imageHeight)
    val reopened =
      cropRectFor(
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        zoom = zoomPan.zoom,
        panX = zoomPan.panX,
        panY = zoomPan.panY,
      )
    assertEquals(original.left, reopened.left, TOLERANCE)
    assertEquals(original.top, reopened.top, TOLERANCE)
    assertEquals(original.right, reopened.right, TOLERANCE)
    assertEquals(original.bottom, reopened.bottom, TOLERANCE)
  }

  @Test
  fun `a framing from a differently shaped phone still covers the frame`() {
    // A wide crop saved on another device: reopening must not leave the frame half empty.
    val fromElsewhere = CropRect(left = 0.1f, top = 0.4f, right = 0.9f, bottom = 0.6f)
    val zoomPan = zoomPanFor(fromElsewhere, frameWidth, frameHeight, imageWidth, imageHeight)
    assert(zoomPan.zoom >= 1f) { "zoom must not drop below the covering scale" }
    val reopened =
      cropRectFor(
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        zoom = zoomPan.zoom,
        panX = zoomPan.panX,
        panY = zoomPan.panY,
      )
    assert(reopened.left >= 0f && reopened.top >= 0f) { "crop escaped the photo: $reopened" }
    assert(reopened.right <= 1f && reopened.bottom <= 1f) { "crop escaped the photo: $reopened" }
  }

  private fun cropRect(zoom: Float, panX: Float, panY: Float): CropRect =
    cropRectFor(frameWidth, frameHeight, imageWidth, imageHeight, zoom, panX, panY)

  private companion object {
    const val TOLERANCE = 0.001f
  }
}
