package com.ahad.macat.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ahad.macat.data.Category
import com.ahad.macat.data.Colour
import com.ahad.macat.data.FilterState
import com.ahad.macat.data.FilterVisibility
import com.ahad.macat.data.SortOrder

/**
 * The filter controls the feed and the grid share.
 *
 * Which rows appear is the user's choice ([FilterVisibility]); a row that is switched off is not
 * drawn, and the ViewModel has already cleared whatever it had selected, so nothing narrows the
 * catalogue from behind a hidden control.
 *
 * [onDark] is for the feed, where these sit over a full-bleed photo rather than on a surface.
 */
@Composable
fun FilterBar(
  visibility: FilterVisibility,
  state: FilterState,
  categories: List<Category>,
  colours: List<Colour>,
  onCategory: (String?) -> Unit,
  onColour: (Colour?) -> Unit,
  onToggleFavourites: () -> Unit,
  modifier: Modifier = Modifier,
  onDark: Boolean = false,
) {
  val showCategories = visibility.categories && categories.isNotEmpty()
  val showColours = visibility.colours && colours.isNotEmpty()
  if (!showCategories && !showColours && !visibility.favourites) return

  Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (showCategories || visibility.favourites) {
      Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (visibility.favourites) {
          BarChip(
            label = "Favourites",
            selected = state.favouritesOnly,
            onClick = onToggleFavourites,
            onDark = onDark,
            icon = Icons.Default.Star,
          )
        }
        if (showCategories) {
          BarChip("All", selected = state.category == null, onClick = { onCategory(null) }, onDark)
          categories.forEach { category ->
            BarChip(
              label = category.name,
              selected = state.category == category.name,
              onClick = { onCategory(category.name) },
              onDark = onDark,
            )
          }
        }
      }
    }

    if (showColours) {
      Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        colours.forEach { colour ->
          SwatchChip(
            colour = colour,
            selected = state.colour == colour,
            // Tapping the selected colour again clears it — the row has no "All" of its own.
            onClick = { onColour(if (state.colour == colour) null else colour) },
            onDark = onDark,
          )
        }
      }
    }
  }
}

@Composable
private fun BarChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  onDark: Boolean,
  icon: ImageVector? = null,
) {
  val haptics = LocalHapticFeedback.current
  FilterChip(
    selected = selected,
    onClick = {
      haptics.performHapticFeedback(
        if (selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
      )
      onClick()
    },
    label = { Text(label) },
    leadingIcon =
      icon?.let { { Icon(it, contentDescription = null, Modifier.size(18.dp)) } },
    colors =
      if (onDark) {
        FilterChipDefaults.filterChipColors(
          labelColor = Color.White,
          iconColor = Color.White,
          selectedContainerColor = Color.White,
          selectedLabelColor = Color.Black,
          selectedLeadingIconColor = Color.Black,
        )
      } else {
        FilterChipDefaults.filterChipColors()
      },
    border =
      if (onDark) {
        FilterChipDefaults.filterChipBorder(
          enabled = true,
          selected = selected,
          borderColor = Color.White.copy(alpha = 0.5f),
        )
      } else {
        FilterChipDefaults.filterChipBorder(enabled = true, selected = selected)
      },
  )
}

/** A colour as a tappable dot. Selection is a ring, so the colour itself stays unobscured. */
@Composable
private fun SwatchChip(colour: Colour, selected: Boolean, onClick: () -> Unit, onDark: Boolean) {
  val haptics = LocalHapticFeedback.current
  val ring = if (onDark) Color.White else MaterialTheme.colorScheme.primary
  Box(
    modifier =
      Modifier.size(36.dp)
        .selectable(
          selected = selected,
          onClick = {
            haptics.performHapticFeedback(
              if (selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
            )
            onClick()
          },
        )
        .semantics { stateDescription = if (selected) "Selected" else "Not selected" },
    contentAlignment = Alignment.Center,
  ) {
    if (selected) Box(Modifier.size(32.dp).border(2.dp, ring, CircleShape))
    Swatch(colour, size = 22.dp)
  }
}

/** The sort picker, as a menu behind one icon — it belongs with the other top-bar actions. */
@Composable
fun SortMenu(current: SortOrder, onSort: (SortOrder) -> Unit, icon: ImageVector, tint: Color) {
  var open by remember { mutableStateOf(false) }
  val haptics = LocalHapticFeedback.current
  Box {
    IconButton(onClick = { open = true }) {
      Icon(icon, contentDescription = "Sort and shuffle", tint = tint)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      SortOrder.entries.forEach { order ->
        DropdownMenuItem(
          text = { Text(order.label) },
          leadingIcon = {
            if (order == current) Icon(Icons.Default.Check, contentDescription = null)
          },
          onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            open = false
            onSort(order)
          },
        )
      }
    }
  }
}
