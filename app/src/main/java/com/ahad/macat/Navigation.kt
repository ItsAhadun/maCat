package com.ahad.macat

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.ahad.macat.ui.CatalogueViewModel
import com.ahad.macat.ui.add.AddItemScreen
import com.ahad.macat.ui.add.BulkAddScreen
import com.ahad.macat.ui.feed.FeedScreen
import com.ahad.macat.ui.grid.GridScreen

@Composable
fun MainNavigation() {
  val appContext = LocalContext.current.applicationContext
  // Activity-scoped: feed, grid and add flows share the catalogue and filter state.
  val viewModel: CatalogueViewModel = viewModel {
    CatalogueViewModel((appContext as MaCatApp).repository)
  }
  val backStack = rememberNavBackStack(Feed)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Feed> {
          FeedScreen(
            viewModel = viewModel,
            onOpenGrid = { backStack.add(Grid) },
            onAddItem = { backStack.add(AddItem()) },
            onBulkAdd = { backStack.add(BulkAdd) },
            onEditItem = { itemId -> backStack.add(AddItem(itemId)) },
          )
        }
        entry<Grid> { GridScreen(viewModel = viewModel, onBack = { backStack.removeLastOrNull() }) }
        entry<AddItem> { key ->
          AddItemScreen(
            viewModel = viewModel,
            itemId = key.itemId,
            onDone = { backStack.removeLastOrNull() },
          )
        }
        entry<BulkAdd> {
          BulkAddScreen(viewModel = viewModel, onDone = { backStack.removeLastOrNull() })
        }
      },
  )
}
