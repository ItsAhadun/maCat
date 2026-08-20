package com.ahad.macat.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahad.macat.data.Category
import com.ahad.macat.data.FilterVisibility
import com.ahad.macat.ui.CatalogueViewModel

/**
 * Categories and which filter controls the browsing screens show.
 *
 * The two belong together: they are both about what the catalogue is shaped like for this person.
 * Someone who never sorts can take the sort control off the bar; someone who files by colour alone
 * can take the categories off it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: CatalogueViewModel, onBack: () -> Unit) {
  val categories by viewModel.categories.collectAsStateWithLifecycle()
  val visibility by viewModel.filterVisibility.collectAsStateWithLifecycle()

  var adding by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf<Category?>(null) }
  var deleting by remember { mutableStateOf<Category?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    Column(
      Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SectionHeader("Categories")
      categories.forEachIndexed { index, category ->
        CategoryRow(
          category = category,
          canMoveUp = index > 0,
          canMoveDown = index < categories.lastIndex,
          onMoveUp = { viewModel.moveCategory(category, -1) },
          onMoveDown = { viewModel.moveCategory(category, 1) },
          onRename = { renaming = category },
          onDelete = { deleting = category },
        )
      }
      OutlinedButton(
        onClick = { adding = true },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      ) {
        Icon(Icons.Default.Add, contentDescription = null, Modifier.padding(end = 8.dp))
        Text("Add category")
      }

      HorizontalDivider(Modifier.padding(vertical = 8.dp))

      SectionHeader("Filters shown")
      Text(
        "Hiding a filter also clears it, so nothing narrows your catalogue from behind a control " +
          "you can no longer see.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      )
      VisibilityToggle("Category chips", visibility.categories) {
        viewModel.setFilterVisibility(visibility.copy(categories = it))
      }
      VisibilityToggle("Colour swatches", visibility.colours) {
        viewModel.setFilterVisibility(visibility.copy(colours = it))
      }
      VisibilityToggle("Favourites chip", visibility.favourites) {
        viewModel.setFilterVisibility(visibility.copy(favourites = it))
      }
      VisibilityToggle("Sort and shuffle", visibility.sort) {
        viewModel.setFilterVisibility(visibility.copy(sort = it))
      }
      VisibilityToggle("Search", visibility.search) {
        viewModel.setFilterVisibility(visibility.copy(search = it))
      }
      Spacer(Modifier.height(24.dp))
    }
  }

  if (adding) {
    NameDialog(
      title = "New category",
      initial = "",
      confirmLabel = "Add",
      onConfirm = {
        viewModel.addCategory(it)
        adding = false
      },
      onDismiss = { adding = false },
    )
  }

  renaming?.let { category ->
    NameDialog(
      title = "Rename “${category.name}”",
      initial = category.name,
      confirmLabel = "Rename",
      onConfirm = {
        viewModel.renameCategory(category, it)
        renaming = null
      },
      onDismiss = { renaming = null },
    )
  }

  deleting?.let { category ->
    DeleteCategoryDialog(
      category = category,
      others = categories.filter { it.id != category.id }.map { it.name },
      countInCategory = { viewModel.countInCategory(category) },
      onConfirm = { replacement ->
        viewModel.deleteCategory(category, replacement)
        deleting = null
      },
      onDismiss = { deleting = null },
    )
  }
}

@Composable
private fun SectionHeader(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
  )
}

@Composable
private fun CategoryRow(
  category: Category,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(category.name, style = MaterialTheme.typography.bodyLarge)
      if (category.isBuiltIn) {
        Text(
          "Built in",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
      Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move “${category.name}” up")
    }
    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
      Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move “${category.name}” down")
    }
    IconButton(onClick = onRename) {
      Icon(Icons.Default.Edit, contentDescription = "Rename “${category.name}”")
    }
    // Built-ins are renamable and reorderable but never deletable: an item always has to have
    // somewhere to be, and the last category disappearing would leave nowhere.
    IconButton(onClick = onDelete, enabled = !category.isBuiltIn) {
      Icon(Icons.Default.Delete, contentDescription = "Delete “${category.name}”")
    }
  }
}

@Composable
private fun VisibilityToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onChange)
  }
}

@Composable
private fun NameDialog(
  title: String,
  initial: String,
  confirmLabel: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Name") },
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
        Text(confirmLabel)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/**
 * Deleting a category cannot strand the items filed under it, so this asks where they go first.
 * The count is read when the dialog opens rather than kept in the list, because it is only ever
 * needed here.
 */
@Composable
private fun DeleteCategoryDialog(
  category: Category,
  others: List<String>,
  countInCategory: suspend () -> Int,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var count by remember { mutableStateOf<Int?>(null) }
  var replacement by remember { mutableStateOf(others.firstOrNull().orEmpty()) }
  var menuOpen by remember { mutableStateOf(false) }
  LaunchedEffect(category.id) { count = countInCategory() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Delete “${category.name}”?") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val held = count) {
          null -> Text("Checking what is filed under it…")
          0 -> Text("Nothing is filed under it.")
          else ->
            Text(
              "$held ${if (held == 1) "item is" else "items are"} filed under it. " +
                "They need somewhere to go."
            )
        }
        if ((count ?: 0) > 0) {
          Box {
            OutlinedButton(onClick = { menuOpen = true }) {
              Text("Move them to: ", fontWeight = FontWeight.Normal)
              Text(replacement, fontWeight = FontWeight.Medium)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              others.forEach { name ->
                DropdownMenuItem(
                  text = { Text(name) },
                  onClick = {
                    replacement = name
                    menuOpen = false
                  },
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(replacement) },
        // Nowhere to move them to means nothing safe to do.
        enabled = count != null && (count == 0 || replacement.isNotEmpty()),
      ) {
        Text("Delete")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
