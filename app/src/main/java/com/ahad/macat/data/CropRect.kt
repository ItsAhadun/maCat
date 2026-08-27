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

/** Smallest a split box can be dragged, as a share of the photo — smaller cannot be grabbed again. */
const val MIN_BOX = 0.05f

/** Slides the box, keeping its size — one pushed against an edge stops rather than shrinking. */
fun CropRect.movedBy(dx: Float, dy: Float): CropRect {
  val x = (left + dx).coerceIn(0f, 1f - width)
  val y = (top + dy).coerceIn(0f, 1f - height)
  return CropRect(x, y, x + width, y + height)
}

/**
 * Moves one corner, keeping the box the right way round and no smaller than [MIN_BOX].
 *
 * Both ends of each clamp are held apart deliberately: a detected box can arrive narrower than
 * [MIN_BOX] — a thin sliver of a strap does — and `coerceIn(0f, right - MIN_BOX)` throws the moment
 * its top bound goes negative.
 */
fun CropRect.resizedBy(
  dLeft: Float = 0f,
  dTop: Float = 0f,
  dRight: Float = 0f,
  dBottom: Float = 0f,
): CropRect =
  CropRect(
    left = (left + dLeft).coerceIn(0f, (right - MIN_BOX).coerceAtLeast(0f)),
    top = (top + dTop).coerceIn(0f, (bottom - MIN_BOX).coerceAtLeast(0f)),
    right = (right + dRight).coerceIn((left + MIN_BOX).coerceAtMost(1f), 1f),
    bottom = (bottom + dBottom).coerceIn((top + MIN_BOX).coerceAtMost(1f), 1f),
  )

/** A photo's zoom (1 = fills the frame) and its pan in frame pixels, as the editor holds it. */
data class ZoomPan(val zoom: Float, val panX: Float, val panY: Float)

/**
 * Grows this rect outwards until the region it picks out of a [imageWidth] x [imageHeight] photo is
 * [targetAspect] (width / height) shaped, staying inside the photo.
 *
 * Used when a photo is split into one item per box: the boxes are drawn tight around the items, but
 * the feed shows every photo at its own shape with `ContentScale.Crop`, so a tight box round a
 * sandal lying sideways would have its ends chopped off on display. Growing it here — outwards
 * only, so nothing the user drew is ever cut into — costs a little backdrop around the item and
 * keeps the whole of it on screen. A photo too small to reach the aspect simply gives all it has.
 */
fun CropRect.expandedToAspect(targetAspect: Float, imageWidth: Int, imageHeight: Int): CropRect {
  if (targetAspect <= 0f || imageWidth <= 0 || imageHeight <= 0) return this
  val currentWidth = width * imageWidth
  val currentHeight = height * imageHeight
  if (currentWidth <= 0f || currentHeight <= 0f) return this

  var grownWidth = currentWidth
  var grownHeight = currentHeight
  if (currentWidth / currentHeight < targetAspect) {
    grownWidth = currentHeight * targetAspect
  } else {
    grownHeight = currentWidth / targetAspect
  }
  grownWidth = grownWidth.coerceAtMost(imageWidth.toFloat())
  grownHeight = grownHeight.coerceAtMost(imageHeight.toFloat())

  // Grow about the middle, then slide back inside the photo. Sliding rather than shrinking is what
  // keeps the original rect covered when it was already up against an edge.
  val centreX = (left + right) / 2f * imageWidth
  val centreY = (top + bottom) / 2f * imageHeight
  val x = (centreX - grownWidth / 2f).coerceIn(0f, imageWidth - grownWidth)
  val y = (centreY - grownHeight / 2f).coerceIn(0f, imageHeight - grownHeight)
  return CropRect(
    left = x / imageWidth,
    top = y / imageHeight,
    right = (x + grownWidth) / imageWidth,
    bottom = (y + grownHeight) / imageHeight,
  )
}

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
