package com.ahad.macat.data

/** How far the framing editor lets a photo be zoomed in. */
const val MAX_CROP_ZOOM = 8f

/**
 * The part of a photo that is actually shown, in normalised source coordinates (0..1).
 * A null crop on an [Item] means no framing was chosen and the whole photo is used.
 */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  val width: Float
    get() = right - left

  val height: Float
    get() = bottom - top
}

/** A photo's zoom (1 = fills the frame) and its pan in frame pixels, as the editor holds it. */
data class ZoomPan(val zoom: Float, val panX: Float, val panY: Float)

/**
 * The scale at which an [imageWidth] x [imageHeight] photo just covers a
 * [frameWidth] x [frameHeight] frame — what `ContentScale.Crop` does, and the editor's zoom of 1.
 */
fun coverScale(frameWidth: Float, frameHeight: Float, imageWidth: Float, imageHeight: Float): Float =
  maxOf(frameWidth / imageWidth, frameHeight / imageHeight)

/** Keeps zoom within range and the pan within the bounds where the photo still covers the frame. */
fun clampZoomPan(
  zoom: Float,
  panX: Float,
  panY: Float,
  frameWidth: Float,
  frameHeight: Float,
  imageWidth: Float,
  imageHeight: Float,
): ZoomPan {
  val clampedZoom = zoom.coerceIn(1f, MAX_CROP_ZOOM)
  val scale = coverScale(frameWidth, frameHeight, imageWidth, imageHeight) * clampedZoom
  val maxPanX = ((imageWidth * scale - frameWidth) / 2f).coerceAtLeast(0f)
  val maxPanY = ((imageHeight * scale - frameHeight) / 2f).coerceAtLeast(0f)
  return ZoomPan(clampedZoom, panX.coerceIn(-maxPanX, maxPanX), panY.coerceIn(-maxPanY, maxPanY))
}

/** The region of the photo left visible in the frame at this [zoom] and pan. */
fun cropRectFor(
  frameWidth: Float,
  frameHeight: Float,
  imageWidth: Float,
  imageHeight: Float,
  zoom: Float,
  panX: Float,
  panY: Float,
): CropRect {
  val scale = coverScale(frameWidth, frameHeight, imageWidth, imageHeight) * zoom
  val visibleWidth = (frameWidth / scale).coerceAtMost(imageWidth)
  val visibleHeight = (frameHeight / scale).coerceAtMost(imageHeight)
  val centreX =
    (imageWidth / 2f - panX / scale).coerceIn(visibleWidth / 2f, imageWidth - visibleWidth / 2f)
  val centreY =
    (imageHeight / 2f - panY / scale).coerceIn(visibleHeight / 2f, imageHeight - visibleHeight / 2f)
  return CropRect(
    left = (centreX - visibleWidth / 2f) / imageWidth,
    top = (centreY - visibleHeight / 2f) / imageHeight,
    right = (centreX + visibleWidth / 2f) / imageWidth,
    bottom = (centreY + visibleHeight / 2f) / imageHeight,
  )
}

/**
 * The zoom and pan that reproduce [crop] in this frame — the inverse of [cropRectFor], used to
 * reopen the editor on a framing already chosen. If the frame is a different shape than the one the
 * crop was made in (a backup restored onto another phone), the zoom is the one that keeps the frame
 * covered.
 */
fun zoomPanFor(
  crop: CropRect,
  frameWidth: Float,
  frameHeight: Float,
  imageWidth: Float,
  imageHeight: Float,
): ZoomPan {
  val cover = coverScale(frameWidth, frameHeight, imageWidth, imageHeight)
  val fill = maxOf(frameWidth / (crop.width * imageWidth), frameHeight / (crop.height * imageHeight))
  val zoom = (fill / cover).coerceIn(1f, MAX_CROP_ZOOM)
  val scale = cover * zoom
  val centreX = (crop.left + crop.right) / 2f * imageWidth
  val centreY = (crop.top + crop.bottom) / 2f * imageHeight
  return clampZoomPan(
    zoom = zoom,
    panX = (imageWidth / 2f - centreX) * scale,
    panY = (imageHeight / 2f - centreY) * scale,
    frameWidth = frameWidth,
    frameHeight = frameHeight,
    imageWidth = imageWidth,
    imageHeight = imageHeight,
  )
}
