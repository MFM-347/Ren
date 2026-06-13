package com.example.renpy.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.renpy.data.FileRenamer
import com.example.renpy.data.RenameOrder
import com.example.renpy.data.RenamePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen state for RenPy. [previews] is non-empty once the user has tapped
 * "Preview"; [result] is populated after a successful (or partially
 * successful) rename pass.
 */
data class RenPyUiState(
    val folderUri: Uri? = null,
    val folderName: String? = null,
    val baseName: String = "",
    val order: RenameOrder = RenameOrder.OLDEST_FIRST,
    val previews: List<RenamePreview> = emptyList(),
    val isLoadingPreview: Boolean = false,
    val isApplying: Boolean = false,
    val result: FileRenamer.RenameResult? = null,
    val error: String? = null,
)

class RenPyViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RenPyUiState())
    val uiState: StateFlow<RenPyUiState> = _uiState

    companion object {
        private const val PREFS_NAME = "renpy_prefs"
        private const val KEY_TREE_URI = "tree_uri"
    }

    init {
        restorePersistedFolder()
    }

    /** Loads a previously-granted SAF tree URI, if any, on app start. */
    private fun restorePersistedFolder() {
        val prefs = getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
        val saved = prefs.getString(KEY_TREE_URI, null) ?: return
        val uri = Uri.parse(saved)

        // Confirm we still hold a persisted permission for this URI; the
        // user may have revoked it via system settings.
        val stillGranted = getApplication<Application>().contentResolver
            .persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission && it.isWritePermission }

        if (stillGranted) {
            val name = DocumentFile.fromTreeUri(getApplication(), uri)?.name
            _uiState.update { it.copy(folderUri = uri, folderName = name) }
        } else {
            prefs.edit().remove(KEY_TREE_URI).apply()
        }
    }

    /**
     * Call after the SAF folder picker returns successfully. Persists the
     * permission so it survives app restarts, and clears any stale preview
     * from a previously-selected folder.
     */
    fun onFolderSelected(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )

        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, uri.toString())
            .apply()

        val name = DocumentFile.fromTreeUri(getApplication(), uri)?.name
        _uiState.update {
            it.copy(
                folderUri = uri,
                folderName = name,
                previews = emptyList(),
                result = null,
                error = null,
            )
        }
    }

    fun onBaseNameChanged(value: String) {
        _uiState.update { it.copy(baseName = value, result = null) }
    }

    fun onOrderChanged(order: RenameOrder) {
        _uiState.update { it.copy(order = order, result = null) }
    }

    /** Clears the current preview, e.g. after editing inputs post-preview. */
    fun clearPreview() {
        _uiState.update { it.copy(previews = emptyList(), result = null, error = null) }
    }

    /**
     * Builds the rename preview off the main thread (directory listing over
     * SAF can be slow for large folders).
     */
    fun generatePreview() {
        val state = _uiState.value
        val uri = state.folderUri
        if (uri == null) {
            _uiState.update { it.copy(error = "Choose a folder first.") }
            return
        }
        if (state.baseName.isBlank()) {
            _uiState.update { it.copy(error = "Enter a base name.") }
            return
        }

        _uiState.update { it.copy(isLoadingPreview = true, error = null, result = null) }

        viewModelScope.launch {
            val previews = withContext(Dispatchers.IO) {
                FileRenamer.buildPreview(
                    context = getApplication(),
                    treeUri = uri,
                    baseName = state.baseName.trim(),
                    order = state.order,
                )
            }

            _uiState.update {
                it.copy(
                    previews = previews,
                    isLoadingPreview = false,
                    error = if (previews.isEmpty()) "No files found in this folder." else null,
                )
            }
        }
    }

    /** Applies the currently-displayed preview, performing real renames. */
    fun confirmRenames() {
        val state = _uiState.value
        val uri = state.folderUri ?: return
        if (state.previews.isEmpty()) return

        _uiState.update { it.copy(isApplying = true, error = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                FileRenamer.applyRenames(
                    context = getApplication(),
                    treeUri = uri,
                    previews = state.previews,
                )
            }

            _uiState.update {
                it.copy(
                    isApplying = false,
                    result = result,
                    previews = emptyList(),
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
