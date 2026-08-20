package com.ahad.macat.ui.bin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ahad.macat.data.BIN_RETENTION_MS
import com.ahad.macat.data.Item
import com.ahad.macat.ui.CatalogueViewModel
import com.ahad.macat.ui.croppedPhotoModel
import java.util.concurrent.TimeUnit
import kotlin.math.max

private const val THUMBNAIL_DECODE_PX = 256

/**
 * The bin. Deleting an item puts it here rather than destroying it, and its photo file stays on
 * disk the whole time, so Restore has something to bring back. Anything older than the retention
 * window is cleared on the next app start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(viewModel: CatalogueViewModel, onBack: () -> Unit) {
  val deleted by viewModel.deletedItems.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var confirmEmpty by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Recently deleted") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (deleted.isNotEmpty()) {
            TextButton(onClick = { confirmEmpty = true }) { Text("Empty bin") }
          }
        },
      )
    }
  ) { padding ->
    if (deleted.isEmpty()) {
      Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Nothing deleted", style = MaterialTheme.typography.bodyLarge)
      }
      return@Scaffold
    }

    Column(Modifier.padding(padding).fillMaxSize()) {
      Text(
        "Items are kept for ${TimeUnit.MILLISECONDS.toDays(BIN_RETENTION_MS)} days, then removed " +
          "for good.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )
      LazyColumn(Modifier.fillMaxSize()) {
        items(deleted, key = { it.id }) { item ->
          BinRow(
            item = item,
            photoModel =
              croppedPhotoModel(context, viewModel.photoFile(item), item.crop, THUMBNAIL_DECODE_PX),
            onRestore = { viewModel.restoreItem(item) },
            onDeleteForever = { viewModel.purgeItem(item) },
          )
        }
      }
    }
  }

  if (confirmEmpty) {
    AlertDialog(
      onDismissRequest = { confirmEmpty = false },
      title = { Text("Empty the bin?") },
      text = {
        Text("${deleted.size} ${if (deleted.size == 1) "item" else "items"} and their photos will be removed for good. This cannot be undone.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.emptyBin()
            confirmEmpty = false
          }
        ) {
          Text("Empty bin")
        }
      },
      dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun BinRow(
  item: Item,
  photoModel: Any,
  onRestore: () -> Unit,
  onDeleteForever: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    AsyncImage(
      model = photoModel,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
    )
    Column(Modifier.weight(1f)) {
      Text(
        item.displayName,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        daysLeftLabel(item.deletedAt),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    TextButton(onClick = onRestore) { Text("Restore") }
    IconButton(onClick = onDeleteForever) {
      Icon(
        Icons.Default.Delete,
        contentDescription = "Delete “${item.displayName}” for good",
      )
    }
  }
}

/** How long this item has left before the purge takes it. */
private fun daysLeftLabel(deletedAt: Long?): String {
  if (deletedAt == null) return ""
  val remaining = BIN_RETENTION_MS - (System.currentTimeMillis() - deletedAt)
  val days = TimeUnit.MILLISECONDS.toDays(max(remaining, 0L))
  return when {
    remaining <= 0L -> "Removed on next launch"
    days == 0L -> "Less than a day left"
    days == 1L -> "1 day left"
    else -> "$days days left"
  }
}
