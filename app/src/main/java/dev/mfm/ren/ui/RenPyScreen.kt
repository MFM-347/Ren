package dev.mfm.ren.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mfm.ren.R
import dev.mfm.ren.data.RenameOrder
import dev.mfm.ren.ui.components.PreviewItem
import dev.mfm.ren.viewmodel.RenViewModel

/**
 * Top-level screen for Ren. Mirrors the original CLI's inputs — base name,
 * target directory, and sort order — with a mandatory preview/confirm step
 * before any filesystem writes occur (Android scoped storage has no dry-run
 * flag, so preview is always shown first).
 *
 * A11y surface contract:
 *  - TopAppBar title carries the heading() semantic.
 *  - Folder picker button merges its state (selected name / empty hint) into
 *    a single contentDescription so TalkBack reads one coherent announcement.
 *  - ExposedDropdownMenuBox uses an OutlinedTextField so the "Order" label is
 *    correctly associated via the Compose semantics tree (not floating).
 *  - All interactive surfaces meet the 48 dp minimum touch target via
 *    heightIn(min = 48.dp) or Material component defaults.
 *  - Errors surface as a Snackbar (non-modal) in addition to the dialog so
 *    they don't steal focus from partially-completed inputs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenScreen(viewModel: RenViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.onFolderSelected(uri)
    }

    var orderMenuExpanded by remember { mutableStateOf(false) }
    val appName = stringResource(R.string.app_name)

    // Surface transient errors as a Snackbar so focus isn't stolen mid-form.
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = appName,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Folder selection ──────────────────────────────────────────

            // The folder name and the picker button are merged into one
            // semantics node so TalkBack reads the full current state
            // ("Choose folder. Selected: Vacation 2024") in a single pass.
            val folderButtonCd = if (state.folderUri == null) {
                stringResource(R.string.cd_folder_none)
            } else {
                stringResource(R.string.cd_folder_selected, state.folderName ?: "")
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.label_folder),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null, // decorative; label covers it
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = state.folderName
                                ?: stringResource(R.string.hint_folder_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.folderUri == null)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                FilledTonalButton(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = folderButtonCd },
                ) {
                    Text(
                        stringResource(
                            if (state.folderUri == null) R.string.btn_choose_folder
                            else R.string.btn_change_folder
                        )
                    )
                }

                // Inline validation: shown only after a failed Preview attempt
                // with no folder selected.
                if (state.showFolderError) {
                    Text(
                        text = stringResource(R.string.error_folder_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            HorizontalDivider()

            // ── Base name ─────────────────────────────────────────────────

            OutlinedTextField(
                value = state.baseName,
                onValueChange = viewModel::onBaseNameChanged,
                label = { Text(stringResource(R.string.label_base_name)) },
                placeholder = { Text(stringResource(R.string.placeholder_base_name)) },
                supportingText = {
                    Text(stringResource(R.string.hint_base_name_pattern))
                },
                isError = state.showBaseNameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Sort order ────────────────────────────────────────────────

            // Uses ExposedDropdownMenuBox + OutlinedTextField so the "Order"
            // label is correctly merged into the focusable node by the
            // Compose semantics tree — not a visually-floating orphan.
            ExposedDropdownMenuBox(
                expanded = orderMenuExpanded,
                onExpandedChange = { orderMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = state.order.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.label_order)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = orderMenuExpanded,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
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

            // ── Preview button ────────────────────────────────────────────

            Button(
                onClick = viewModel::generatePreview,
                enabled = !state.isLoadingPreview && !state.isApplying,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isLoadingPreview) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 0.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        if (state.isLoadingPreview) R.string.btn_previewing
                        else R.string.btn_preview
                    )
                )
            }

            // ── Preview list ──────────────────────────────────────────────

            if (state.previews.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.label_files_found, state.previews.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        // Announce count change to TalkBack after preview loads.
                        contentDescription =
                            "${state.previews.size} files found. Review below."
                    },
                )

                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
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
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(
                            if (state.isApplying) R.string.btn_applying
                            else R.string.btn_confirm
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

            } else {
                // Stable spacer keeps layout from jumping when preview loads.
                Box(modifier = Modifier.weight(1f))
            }
        }
    }

    // ── Result dialog ─────────────────────────────────────────────────────────

    state.result?.let { result ->
        val allOk = result.failures.isEmpty()
        AlertDialog(
            onDismissRequest = viewModel::clearPreview,
            title = {
                Text(
                    stringResource(
                        if (allOk) R.string.dialog_result_title_ok
                        else R.string.dialog_result_title_partial
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.dialog_result_success, result.successCount)
                    )
                    if (result.failures.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.dialog_result_failures,
                                result.failures.joinToString("\n"),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearPreview) {
                    Text(stringResource(R.string.btn_ok))
                }
            },
        )
    }
}
