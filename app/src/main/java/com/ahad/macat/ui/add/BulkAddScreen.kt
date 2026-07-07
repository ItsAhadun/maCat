package com.ahad.macat.ui.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ahad.macat.ui.CatalogueViewModel

/** Two-step bulk add: 1) collect photos (camera or gallery), 2) name + categorize each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkAddScreen(viewModel: CatalogueViewModel, onDone: () -> Unit) {
  var step by rememberSaveable { mutableIntStateOf(0) }
  var showCamera by rememberSaveable { mutableStateOf(false) }
  val entries = viewModel.bulkEntries

  // All photos were removed in the annotate step — go back to capturing.
  LaunchedEffect(entries.size) { if (entries.isEmpty()) step = 0 }

  val pickPhotos =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
      uris.forEach(viewModel::bulkAddPhoto)
    }

  val cancel = {
    viewModel.bulkClear()
    onDone()
  }

  if (showCamera) {
    // Stays open between shots so a whole wardrobe can be captured in one go.
    CameraCaptureScreen(
      newCaptureFile = viewModel::newCaptureFile,
      onPhotoCaptured = viewModel::bulkAddPhoto,
      onClose = { showCamera = false },
      captureCount = entries.size,
    )
    return
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(if (step == 0) "Bulk add — photos" else "Bulk add — details (${entries.size})")
        },
        navigationIcon = {
          IconButton(onClick = { if (step == 0) cancel() else step = 0 }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = { TextButton(onClick = cancel) { Text("Cancel") } },
      )
    }
  ) { padding ->
    if (step == 0) {
      CaptureStep(
        viewModel = viewModel,
        onTakePhoto = { showCamera = true },
        onPickPhotos = {
          pickPhotos.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        onNext = { step = 1 },
        modifier = Modifier.padding(padding),
      )
    } else {
      AnnotateStep(
        viewModel = viewModel,
        onSaveAll = {
          viewModel.saveBulkEntries()
          onDone()
        },
        modifier = Modifier.padding(padding),
      )
    }
  }
}

@Composable
private fun CaptureStep(
  viewModel: CatalogueViewModel,
  onTakePhoto: () -> Unit,
  onPickPhotos: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val entries = viewModel.bulkEntries

  Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    if (entries.isEmpty()) {
      Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
          "Take a photo of each item,\nthen add their details in the next step.",
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        itemsIndexed(entries) { index, entry ->
          Box {
            AsyncImage(
              model = entry.photoUri,
              contentDescription = "Photo ${index + 1}",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
            )
            FilledIconButton(
              onClick = { viewModel.bulkRemoveEntry(index) },
              modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
              shape = CircleShape,
              colors =
                IconButtonDefaults.filledIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
                ),
            ) {
              Icon(
                Icons.Default.Close,
                contentDescription = "Remove photo",
                tint = MaterialTheme.colorScheme.surface,
              )
            }
          }
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Button(onClick = onTakePhoto, modifier = Modifier.weight(1f)) { Text("Take photo") }
      OutlinedButton(onClick = onPickPhotos, modifier = Modifier.weight(1f)) {
        Text("Pick from gallery")
      }
    }
    Button(onClick = onNext, enabled = entries.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
      Text("Next — add details (${entries.size})")
    }
  }
}

@Composable
private fun AnnotateStep(
  viewModel: CatalogueViewModel,
  onSaveAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val entries = viewModel.bulkEntries
  val pagerState = rememberPagerState(pageCount = { entries.size })

  Column(modifier.fillMaxSize().imePadding()) {
    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
      val entry = entries.getOrNull(page) ?: return@HorizontalPager
      Column(
        modifier =
          Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(
          modifier = Modifier.widthIn(max = 480.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Text(
            "Item ${page + 1} of ${entries.size} — swipe for the next one",
            style = MaterialTheme.typography.labelLarge,
          )
          AsyncImage(
            model = entry.photoUri,
            contentDescription = "Photo ${page + 1}",
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(16.dp)),
          )
          OutlinedTextField(
            value = entry.name,
            onValueChange = { viewModel.bulkUpdateEntry(page, entry.copy(name = it)) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          CategorySelector(
            selected = entry.category,
            onSelect = { viewModel.bulkUpdateEntry(page, entry.copy(category = it)) },
          )
          TextButton(onClick = { viewModel.bulkRemoveEntry(page) }) { Text("Remove this photo") }
        }
      }
    }

    val unnamed = entries.count { it.name.isBlank() }
    Button(
      onClick = onSaveAll,
      enabled = entries.isNotEmpty() && unnamed == 0,
      modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
      Text(
        if (unnamed == 0) "Save all ${entries.size} items"
        else "$unnamed still need a name"
      )
    }
  }
}
