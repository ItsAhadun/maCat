package com.ahad.macat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahad.macat.data.Colour

/** The dot that stands for a colour tag. Outlined, so white still reads on a white surface. */
@Composable
fun Swatch(colour: Colour?, modifier: Modifier = Modifier, size: Dp = 16.dp) {
  Box(
    modifier
      .size(size)
      .clip(CircleShape)
      .background(if (colour == null) Color.Transparent else Color(colour.swatch))
      .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
  )
}

/** The dots for every colour an item carries, side by side. Nothing at all when it carries none. */
@Composable
fun SwatchRow(colours: List<Colour>, modifier: Modifier = Modifier, size: Dp = 16.dp) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    colours.forEach { Swatch(it, size = size) }
  }
}

/**
 * Every colour at once, four to a row, as many of them selected as the item has.
 *
 * The list this replaced made you scroll a column of words to pick a colour, which is a strange
 * way to choose something you recognise by sight. Fourteen swatches fit on one screen.
 *
 * It has to *say* that more than one can be picked. A ring around a single swatch is what a radio
 * button looks like, so with nothing else on screen the grid reads as "choose one" and the second
 * colour never gets tapped — which is exactly how it was reported. Hence the instruction at the
 * top, the tick on each chosen swatch, and a Done button to close a menu that no longer closes
 * itself on the first tap.
 */
@Composable
fun ColourPickerGrid(
  selected: List<Colour>,
  onToggle: (Colour) -> Unit,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptics = LocalHapticFeedback.current
  Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
    Text(
      "Tap every colour this item has",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Colour.entries.chunked(COLUMNS).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        row.forEach { colour ->
          val picked = colour in selected
          ColourCell(
            colour = colour,
            selected = picked,
            onSelect = {
              haptics.performHapticFeedback(
                if (picked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
              )
              onToggle(colour)
            },
          )
        }
        // Keep the last row's cells the same width as every other row's.
        repeat(COLUMNS - row.size) { Box(Modifier.width(CELL_WIDTH)) }
      }
    }
    TextButton(onClick = onDone, modifier = Modifier.align(Alignment.End)) {
      Text(if (selected.isEmpty()) "Close" else "Done")
    }
  }
}

@Composable
private fun ColourCell(colour: Colour, selected: Boolean, onSelect: () -> Unit) {
  Column(
    modifier =
      Modifier.width(CELL_WIDTH)
        .clip(MaterialTheme.shapes.small)
        .selectable(selected = selected, onClick = onSelect)
        .padding(vertical = 8.dp)
        // The label below already says the colour; without this a screen reader reads it twice.
        .clearAndSetSemantics {},
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
      if (selected) {
        Box(
          Modifier.size(32.dp)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        )
      }
      Swatch(colour, size = 22.dp)
      // A tick, not just a ring: a ring alone is how one-of-many looks.
      if (selected) {
        Box(
          Modifier.align(Alignment.TopEnd)
            .size(15.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }
    }
    Text(
      text = colour.label,
      style = MaterialTheme.typography.labelSmall,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 4.dp),
    )
  }
}

private const val COLUMNS = 4
private val CELL_WIDTH = 64.dp
