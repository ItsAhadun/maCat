package com.ahad.macat.ui.add

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.ahad.macat.data.CropRect
import com.ahad.macat.data.movedBy
import com.ahad.macat.data.resizedBy
import kotlin.math.roundToInt

/** Decode size for the review: the whole photo is on screen at once, so the editor's budget suits. */
private const val REVIEW_DECODE_PX = 1600

/** Where a box goes when there is nothing to start from, or when one is added by hand. */
private val DEFAULT_BOX = CropRect(0.25f, 0.25f, 0.75f, 0.75f)

/**
 * Asks whether a photo really holds several items, by showing what the app would cut out of it.
 *
 * The question is the screen: a number in a dialog ("3 items found — split?") is not something
 * anyone can answer, because it says nothing about *which* three. Drawn on the photo it is obvious
 * at a glance, and obvious what to correct — so the boxes are draggable, which also covers the case
 * the detector gets wrong. Two shoes touching come back as one lump, and no amount of threshold
 * tuning fixes every photo; a box the user can pull into place does.
 *
 * Shown in place behind a boolean like [CameraCaptureScreen] and [FramingScreen], for the same
 * reason — the drags here would fight a pager or a scroll for the gesture (see tasks/lessons.md).
 */
@Composable
fun SplitReviewScreen(
  photo: Any,
  initialBoxes: List<CropRect>,
  onSplit: (List<CropRect>) -> Unit,
  onKeepAsOne: () -> Unit,
) {
  BackHandler(onBack = onKeepAsOne)
  val context = LocalContext.current
  val painter =
    rememberAsyncImagePainter(
      remember(photo) { ImageRequest.Builder(context).data(photo).size(REVIEW_DECODE_PX).build() }
    )

  // Keyed on the photo: the next entry to be reviewed reuses this slot, and must not inherit the
  // boxes drawn for the last one.
  var boxes by
    rememberSaveable(photo, stateSaver = CropRectListSaver) {
      mutableStateOf(initialBoxes.ifEmpty { listOf(DEFAULT_BOX) })
    }

  Box(Modifier.fillMaxSize().background(Color.Black)) {
    Column(Modifier.fillMaxSize()) {
      BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val frameWidth = constraints.maxWidth.toFloat()
        val frameHeight = constraints.maxHeight.toFloat()
        val imageSize = painter.intrinsicSize

        if (!imageSize.isSpecified) {
          Text(
            "Loading photo…",
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.Center),
          )
          return@BoxWithConstraints
        }

        // The whole photo is shown, letterboxed — boxes are only meaningful against all of it — so
        // the drawn image is smaller than the frame and every box coordinate goes through this rect.
        val fit = minOf(frameWidth / imageSize.width, frameHeight / imageSize.height)
        val shownWidth = imageSize.width * fit
        val shownHeight = imageSize.height * fit
        val originX = (frameWidth - shownWidth) / 2f
        val originY = (frameHeight - shownHeight) / 2f

        Image(
          painter = painter,
          contentDescription = "Photo being split",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
        )

        boxes.forEachIndexed { index, box ->
          SplitBox(
            box = box,
            number = index + 1,
            originX = originX,
            originY = originY,
            shownWidth = shownWidth,
            shownHeight = shownHeight,
            onChange = { changed -> boxes = boxes.toMutableList().also { it[index] = changed } },
            onRemove = { boxes = boxes.toMutableList().also { it.removeAt(index) } },
          )
        }
      }

      SplitActions(
        count = boxes.size,
        onAddBox = { boxes = boxes + DEFAULT_BOX.nudgedBy(boxes.size) },
        onKeepAsOne = onKeepAsOne,
        onSplit = { onSplit(boxes) },
      )
    }

    IconButton(onClick = onKeepAsOne, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
      Icon(Icons.Default.Close, contentDescription = "Keep as one photo", tint = Color.White)
    }
  }
}

/**
 * One box over the photo: drag the middle to move it, either corner to resize it, the cross to drop
 * it.
 *
 * [box] and the callbacks are read through [rememberUpdatedState] because the gesture block outlives
 * the recompositions the drag causes. Captured directly, every drag event would be applied to the
 * box as it was when the finger went down, and the box would jitter around its starting place
 * instead of following.
 */
@Composable
private fun SplitBox(
  box: CropRect,
  number: Int,
  originX: Float,
  originY: Float,
  shownWidth: Float,
  shownHeight: Float,
  onChange: (CropRect) -> Unit,
  onRemove: () -> Unit,
) {
  val density = LocalDensity.current
  val current by rememberUpdatedState(box)
  val change by rememberUpdatedState(onChange)
  val outline = MaterialTheme.colorScheme.primary

  val left = originX + box.left * shownWidth
  val top = originY + box.top * shownHeight

  Box(
    Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
      .size(
        with(density) { (box.width * shownWidth).toDp() },
        with(density) { (box.height * shownHeight).toDp() },
      )
      .border(2.dp, outline, RoundedCornerShape(4.dp))
      .pointerInput(shownWidth, shownHeight) {
        detectDragGestures { pointer, drag ->
          pointer.consume()
          change(current.movedBy(drag.x / shownWidth, drag.y / shownHeight))
        }
      }
  ) {
    Text(
      number.toString(),
      color = Color.White,
      fontWeight = FontWeight.Bold,
      style = MaterialTheme.typography.labelMedium,
      modifier =
        Modifier.align(Alignment.BottomStart)
          .padding(4.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(Color.Black.copy(alpha = 0.55f))
          .padding(horizontal = 6.dp, vertical = 2.dp),
    )
    Box(
      Modifier.align(Alignment.TopEnd)
        .padding(2.dp)
        .size(24.dp)
        .clip(CircleShape)
        .background(Color.Black.copy(alpha = 0.55f))
        .clickable(onClick = onRemove),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Default.Close,
        contentDescription = "Remove box $number",
        tint = Color.White,
        modifier = Modifier.size(16.dp),
      )
    }
  }

  CornerHandle(
    x = left,
    y = top,
    colour = outline,
    description = "Top left of box $number",
    onDrag = { dx, dy ->
      change(current.resizedBy(dLeft = dx / shownWidth, dTop = dy / shownHeight))
    },
  )
  CornerHandle(
    x = originX + box.right * shownWidth,
    y = originY + box.bottom * shownHeight,
    colour = outline,
    description = "Bottom right of box $number",
    onDrag = { dx, dy ->
      change(current.resizedBy(dRight = dx / shownWidth, dBottom = dy / shownHeight))
    },
  )
}

/** A grab point straddling a corner of a box, big enough to hit with a fingertip. */
@Composable
private fun CornerHandle(
  x: Float,
  y: Float,
  colour: Color,
  description: String,
  onDrag: (Float, Float) -> Unit,
) {
  val density = LocalDensity.current
  val half = with(density) { HANDLE_SIZE.toPx() } / 2f
  val drag by rememberUpdatedState(onDrag)
  Box(
    Modifier.offset { IntOffset((x - half).roundToInt(), (y - half).roundToInt()) }
      .size(HANDLE_SIZE)
      .clip(CircleShape)
      .background(colour)
      .semantics { contentDescription = description }
      .pointerInput(Unit) {
        detectDragGestures { pointer, amount ->
          pointer.consume()
          drag(amount.x, amount.y)
        }
      }
  )
}

@Composable
private fun SplitActions(
  count: Int,
  onAddBox: () -> Unit,
  onKeepAsOne: () -> Unit,
  onSplit: () -> Unit,
) {
  Column(
    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      if (count == 1) "One item — drag the box to frame it"
      else "$count items — drag the boxes to fix them",
      color = Color.White,
      style = MaterialTheme.typography.labelLarge,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedButton(onClick = onAddBox, modifier = Modifier.weight(1f)) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Add box", modifier = Modifier.padding(start = 6.dp))
      }
      TextButton(onClick = onKeepAsOne, modifier = Modifier.weight(1f)) {
        Text("Keep as one", color = Color.White)
      }
    }
    Button(onClick = onSplit, enabled = count > 0, modifier = Modifier.fillMaxWidth()) {
      Text(if (count == 1) "Crop to one item" else "Split into $count items")
    }
  }
}

/** Big enough to hit with a fingertip; the box corner sits in the middle of it. */
private val HANDLE_SIZE = 28.dp

/** Added boxes step down the photo, so a new one is never hidden exactly under the last. */
private fun CropRect.nudgedBy(step: Int): CropRect {
  val shift = (step % 5) * 0.04f
  return movedBy(shift, shift)
}

/** Boxes are not [android.os.Bundle] material; the four numbers each is made of are. */
private val CropRectListSaver =
  listSaver<List<CropRect>, Float>(
    save = { boxes -> boxes.flatMap { listOf(it.left, it.top, it.right, it.bottom) } },
    restore = { values ->
      values.chunked(4).mapNotNull { if (it.size == 4) CropRect(it[0], it[1], it[2], it[3]) else null }
    },
  )
