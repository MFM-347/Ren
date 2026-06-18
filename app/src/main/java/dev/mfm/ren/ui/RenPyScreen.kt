package dev.mfm.ren.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mfm.ren.data.RenameOrder
import dev.mfm.ren.ui.components.PreviewItem
import dev.mfm.ren.viewmodel.RenViewModel

/**
 * Top-level screen for Ren. Mirrors the original CLI's inputs -- base name,
 * target directory, and sort order -- with an additional preview/confirm step
 * required by Android's scoped storage model (there is no "simulate" flag here;
 * preview is always shown first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenScreen(viewModel: RenViewModel) {
  val state by viewModel.uiState.collectAsState()

  val folderPicker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
      if (uri != null) {
        viewModel.onFolderSelected(uri)
      }
    }

  var orderMenuExpanded by remember { mutableStateOf(false) }

  Scaffold(
    topBar = { TopAppBar(title = { Text("Ren") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // --- Folder selection ---
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Folder", style = MaterialTheme.typography.titleSmall)
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = state.folderName ?: "No folder selected",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        }
        OutlinedButton(
          onClick = { folderPicker.launch(null) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            if (state.folderUri == null) "Choose folder" else "Change folder")
        }
      }

      HorizontalDivider()

      // --- Base name ---
      OutlinedTextField(
        value = state.baseName,
        onValueChange = viewModel::onBaseNameChanged,
        label = { Text("Base name") },
        placeholder = { Text("e.g. vacation-photo") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      // --- Order selector ---
      ExposedDropdownMenuBox(
        expanded = orderMenuExpanded,
        onExpandedChange = { orderMenuExpanded = it },
      ) {
        OutlinedTextField(
          value = state.order.label,
          onValueChange = {},
          readOnly = true,
          label = { Text("Order") },
          trailingIcon = {
            ExposedDropdownMenuDefaults.TrailingIcon(
              expanded = orderMenuExpanded)
          },
          modifier = Modifier.fillMaxWidth().menuAnchor(),
        )

        DropdownMenu(
          expanded = orderMenuExpanded,
          onDismissRequest = { orderMenuExpanded = false },
        ) {
          RenameOrder.entries.forEach { order ->
            DropdownMenuItem(
              text = { Text(order.label) },
              onClick = {
                viewModel.onOrderChanged(order)
                orderMenuExpanded = false
              },
            )
          }
        }
      }

      // --- Preview button ---
      Button(
        onClick = viewModel::generatePreview,
        enabled = !state.isLoadingPreview && !state.isApplying,
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (state.isLoadingPreview) {
          CircularProgressIndicator(
            modifier = Modifier.padding(end = 8.dp).size(16.dp),
            strokeWidth = 2.dp,
          )
        }
        Text("Preview")
      }

      // --- Preview list ---
      if (state.previews.isNotEmpty()) {
        Text(
          text = "${state.previews.size} file(s) found",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(modifier = Modifier.weight(1f)) {
          LazyColumn {
            items(state.previews, key = { it.uri }) { preview ->
              PreviewItem(preview)
              HorizontalDivider()
            }
          }
        }

        Button(
          onClick = viewModel::confirmRenames,
          enabled = !state.isApplying,
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (state.isApplying) {
            CircularProgressIndicator(
              modifier = Modifier.padding(end = 8.dp).size(16.dp),
              strokeWidth = 2.dp,
            )
          }
          Text("Confirm renames")
        }
      } else {
        // Push remaining content to the bottom isn't strictly
        // needed; spacer keeps layout stable when list is absent.
        Box(modifier = Modifier.weight(1f))
      }
    }
  }

  // --- Result dialog ---
  state.result?.let { result ->
    AlertDialog(
      onDismissRequest = { viewModel.clearPreview() },
      title = {
        Text(if (result.failures.isEmpty()) "Done" else "Completed with errors")
      },
      text = {
        Column {
          Text("Renamed ${result.successCount} file(s).")
          if (result.failures.isNotEmpty()) {
            Text(
              "Failed: ${result.failures.joinToString(", ")}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { viewModel.clearPreview() }) { Text("OK") }
      },
    )
  }

  // --- Error dialog ---
  state.error?.let { message ->
    AlertDialog(
      onDismissRequest = viewModel::dismissError,
      title = { Text("Notice") },
      text = { Text(message) },
      confirmButton = {
        TextButton(onClick = viewModel::dismissError) { Text("OK") }
      },
    )
  }
}
