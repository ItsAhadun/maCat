package com.ahad.macat.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withStateAtLeast
import coil3.compose.AsyncImage
import com.ahad.macat.data.Category
import com.ahad.macat.data.Item
import com.ahad.macat.ui.CatalogueViewModel

/** Full-screen, vertically swipeable item browser — the app's main screen. */
@Composable
fun FeedScreen(
  viewModel: CatalogueViewModel,
  onOpenGrid: () -> Unit,
  onAddItem: () -> Unit,
  onBulkAdd: () -> Unit,
  onEditItem: (Long) -> Unit,
) {
  val items by viewModel.filteredItems.collectAsStateWithLifecycle()
  val filter by viewModel.filter.collectAsStateWithLifecycle()
  val scrollToItemId by viewModel.scrollToItemId.collectAsStateWithLifecycle()
  var itemToDelete by remember { mutableStateOf<Item?>(null) }

  Box(Modifier.fillMaxSize().background(Color.Black)) {
    val list = items
    when {
      list == null -> {} // loading — keep the screen black for a moment
      list.isEmpty() ->
        EmptyState(
          filter = filter,
          onAddItem = onAddItem,
          onBulkAdd = onBulkAdd,
          modifier = Modifier.align(Alignment.Center),
        )
      else -> {
        // Loop the feed: with more than one item the pager gets a huge page count and every
        // page maps to list[page % size], so swiping past the last item wraps to the first.
        val looping = list.size > 1
        val pageCount = if (looping) list.size * 1000 else 1
        val pagerState =
          rememberPagerState(initialPage = if (looping) pageCount / 2 else 0) { pageCount }
        // A page congruent to item 0, near the current position.
        fun basePage() = pagerState.currentPage - pagerState.currentPage.mod(list.size)

        // The grid asked us to show a specific item. During the pop transition NavDisplay
        // composes this screen transiently and then recreates it with restored pager state,
        // so only consume the request once the settled composition is RESUMED.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(scrollToItemId, list) {
          val targetId = scrollToItemId ?: return@LaunchedEffect
          val index = list.indexOfFirst { it.id == targetId }
          if (index >= 0) {
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {}
            pagerState.scrollToPage(basePage() + index)
          }
          viewModel.clearScrollRequest()
        }
        // Start from the top when the category filter changes.
        LaunchedEffect(filter) {
          if (pagerState.currentPage.mod(list.size) != 0 && viewModel.scrollToItemId.value == null) {
            pagerState.scrollToPage(basePage())
          }
        }

        VerticalPager(
          state = pagerState,
          modifier = Modifier.fillMaxSize(),
          beyondViewportPageCount = 1,
        ) { page ->
          val item = list[page.mod(list.size)]
          ItemPage(
            item = item,
            photoModel = viewModel.photoFile(item),
            onEdit = {
              // Come back to this item when the edit screen closes.
              viewModel.requestScrollTo(item.id)
              onEditItem(item.id)
            },
            onDelete = { itemToDelete = item },
          )
        }
      }
    }

    TopOverlay(
      filter = filter,
      onFilterChange = viewModel::setFilter,
      onOpenGrid = onOpenGrid,
      onAddItem = onAddItem,
      onBulkAdd = onBulkAdd,
      modifier = Modifier.align(Alignment.TopCenter),
    )
  }

  itemToDelete?.let { item ->
    AlertDialog(
      onDismissRequest = { itemToDelete = null },
      title = { Text("Delete “${item.name}”?") },
      text = { Text("The item and its photo will be removed from your catalogue.") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteItem(item)
            itemToDelete = null
          }
        ) {
          Text("Delete")
        }
      },
      dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun ItemPage(
  item: Item,
  photoModel: Any,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    // On wide layouts (unfolded Fold, landscape) show the whole photo instead of cropping.
    val contentScale = if (maxWidth > maxHeight) ContentScale.Fit else ContentScale.Crop
    AsyncImage(
      model = photoModel,
      contentDescription = item.name,
      contentScale = contentScale,
      modifier = Modifier.fillMaxSize(),
    )

    Box(
      Modifier.fillMaxWidth()
        .height(200.dp)
        .align(Alignment.BottomCenter)
        .background(
          Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
        )
    )

    Row(
      modifier =
        Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      Column(Modifier.weight(1f)) {
        Text(
          text = item.category.label.uppercase(),
          color = Color.White.copy(alpha = 0.7f),
          style = MaterialTheme.typography.labelLarge,
        )
        Text(
          text = item.name,
          color = Color.White,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      IconButton(onClick = onEdit) {
        Icon(Icons.Default.Edit, contentDescription = "Edit item", tint = Color.White)
      }
      IconButton(onClick = onDelete) {
        Icon(Icons.Default.Delete, contentDescription = "Delete item", tint = Color.White)
      }
    }
  }
}

@Composable
private fun TopOverlay(
  filter: Category?,
  onFilterChange: (Category?) -> Unit,
  onOpenGrid: () -> Unit,
  onAddItem: () -> Unit,
  onBulkAdd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var addMenuOpen by remember { mutableStateOf(false) }

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        .statusBarsPadding()
        .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      Modifier.weight(1f).horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      FeedFilterChip(label = "All", selected = filter == null, onClick = { onFilterChange(null) })
      Category.entries.forEach { category ->
        FeedFilterChip(
          label = category.label,
          selected = filter == category,
          onClick = { onFilterChange(category) },
        )
      }
    }
    IconButton(onClick = onOpenGrid) {
      Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Grid view", tint = Color.White)
    }
    Box {
      IconButton(onClick = { addMenuOpen = true }) {
        Icon(Icons.Default.Add, contentDescription = "Add items", tint = Color.White)
      }
      DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
        DropdownMenuItem(
          text = { Text("Add item") },
          onClick = {
            addMenuOpen = false
            onAddItem()
          },
        )
        DropdownMenuItem(
          text = { Text("Bulk add") },
          onClick = {
            addMenuOpen = false
            onBulkAdd()
          },
        )
      }
    }
  }
}

@Composable
private fun FeedFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
    colors =
      FilterChipDefaults.filterChipColors(
        labelColor = Color.White,
        selectedContainerColor = Color.White,
        selectedLabelColor = Color.Black,
      ),
    border =
      FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = Color.White.copy(alpha = 0.5f),
      ),
  )
}

@Composable
private fun EmptyState(
  filter: Category?,
  onAddItem: () -> Unit,
  onBulkAdd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = if (filter == null) "Your catalogue is empty" else "No ${filter.label.lowercase()} yet",
      color = Color.White,
      style = MaterialTheme.typography.titleLarge,
    )
    Row(
      modifier = Modifier.padding(top = 24.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Button(
        onClick = onAddItem,
        colors =
          ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
      ) {
        Text("Add an item")
      }
      OutlinedButton(
        onClick = onBulkAdd,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
      ) {
        Text("Bulk add")
      }
    }
  }
}
