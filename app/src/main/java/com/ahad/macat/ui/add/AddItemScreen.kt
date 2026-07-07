package com.ahad.macat.ui.add

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ahad.macat.data.Category
import com.ahad.macat.ui.CatalogueViewModel

/** Add a new item, or edit an existing one when [itemId] is set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(viewModel: CatalogueViewModel, itemId: Long?, onDone: () -> Unit) {
  val editingItem = remember(itemId) { itemId?.let(viewModel::itemById) }

  var name by rememberSaveable { mutableStateOf(editingItem?.name ?: "") }
  var category by rememberSaveable {
    mutableStateOf(editingItem?.category ?: viewModel.filter.value ?: Category.CLOTHES)
  }
  // Newly taken/picked photo; null while editing means "keep the current photo".
  var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
  var showCamera by rememberSaveable { mutableStateOf(false) }

  val pickPhoto =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) photoUri = uri
    }

  if (showCamera) {
    CameraCaptureScreen(
      newCaptureFile = viewModel::newCaptureFile,
      onPhotoCaptured = { uri ->
        photoUri = uri
        showCamera = false
      },
      onClose = { showCamera = false },
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
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .aspectRatio(3f / 4f)
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center,
        ) {
          val photoModel: Any? = photoUri ?: editingItem?.let(viewModel::photoFile)
          if (photoModel != null) {
            AsyncImage(
              model = photoModel,
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

        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        CategorySelector(selected = category, onSelect = { category = it })

        Button(
          onClick = {
            val editing = editingItem
            if (editing != null) {
              viewModel.updateItem(editing, name.trim(), category, photoUri)
            } else {
              viewModel.addItem(name.trim(), category, photoUri!!)
            }
            onDone()
          },
          enabled = name.isNotBlank() && (photoUri != null || editingItem != null),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (editingItem != null) "Save changes" else "Save item")
        }
      }
    }
  }
}

@Composable
fun CategorySelector(selected: Category, onSelect: (Category) -> Unit) {
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    Category.entries.forEachIndexed { index, category ->
      SegmentedButton(
        selected = selected == category,
        onClick = { onSelect(category) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = Category.entries.size),
      ) {
        Text(category.label)
      }
    }
  }
}
