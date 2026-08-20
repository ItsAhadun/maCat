package com.ahad.macat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.ahad.macat.ui.CatalogueViewModel
import com.ahad.macat.ui.add.AddItemScreen
import com.ahad.macat.ui.add.BulkAddScreen
import com.ahad.macat.ui.bin.RecentlyDeletedScreen
import com.ahad.macat.ui.census.ColourCensusScreen
import com.ahad.macat.ui.feed.FeedScreen
import com.ahad.macat.ui.grid.GridScreen
import com.ahad.macat.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val app = LocalContext.current.applicationContext as MaCatApp
  // Activity-scoped: every screen shares the catalogue, the filters and the selection.
  val viewModel: CatalogueViewModel = viewModel {
    CatalogueViewModel(app.repository, app.backupManager, app.settings)
  }
  val backStack = rememberNavBackStack(Feed)

  // One snackbar host for the whole app rather than one per screen. NavDisplay destroys every
  // entry that is not on top, so a host owned by the feed would be gone exactly when the grid
  // deletes a selection — and the Undo for it would surface minutes later, on the wrong screen.
  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(Unit) {
    viewModel.messages.collect { message ->
      val result =
        snackbarHostState.showSnackbar(
          message = message.text,
          actionLabel = message.actionLabel,
          duration = SnackbarDuration.Short,
        )
      if (result == SnackbarResult.ActionPerformed) message.action?.invoke()
    }
  }

  Box(Modifier.fillMaxSize()) {
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
              onOpenCensus = { backStack.add(ColourCensus) },
              onOpenBin = { backStack.add(RecentlyDeleted) },
              onOpenSettings = { backStack.add(Settings) },
            )
          }
          entry<Grid> {
            GridScreen(viewModel = viewModel, onBack = { backStack.removeLastOrNull() })
          }
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
          entry<ColourCensus> {
            ColourCensusScreen(
              viewModel = viewModel,
              // Picking a colour is a filter and a destination at once: swap the census for the
              // grid, so Back does not land on the census that was just acted on.
              onColourPicked = {
                backStack.removeLastOrNull()
                backStack.add(Grid)
              },
              onBack = { backStack.removeLastOrNull() },
            )
          }
          entry<RecentlyDeleted> {
            RecentlyDeletedScreen(viewModel = viewModel, onBack = { backStack.removeLastOrNull() })
          }
          entry<Settings> {
            SettingsScreen(viewModel = viewModel, onBack = { backStack.removeLastOrNull() })
          }
        },
    )
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp),
    )
  }
}
