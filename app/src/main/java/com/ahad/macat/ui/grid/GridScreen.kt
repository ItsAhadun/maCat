package com.ahad.macat.ui.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ahad.macat.data.Category
import com.ahad.macat.ui.CatalogueViewModel

/** Thumbnail overview; tapping an item jumps back to it full-screen in the feed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(viewModel: CatalogueViewModel, onBack: () -> Unit) {
  val items by viewModel.filteredItems.collectAsStateWithLifecycle()
  val filter by viewModel.filter.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("My catalogue") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to feed")
          }
        },
      )
    }
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = filter == null,
          onClick = { viewModel.setFilter(null) },
          label = { Text("All") },
        )
        Category.entries.forEach { category ->
          FilterChip(
            selected = filter == category,
            onClick = { viewModel.setFilter(category) },
            label = { Text(category.label) },
          )
        }
      }

      val list = items.orEmpty()
      if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("Nothing here yet", style = MaterialTheme.typography.bodyLarge)
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 120.dp),
          modifier = Modifier.fillMaxSize(),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(list, key = { it.id }) { item ->
            Column(
              Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                viewModel.requestScrollTo(item.id)
                onBack()
              }
            ) {
              AsyncImage(
                model = viewModel.photoFile(item),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier =
                  Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
              )
              Text(
                text = item.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
              )
            }
          }
        }
      }
    }
  }
}
