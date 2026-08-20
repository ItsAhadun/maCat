package com.ahad.macat.ui.census

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahad.macat.data.Colour
import com.ahad.macat.ui.CatalogueViewModel
import com.ahad.macat.ui.components.Swatch
import kotlin.math.roundToInt

/**
 * The whole wardrobe as one bar of colour.
 *
 * Nothing new is stored for this: it is the colour tag every item already carries, counted. The
 * point is the question it answers that the catalogue otherwise cannot — what do I actually own,
 * in bulk — and the usual answer is "far more of one colour than I thought".
 *
 * Tapping a share filters the catalogue to it and hands you back to the grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColourCensusScreen(
  viewModel: CatalogueViewModel,
  onColourPicked: () -> Unit,
  onBack: () -> Unit,
) {
  val census by viewModel.colourCensus.collectAsStateWithLifecycle()
  val haptics = LocalHapticFeedback.current
  val total = census.sumOf { it.second }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Colour census") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    if (census.isEmpty()) {
      Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Nothing tagged with a colour yet", style = MaterialTheme.typography.bodyLarge)
      }
      return@Scaffold
    }

    val pick: (Colour) -> Unit = { colour ->
      haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
      viewModel.setColourFilter(colour)
      onColourPicked()
    }

    Column(
      Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Text(
        "$total ${if (total == 1) "item" else "items"} carry a colour",
        style = MaterialTheme.typography.titleMedium,
      )

      Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp))
      ) {
        census.forEach { (colour, count) ->
          Box(
            Modifier.weight(count.toFloat())
              .fillMaxHeight()
              .background(Color(colour.swatch))
              .clickable { pick(colour) }
              .semantics {
                contentDescription = "${colour.label}, $count of $total"
              }
          )
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        census
          .sortedByDescending { it.second }
          .forEach { (colour, count) -> CensusRow(colour, count, total, onClick = { pick(colour) }) }
      }
    }
  }
}

@Composable
private fun CensusRow(colour: Colour, count: Int, total: Int, onClick: () -> Unit) {
  val share = if (total == 0) 0 else (count * 100f / total).roundToInt()
  Row(
    Modifier.fillMaxWidth()
      .clip(MaterialTheme.shapes.small)
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Swatch(colour, size = 20.dp)
    Text(colour.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    Text("$count", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    Text(
      "$share%",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
