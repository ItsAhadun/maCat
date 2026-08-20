package com.ahad.macat.ui.feed

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withStateAtLeast
import coil3.compose.AsyncImage
import com.ahad.macat.data.Category
import com.ahad.macat.data.Colour
import com.ahad.macat.data.FilterState
import com.ahad.macat.data.FilterVisibility
import com.ahad.macat.data.Item
import com.ahad.macat.data.SortOrder
import com.ahad.macat.ui.CatalogueViewModel
import com.ahad.macat.ui.components.FilterBar
import com.ahad.macat.ui.components.SortMenu
import com.ahad.macat.ui.croppedPhotoModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.drop

/** Full-screen, vertically swipeable item browser — the app's main screen. */
@Composable
fun FeedScreen(
  viewModel: CatalogueViewModel,
  onOpenGrid: () -> Unit,
  onAddItem: () -> Unit,
  onBulkAdd: () -> Unit,
  onEditItem: (Long) -> Unit,
  onOpenCensus: () -> Unit,
  onOpenBin: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val items by viewModel.filteredItems.collectAsStateWithLifecycle()
  val filterState by viewModel.filterState.collectAsStateWithLifecycle()
  val visibility by viewModel.filterVisibility.collectAsStateWithLifecycle()
  val categories by viewModel.categories.collectAsStateWithLifecycle()
  val colours by viewModel.availableColours.collectAsStateWithLifecycle()

  val context = LocalContext.current
  // Photos fill the window here, so a framed one has to decode at least that big.
  val feedDecodePx = LocalWindowInfo.current.containerSize.height.coerceAtLeast(1080)
  val backupLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
      uri ->
      uri?.let(viewModel::exportBackup)
    }
  val restoreLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let(viewModel::importBackup)
    }
  // The photo is the point of this screen, so the chrome can be got out of the way. Survives
  // navigation away and back, because coming back to hidden chrome you did not re-hide is worse
  // than coming back to visible chrome you did.
  var chromeVisible by rememberSaveable { mutableStateOf(true) }

  Box(Modifier.fillMaxSize().background(Color.Black)) {
    val list = items
    when {
      list == null -> {} // loading — keep the screen black for a moment
      list.isEmpty() ->
        EmptyState(
          state = filterState,
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

        // Another screen asked us to show a specific item. A request the feed makes for itself
        // (tapping edit) is meant for the composition that comes back once that screen closes,
        // so only one that was already pending when this composition started is consumed here.
        //
        // This scrolls after the fact rather than opening on the item via `initialPage`, and it
        // has to: `rememberPagerState` is saveable-backed, so on a plain return it restores the
        // page it was left on and ignores `initialPage` entirely. Keying the state by the pending
        // item does make `initialPage` apply, but then a plain back-navigation lands in a
        // different saved slot and stops restoring the user's place — both were measured on
        // device. Position restore is worth more than the animation that wanted the other order.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val pendingScrollTo = remember { viewModel.scrollToItemId.value }
        LaunchedEffect(Unit) {
          val targetId = pendingScrollTo ?: return@LaunchedEffect
          val index = list.indexOfFirst { it.id == targetId }
          if (index >= 0) {
            // NavDisplay is still animating us in; scroll once we are the settled screen.
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {}
            pagerState.scrollToPage(basePage() + index)
          }
          viewModel.clearScrollRequest()
        }
        // Start from the top when the filtering changes — the pages now index into a different
        // list. It can change while the feed is off-screen (the grid has the same controls), so
        // remember the state the current page belongs to instead of resetting whenever this
        // screen is composed afresh, which would lose the page on every trip back.
        var pagedFilter by rememberSaveable { mutableStateOf(filterState.pageKey()) }
        LaunchedEffect(filterState) {
          if (filterState.pageKey() != pagedFilter) {
            pagedFilter = filterState.pageKey()
            // Back to the page the pager starts on: the page count has just changed with the
            // list, so a page worked out from the current one can fall outside the new range.
            if (viewModel.scrollToItemId.value == null) pagerState.scrollToPage(pageCount / 2)
          }
        }

        // Gate pinch-to-zoom: never while a page swipe/fling is in flight, and not within a
        // short window after it settles, so fast scrolling can't register as a pinch.
        var scrollSettledAt by remember { mutableLongStateOf(0L) }
        LaunchedEffect(pagerState) {
          snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) scrollSettledAt = SystemClock.uptimeMillis()
          }
        }
        val pinchAllowed =
          remember(pagerState) {
            {
              !pagerState.isScrollInProgress &&
                SystemClock.uptimeMillis() - scrollSettledAt >= PINCH_DEBOUNCE_AFTER_SCROLL_MS
            }
          }

        // A tick as each page settles under the finger. `drop(1)` because snapshotFlow emits the
        // current value on collection, and arriving at the feed is not a page change.
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(pagerState) {
          snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
        }

        VerticalPager(
          state = pagerState,
          modifier = Modifier.fillMaxSize(),
          beyondViewportPageCount = 1,
        ) { page ->
          val item = list[page.mod(list.size)]
          ItemPage(
            item = item,
            photoModel =
              croppedPhotoModel(context, viewModel.photoFile(item), item.crop, feedDecodePx),
            pinchAllowed = pinchAllowed,
            chromeVisible = chromeVisible,
            onToggleChrome = { chromeVisible = !chromeVisible },
            onEdit = {
              // Come back to this item when the edit screen closes.
              viewModel.requestScrollTo(item.id)
              onEditItem(item.id)
            },
            onToggleFavourite = { viewModel.setFavourite(item, !item.favourite) },
            onDelete = { viewModel.deleteItem(item) },
          )
        }
      }
    }

    AnimatedVisibility(
      visible = chromeVisible,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter),
    ) {
      TopOverlay(
        state = filterState,
        visibility = visibility,
        categories = categories,
        colours = colours,
        onCategory = viewModel::setCategoryFilter,
        onColour = viewModel::setColourFilter,
        onToggleFavourites = viewModel::toggleFavouritesOnly,
        onSort = viewModel::setSort,
        onOpenGrid = onOpenGrid,
        onAddItem = onAddItem,
        onBulkAdd = onBulkAdd,
        onBackup = { backupLauncher.launch(defaultBackupName()) },
        onRestore = { restoreLauncher.launch(BACKUP_MIME_TYPES) },
        onOpenCensus = onOpenCensus,
        onOpenBin = onOpenBin,
        onOpenSettings = onOpenSettings,
      )
    }
  }
}

/**
 * What the pager's position is valid for. Two filter states that select the same list in the same
 * order can keep the current page; anything else has to go back to the start.
 */
private fun FilterState.pageKey(): String =
  listOf(category, colour?.name, favouritesOnly, query.trim(), sort.name, shuffleSeed)
    .joinToString("|")

/** Cap on transient pinch zoom. */
private const val MAX_PINCH_ZOOM = 4f

/** Pinches that begin within this window after a page scroll settles are ignored. */
private const val PINCH_DEBOUNCE_AFTER_SCROLL_MS = 150L

@Composable
private fun ItemPage(
  item: Item,
  photoModel: Any,
  pinchAllowed: () -> Boolean,
  chromeVisible: Boolean,
  onToggleChrome: () -> Unit,
  onEdit: () -> Unit,
  onToggleFavourite: () -> Unit,
  onDelete: () -> Unit,
) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    // On wide layouts (unfolded Fold, landscape) show the whole photo instead of cropping.
    val contentScale = if (maxWidth > maxHeight) ContentScale.Fit else ContentScale.Crop

    // Transient pinch-to-zoom: the photo tracks the fingers exactly while pinching (snap spec)
    // and springs back to normal when released.
    var pinching by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var pinchOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    val shownZoom by
      animateFloatAsState(
        targetValue = zoom,
        animationSpec = if (pinching) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "pinchZoom",
      )
    val shownPan by
      animateOffsetAsState(
        targetValue = pan,
        animationSpec = if (pinching) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "pinchPan",
      )

    AsyncImage(
      model = photoModel,
      contentDescription = item.displayName,
      contentScale = contentScale,
      modifier =
        Modifier.fillMaxSize()
          .pointerInput(pinchAllowed) {
            detectPinchOrTap(
              pinchAllowed = pinchAllowed,
              onTap = onToggleChrome,
              onStart = { centroid ->
                pinchOrigin = TransformOrigin(centroid.x / size.width, centroid.y / size.height)
                pinching = true
              },
              onPinch = { panChange, zoomChange ->
                zoom = (zoom * zoomChange).coerceIn(1f, MAX_PINCH_ZOOM)
                pan += panChange
              },
              onEnd = {
                pinching = false
                zoom = 1f
                pan = Offset.Zero
              },
            )
          }
          .graphicsLayer {
            scaleX = shownZoom
            scaleY = shownZoom
            translationX = shownPan.x
            translationY = shownPan.y
            transformOrigin = pinchOrigin
          },
    )

    AnimatedVisibility(
      visible = chromeVisible,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter),
    ) {
      Box(Modifier.fillMaxWidth()) {
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
            Modifier.fillMaxWidth()
              .align(Alignment.BottomCenter)
              .navigationBarsPadding()
              .padding(16.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              text =
                listOfNotNull(item.category, item.colour?.label).joinToString(" · ").uppercase(),
              color = Color.White.copy(alpha = 0.7f),
              style = MaterialTheme.typography.labelLarge,
            )
            Text(
              text = item.displayName,
              color = Color.White,
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          FavouriteButton(favourite = item.favourite, onToggle = onToggleFavourite)
          IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit item", tint = Color.White)
          }
          DeleteButton(onDelete = onDelete)
        }
      }
    }
  }
}

@Composable
private fun FavouriteButton(favourite: Boolean, onToggle: () -> Unit) {
  val haptics = LocalHapticFeedback.current
  IconButton(
    onClick = {
      haptics.performHapticFeedback(
        if (favourite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
      )
      onToggle()
    }
  ) {
    Icon(
      Icons.Default.Star,
      contentDescription = if (favourite) "Remove from favourites" else "Add to favourites",
      tint = if (favourite) Color.White else Color.White.copy(alpha = 0.45f),
    )
  }
}

/**
 * No confirmation dialog: the item goes to the bin rather than being destroyed, the snackbar
 * offers Undo immediately, and Recently Deleted holds it for a month after that. Asking "are you
 * sure" before something this recoverable is friction pretending to be safety.
 */
@Composable
private fun DeleteButton(onDelete: () -> Unit) {
  val haptics = LocalHapticFeedback.current
  IconButton(
    onClick = {
      haptics.performHapticFeedback(HapticFeedbackType.Reject)
      onDelete()
    }
  ) {
    Icon(Icons.Default.Delete, contentDescription = "Delete item", tint = Color.White)
  }
}

/**
 * One detector for both gestures the photo answers to: a pinch, and a tap to hide the chrome.
 *
 * They share a detector rather than getting one each because a second `pointerInput` running
 * `detectTapGestures` alongside this **silently kills paging** — that helper consumes the initial
 * down, and the pager then never starts a drag. Nothing here consumes anything until a pinch is
 * genuinely under way, so a one-finger swipe still reaches the pager untouched.
 *
 * A second finger claims the gesture outright, and from then on events are consumed so the pager
 * can't scroll mid-pinch. Waiting for pinch movement past touch slop instead would race the
 * pager's own slop detection, which wins whenever one finger moves more than the other (as natural
 * pinches do) and kills the zoom. [pinchAllowed] vetoes pinches that begin while the pager is
 * scrolling or has only just settled, so fast paging with a stray second finger never zooms.
 *
 * A gesture that never moves past touch slop and never grows a second finger is a tap.
 */
private suspend fun PointerInputScope.detectPinchOrTap(
  pinchAllowed: () -> Boolean,
  onTap: () -> Unit,
  onStart: (centroid: Offset) -> Unit,
  onPinch: (pan: Offset, zoom: Float) -> Unit,
  onEnd: () -> Unit,
) {
  awaitEachGesture {
    var active = false
    // Anything that rules the gesture out as a tap: it moved, it grew a second finger, or
    // somebody else claimed it.
    var notATap = false
    val down = awaitFirstDown(requireUnconsumed = false)
    while (true) {
      val event = awaitPointerEvent()
      if (event.changes.none { it.pressed }) break
      if (!active) {
        val moved =
          event.changes.firstOrNull { it.id == down.id }?.let {
            (it.position - down.position).getDistance() > viewConfiguration.touchSlop
          } ?: false
        if (moved) notATap = true
        if (event.changes.count { it.pressed } < 2) continue // one finger: the pager's gesture
        notATap = true
        if (event.changes.any { it.isConsumed }) break // the pager already claimed this gesture
        if (!pinchAllowed()) break // mid-swipe or just settled — never zoom
        active = true
        onStart(event.calculateCentroid())
      }
      onPinch(event.calculatePan(), event.calculateZoom())
      event.changes.forEach { if (it.positionChanged()) it.consume() }
    }
    when {
      active -> onEnd()
      !notATap -> onTap()
    }
  }
}

@Composable
private fun TopOverlay(
  state: FilterState,
  visibility: FilterVisibility,
  categories: List<Category>,
  colours: List<Colour>,
  onCategory: (String?) -> Unit,
  onColour: (Colour?) -> Unit,
  onToggleFavourites: () -> Unit,
  onSort: (SortOrder) -> Unit,
  onOpenGrid: () -> Unit,
  onAddItem: () -> Unit,
  onBulkAdd: () -> Unit,
  onBackup: () -> Unit,
  onRestore: () -> Unit,
  onOpenCensus: () -> Unit,
  onOpenBin: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var addMenuOpen by remember { mutableStateOf(false) }
  var overflowMenuOpen by remember { mutableStateOf(false) }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(
          Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))
        )
        .statusBarsPadding()
        .padding(bottom = 8.dp),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.End,
    ) {
      Box(Modifier.weight(1f))
      if (visibility.sort) {
        SortMenu(
          current = state.sort,
          onSort = onSort,
          icon = Icons.Default.Refresh,
          tint = Color.White,
        )
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
      Box {
        IconButton(onClick = { overflowMenuOpen = true }) {
          Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
        }
        DropdownMenu(expanded = overflowMenuOpen, onDismissRequest = { overflowMenuOpen = false }) {
          listOf(
              "Colour census" to onOpenCensus,
              "Recently deleted" to onOpenBin,
              "Back up catalogue" to onBackup,
              "Restore catalogue" to onRestore,
              "Settings" to onOpenSettings,
            )
            .forEach { (label, action) ->
              DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                  overflowMenuOpen = false
                  action()
                },
              )
            }
        }
      }
    }

    FilterBar(
      visibility = visibility,
      state = state,
      categories = categories,
      colours = colours,
      onCategory = onCategory,
      onColour = onColour,
      onToggleFavourites = onToggleFavourites,
      onDark = true,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

/** MIME types a saved backup may be reported as, for the "restore" file picker filter. */
private val BACKUP_MIME_TYPES =
  arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")

private fun defaultBackupName(): String {
  val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
  return "maCat-backup-$stamp.zip"
}

@Composable
private fun EmptyState(
  state: FilterState,
  onAddItem: () -> Unit,
  onBulkAdd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = if (state.isNarrowed) "Nothing matches those filters" else "Your catalogue is empty",
      color = Color.White,
      style = MaterialTheme.typography.titleLarge,
    )
    if (!state.isNarrowed) {
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
}
