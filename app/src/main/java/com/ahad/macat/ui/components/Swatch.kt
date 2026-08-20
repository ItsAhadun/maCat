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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

/**
 * Every colour at once, four to a row.
 *
 * The list this replaced made you scroll a column of words to pick a colour, which is a strange
 * way to choose something you recognise by sight. Fourteen swatches fit on one screen.
 */
@Composable
fun ColourPickerGrid(
  selected: Colour?,
  onSelect: (Colour) -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptics = LocalHapticFeedback.current
  Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
    Colour.entries.chunked(COLUMNS).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        row.forEach { colour ->
          ColourCell(
            colour = colour,
            selected = colour == selected,
            onSelect = {
              haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
              onSelect(colour)
            },
          )
        }
        // Keep the last row's cells the same width as every other row's.
        repeat(COLUMNS - row.size) { Box(Modifier.width(CELL_WIDTH)) }
      }
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
    Box(contentAlignment = Alignment.Center) {
      if (selected) {
        Box(
          Modifier.size(32.dp)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        )
      }
      Swatch(colour, size = 22.dp)
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
