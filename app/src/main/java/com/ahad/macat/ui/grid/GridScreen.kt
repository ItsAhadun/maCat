package com.ahad.macat.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ahad.macat.data.Item
import com.ahad.macat.ui.CatalogueViewModel
import com.ahad.macat.ui.components.FilterBar
import com.ahad.macat.ui.components.SortMenu
import com.ahad.macat.ui.croppedPhotoModel

/** Decode budget for a thumbnail, before the framing rect widens it. */
private const val THUMBNAIL_DECODE_PX = 512

/**
 * Thumbnail overview; tapping an item jumps back to it full-screen in the feed.
 *
 * Also where the catalogue is managed in bulk: long-pressing a thumbnail starts a selection that
 * can be deleted, refiled or favourited in one go, which used to mean one round trip through the
 * feed per item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(viewModel: CatalogueViewModel, onBack: () -> Unit) {
  val items by viewModel.filteredItems.collectAsStateWithLifecycle()
  val filterState by viewModel.filterState.collectAsStateWithLifecycle()
  val visibility by viewModel.filterVisibility.collectAsStateWithLifecycle()
  val categories by viewModel.categories.collectAsStateWithLifecycle()
  val colours by viewModel.availableColours.collectAsStateWithLifecycle()
  val selection by viewModel.selection.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current

  var searching by rememberSaveable { mutableStateOf(false) }
  val selecting = selection.isNotEmpty()

  // Leaving the screen with a selection still live would come back as a mode the user did not
  // ask for, so the selection is dropped whenever the grid goes away.
  DisposableEffect(Unit) { onDispose { viewModel.clearSelection() } }

  Scaffold(
    topBar = {
      if (selecting) {
        SelectionBar(
          count = selection.size,
          categories = categories.map { it.name },
          onClose = viewModel::clearSelection,
          onDelete = viewModel::deleteSelected,
          onCategory = viewModel::setCategoryOfSelected,
          onFavourite = { viewModel.setFavouriteOnSelected(true) },
        )
      } else {
        TopAppBar(
          title = {
            if (searching) {
              SearchField(query = filterState.query, onQuery = viewModel::setQuery)
            } else {
              Text("My catalogue")
            }
          },
          navigationIcon = {
            IconButton(
              onClick = {
                if (searching) {
                  searching = false
                  viewModel.setQuery("")
                } else {
                  onBack()
                }
              }
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to feed")
            }
          },
          actions = {
            if (visibility.search && !searching) {
              IconButton(onClick = { searching = true }) {
                Icon(Icons.Default.Search, contentDescription = "Search")
              }
            }
            if (visibility.sort) {
              SortMenu(
                current = filterState.sort,
                onSort = viewModel::setSort,
                icon = Icons.AutoMirrored.Filled.List,
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }
          },
        )
      }
    }
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      FilterBar(
        visibility = visibility,
        state = filterState,
        categories = categories,
        colours = colours,
        onCategory = viewModel::setCategoryFilter,
        onColour = viewModel::setColourFilter,
        onToggleFavourites = viewModel::toggleFavouritesOnly,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
      )

      val list = items.orEmpty()
      if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            if (filterState.isNarrowed) "Nothing matches those filters" else "Nothing here yet",
            style = MaterialTheme.typography.bodyLarge,
          )
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 120.dp),
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(list, key = { it.id }) { item ->
            GridCell(
              item = item,
              selected = item.id in selection,
              selecting = selecting,
              photoModel =
                croppedPhotoModel(
                  context,
                  viewModel.photoFile(item),
                  item.crop,
                  THUMBNAIL_DECODE_PX,
                ),
              onTap = {
                if (selecting) {
                  viewModel.toggleSelected(item.id)
                } else {
                  viewModel.requestScrollTo(item.id)
                  onBack()
                }
              },
              onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.toggleSelected(item.id)
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GridCell(
  item: Item,
  selected: Boolean,
  selecting: Boolean,
  photoModel: Any,
  onTap: () -> Unit,
  onLongPress: () -> Unit,
) {
  Column(
    Modifier.clip(RoundedCornerShape(12.dp)).pointerInput(selecting) {
      detectTapGestures(
        onTap = { onTap() },
        onLongPress = { onLongPress() },
      )
    }
  ) {
    Box {
      AsyncImage(
        model = photoModel,
        contentDescription = item.displayName,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
      )
      if (selected) {
        Box(
          Modifier.fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        )
        Icon(
          Icons.Default.Check,
          contentDescription = "Selected",
          tint = MaterialTheme.colorScheme.onPrimary,
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(6.dp)
              .size(24.dp)
              .background(MaterialTheme.colorScheme.primary, CircleShape)
              .padding(3.dp),
        )
      }
      if (item.favourite) {
        Icon(
          Icons.Default.Star,
          contentDescription = "Favourite",
          tint = Color.White,
          modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(18.dp),
        )
      }
    }
    Text(
      text = item.displayName,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
  }
}

/** The contextual bar that replaces the title while a selection is live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
  count: Int,
  categories: List<String>,
  onClose: () -> Unit,
  onDelete: () -> Unit,
  onCategory: (String) -> Unit,
  onFavourite: () -> Unit,
) {
  var categoryMenuOpen by remember { mutableStateOf(false) }
  val haptics = LocalHapticFeedback.current
  TopAppBar(
    colors =
      TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer
      ),
    title = { Text("$count selected") },
    navigationIcon = {
      IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = "Cancel selection")
      }
    },
    actions = {
      IconButton(onClick = onFavourite) {
        Icon(Icons.Default.Star, contentDescription = "Mark as favourite")
      }
      Box {
        IconButton(onClick = { categoryMenuOpen = true }) {
          Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Change category")
        }
        DropdownMenu(
          expanded = categoryMenuOpen,
          onDismissRequest = { categoryMenuOpen = false },
        ) {
          categories.forEach { name ->
            DropdownMenuItem(
              text = { Text(name) },
              onClick = {
                categoryMenuOpen = false
                onCategory(name)
              },
            )
          }
        }
      }
      IconButton(
        onClick = {
          haptics.performHapticFeedback(HapticFeedbackType.Reject)
          onDelete()
        }
      ) {
        Icon(Icons.Default.Delete, contentDescription = "Delete selected")
      }
    },
  )
}

/** Dismissal is the top bar's back arrow, which also clears the query — see the caller. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
  TextField(
    value = query,
    onValueChange = onQuery,
    placeholder = { Text("Search") },
    singleLine = true,
    colors =
      TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
      ),
    trailingIcon = {
      if (query.isNotEmpty()) {
        IconButton(onClick = { onQuery("") }) {
          Icon(Icons.Default.Close, contentDescription = "Clear search")
        }
      }
    },
    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
  )
}
