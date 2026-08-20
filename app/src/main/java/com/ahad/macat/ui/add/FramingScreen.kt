package com.ahad.macat.ui.add

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.ahad.macat.data.CropRect
import com.ahad.macat.data.MAX_CROP_ZOOM
import com.ahad.macat.data.ZoomPan
import com.ahad.macat.data.clampZoomPan
import com.ahad.macat.data.coverScale
import com.ahad.macat.data.cropRectFor
import com.ahad.macat.data.zoomPanFor

/** Decode size for the editor: enough detail to still look sharp zoomed in, small enough to hold. */
private const val EDITOR_DECODE_PX = 1600

/**
 * Full-screen framing editor. The frame *is* the screen, so what the user lines up here is exactly
 * what the feed shows — the 3:4 preview it replaces was cropped again by the feed's taller viewport.
 *
 * Shown in place behind a boolean like [CameraCaptureScreen], which also keeps the pinch gesture
 * clear of any pager or scroll to fight with (see tasks/lessons.md).
 *
 * [onDone] gets null when the photo is back at its untouched framing, so items only carry a crop
 * once one was actually chosen.
 */
@Composable
fun FramingScreen(photo: Any, initialCrop: CropRect?, onDone: (CropRect?) -> Unit, onCancel: () -> Unit) {
  BackHandler(onBack = onCancel)
  val context = LocalContext.current
  val painter =
    rememberAsyncImagePainter(
      remember(photo) { ImageRequest.Builder(context).data(photo).size(EDITOR_DECODE_PX).build() }
    )

  BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
    val frameWidth = constraints.maxWidth.toFloat()
    val frameHeight = constraints.maxHeight.toFloat()
    val imageSize = painter.intrinsicSize
    val loaded = imageSize.isSpecified

    var zoomPan by remember { mutableStateOf(ZoomPan(1f, 0f, 0f)) }
    // Read and written from inside the gesture lambda, which outlives any recomposition,
    // so this is a state holder rather than a delegated var.
    val atEdge = remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // Reopening an existing framing: put the photo back where it was left.
    var restored by remember { mutableStateOf(initialCrop == null) }
    LaunchedEffect(loaded, frameWidth, frameHeight) {
      if (loaded && !restored) {
        zoomPan =
          zoomPanFor(
            crop = checkNotNull(initialCrop),
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            imageWidth = imageSize.width,
            imageHeight = imageSize.height,
          )
        restored = true
      }
    }

    // The photo is laid out at the size that just covers the frame — bigger than the frame, and
    // clipped by it — so dragging uncovers the parts hanging over the edges. Letting
    // ContentScale.Crop do the covering instead would clip the photo before the pan moved it,
    // and the drag would reveal nothing but background.
    if (loaded) {
      val density = LocalDensity.current
      val cover = coverScale(frameWidth, frameHeight, imageSize.width, imageSize.height)
      Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        Image(
          painter = painter,
          contentDescription = "Item photo",
          contentScale = ContentScale.Crop,
          modifier =
            Modifier.requiredSize(
                with(density) { (imageSize.width * cover).toDp() },
                with(density) { (imageSize.height * cover).toDp() },
              )
              .graphicsLayer {
                scaleX = zoomPan.zoom
                scaleY = zoomPan.zoom
                translationX = zoomPan.panX
                translationY = zoomPan.panY
              },
        )
      }
    }

    // Gestures sit on an untransformed layer above the photo, so the centroid stays in screen
    // coordinates and the zoom can be anchored on the fingers.
    Box(
      Modifier.fillMaxSize().pointerInput(loaded, frameWidth, frameHeight) {
        if (!loaded) return@pointerInput
        detectTransformGestures { centroid, panChange, zoomChange, _ ->
          val current = zoomPan
          val zoom = (current.zoom * zoomChange).coerceIn(1f, MAX_CROP_ZOOM)
          val applied = zoom / current.zoom
          val anchorX = centroid.x - frameWidth / 2f
          val anchorY = centroid.y - frameHeight / 2f
          val pannedX = current.panX + panChange.x
          val pannedY = current.panY + panChange.y
          val requestedX = anchorX - (anchorX - pannedX) * applied
          val requestedY = anchorY - (anchorY - pannedY) * applied
          val clamped =
            clampZoomPan(
              zoom = zoom,
              panX = requestedX,
              panY = requestedY,
              frameWidth = frameWidth,
              frameHeight = frameHeight,
              imageWidth = imageSize.width,
              imageHeight = imageSize.height,
            )
          // A tick the moment the photo comes up against the edge of the frame, so the limit is
          // felt rather than only seen. Edge-triggered: dragging along an edge already reached
          // keeps clamping every frame, and buzzing for all of them would be a rattle.
          val nowAtEdge = clamped.panX != requestedX || clamped.panY != requestedY
          if (nowAtEdge && !atEdge.value) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
          }
          atEdge.value = nowAtEdge
          zoomPan = clamped
        }
      }
    )

    Row(
      modifier =
        Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onCancel) {
        Icon(Icons.Default.Close, contentDescription = "Cancel framing", tint = Color.White)
      }
      Text(
        text = if (loaded) "Pinch to zoom, drag to frame" else "Loading photo…",
        color = Color.White.copy(alpha = 0.8f),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.weight(1f).padding(start = 8.dp),
      )
      TextButton(onClick = { zoomPan = ZoomPan(1f, 0f, 0f) }) { Text("Reset", color = Color.White) }
      Button(
        onClick = {
          val untouched = zoomPan == ZoomPan(1f, 0f, 0f)
          onDone(
            if (untouched) null
            else
              cropRectFor(
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                imageWidth = imageSize.width,
                imageHeight = imageSize.height,
                zoom = zoomPan.zoom,
                panX = zoomPan.panX,
                panY = zoomPan.panY,
              )
          )
        },
        enabled = loaded,
        modifier = Modifier.padding(start = 8.dp),
      ) {
        Text("Done")
      }
    }
  }
}
