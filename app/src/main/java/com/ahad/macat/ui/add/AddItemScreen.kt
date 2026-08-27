package com.ahad.macat.ui.add

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ahad.macat.data.Category
import com.ahad.macat.data.Colour
import com.ahad.macat.data.CropRect
import com.ahad.macat.data.autoName
import com.ahad.macat.ui.CatalogueViewModel
import com.ahad.macat.ui.components.ColourPickerGrid
import com.ahad.macat.ui.components.Swatch
import com.ahad.macat.ui.components.SwatchRow
import com.ahad.macat.ui.croppedPhotoModel

/**
 * Height of the photo preview; its width follows the feed shape, so it previews the real thing.
 * Shared with the bulk-add details step.
 */
internal val PREVIEW_HEIGHT = 340.dp

/** Decode budget for that preview. */
internal const val PREVIEW_DECODE_PX = 1024

/** Add a new item, or edit an existing one when [itemId] is set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(viewModel: CatalogueViewModel, itemId: Long?, onDone: () -> Unit) {
  val editingItem = remember(itemId) { itemId?.let(viewModel::itemById) }
  val categories by viewModel.categories.collectAsStateWithLifecycle()
  val haptics = LocalHapticFeedback.current

  var name by rememberSaveable { mutableStateOf(editingItem?.name ?: "") }
  var category by rememberSaveable {
    mutableStateOf(editingItem?.category ?: viewModel.defaultCategory)
  }
  var colours by
    rememberSaveable(stateSaver = ColourListSaver) {
      mutableStateOf(editingItem?.colours.orEmpty())
    }
  // Once the colours have been set by hand, detection stops overruling them.
  var colourPicked by rememberSaveable { mutableStateOf(false) }
  // Newly taken/picked photo; null while editing means "keep the current photo".
  var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
  var crop by rememberSaveable(stateSaver = CropRectSaver) { mutableStateOf(editingItem?.crop) }
  var showCamera by rememberSaveable { mutableStateOf(false) }
  var showFraming by rememberSaveable { mutableStateOf(false) }

  val context = LocalContext.current
  val photo: Any? = photoUri ?: editingItem?.let(viewModel::photoFile)

  val pickPhoto =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        photoUri = uri
        crop = null
      }
    }

  // Tag the photo as soon as there is one: a new photo, or an older item that predates colours.
  LaunchedEffect(photoUri) {
    if (colourPicked) return@LaunchedEffect
    val source =
      photoUri
        ?: editingItem
          ?.takeIf { it.colours.isNullOrEmpty() }
          ?.let { Uri.fromFile(viewModel.photoFile(it)) }
        ?: return@LaunchedEffect
    colours = viewModel.detectColours(source)
  }

  if (showCamera) {
    CameraCaptureScreen(
      newCaptureFile = viewModel::newCaptureFile,
      onPhotoCaptured = { uri ->
        photoUri = uri
        crop = null
        showCamera = false
      },
      onClose = { showCamera = false },
    )
    return
  }

  if (showFraming && photo != null) {
    FramingScreen(
      photo = photo,
      initialCrop = crop,
      onDone = {
        crop = it
        showFraming = false
      },
      onCancel = { showFraming = false },
    )
    return
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (editingItem != null) "Edit item" else "Add item") },
        navigationIcon = {
          IconButton(onClick = onDone) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 480.dp).padding(16.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier =
            Modifier.height(PREVIEW_HEIGHT)
              .aspectRatio(feedAspectRatio())
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .then(if (photo != null) Modifier.clickable { showFraming = true } else Modifier),
          contentAlignment = Alignment.Center,
        ) {
          if (photo != null) {
            AsyncImage(
              model = croppedPhotoModel(context, photo, crop, PREVIEW_DECODE_PX),
              contentDescription = "Item photo",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Text("No photo yet", style = MaterialTheme.typography.bodyLarge)
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedButton(onClick = { showCamera = true }, modifier = Modifier.weight(1f)) {
            Text("Take photo")
          }
          OutlinedButton(
            onClick = {
              pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("Choose photo")
          }
        }

        OutlinedButton(
          onClick = { showFraming = true },
          enabled = photo != null,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (crop != null) "Change framing" else "Adjust framing")
        }

        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Name (optional)") },
          // The placeholder only shows while the field has focus, so the auto name goes here
          // instead: it is the whole point of leaving the name empty.
          supportingText = {
            if (name.isBlank()) Text("Saved as “${autoName(colours.firstOrNull(), category)}”")
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        CategorySelector(
          categories = categories,
          selected = category,
          onSelect = { category = it },
          modifier = Modifier.fillMaxWidth(),
        )

        ColourSelector(
          selected = colours,
          onToggle = { picked ->
            colours = if (picked in colours) colours - picked else colours + picked
            colourPicked = true
          },
          modifier = Modifier.align(Alignment.Start),
        )

        Button(
          onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            val editing = editingItem
            if (editing != null) {
              viewModel.updateItem(editing, name.trim(), category, photoUri, colours, crop)
            } else {
              viewModel.addItem(name.trim(), category, photoUri!!, colours, crop)
            }
            onDone()
          },
          enabled = photoUri != null || editingItem != null,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (editingItem != null) "Save changes" else "Save item")
        }
      }
    }
  }
}

/**
 * The categories, as a scrolling chip row. This was a segmented button row back when there were
 * exactly three and they could never change; there can now be any number of them.
 */
@Composable
fun CategorySelector(
  categories: List<Category>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptics = LocalHapticFeedback.current
  Row(
    modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    categories.forEach { category ->
      FilterChip(
        selected = selected == category.name,
        onClick = {
          haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
          onSelect(category.name)
        },
        label = { Text(category.name) },
      )
    }
  }
}

/**
 * The colour tags, guessed from the photo and correctable here when the guess is off.
 *
 * The menu stays open as colours are tapped: an item can have several, and closing after the
 * first would make picking the second a second trip.
 */
@Composable
fun ColourSelector(
  selected: List<Colour>,
  onToggle: (Colour) -> Unit,
  modifier: Modifier = Modifier,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box(modifier) {
    AssistChip(
      onClick = { open = true },
      label = {
        Text(if (selected.isEmpty()) "Add colours" else selected.joinToString { it.label })
      },
      leadingIcon = { if (selected.isEmpty()) Swatch(null) else SwatchRow(selected) },
    )
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      ColourPickerGrid(selected = selected, onToggle = onToggle, onDone = { open = false })
    }
  }
}

/**
 * The shape of the feed photo — the whole window, since the feed is full-bleed. Previews and the
 * framing editor use it so what gets framed is what ends up on screen.
 */
@Composable
fun feedAspectRatio(): Float {
  val size = LocalWindowInfo.current.containerSize
  return if (size.width > 0 && size.height > 0) {
    size.width.toFloat() / size.height.toFloat()
  } else {
    9f / 19.5f
  }
}

/** Enums are not [android.os.Bundle] material on their own; their names are. */
private val ColourListSaver =
  listSaver<List<Colour>, String>(
    save = { colours -> colours.map { it.name } },
    restore = { names -> names.map { Colour.valueOf(it) } },
  )

private val CropRectSaver =
  listSaver<CropRect?, Float>(
    save = { crop -> crop?.let { listOf(it.left, it.top, it.right, it.bottom) } ?: emptyList() },
    restore = { values ->
      if (values.size == 4) CropRect(values[0], values[1], values[2], values[3]) else null
    },
  )
