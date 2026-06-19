package dev.mfm.ren.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mfm.ren.data.FileRenamer
import dev.mfm.ren.data.RenameOrder
import dev.mfm.ren.data.RenamePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen state for Ren. [previews] is non-empty once the user has tapped
 * "Preview"; [result] is populated after a successful (or partially successful)
 * rename pass.
 *
 * [showFolderError] and [showBaseNameError] drive inline field-level validation
 * messages in the UI instead of interrupting dialogs, so the user knows exactly
 * which input needs attention without losing context.
 */
data class RenUiState(
  val folderUri: Uri? = null,
  val folderName: String? = null,
  val baseName: String = "",
  val order: RenameOrder = RenameOrder.OLDEST_FIRST,
  val previews: List<RenamePreview> = emptyList(),
  val isLoadingPreview: Boolean = false,
  val isApplying: Boolean = false,
  val result: FileRenamer.RenameResult? = null,
  val error: String? = null,
  // Inline validation flags — set on failed Preview attempt, cleared on fix.
  val showFolderError: Boolean = false,
  val showBaseNameError: Boolean = false,
)

class RenViewModel(application: Application) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(RenUiState())
  val uiState: StateFlow<RenUiState> = _uiState

  companion object {
    private const val PREFS_NAME = "Ren_prefs"
    private const val KEY_TREE_URI = "tree_uri"
  }

  init {
    restorePersistedFolder()
  }

  /**
   * Loads a previously-granted SAF tree URI, if any, on app start. The
   * permission check and DocumentFile.name resolution are moved to IO so the
   * ViewModel constructor does not block the main thread during startup.
   */
  private fun restorePersistedFolder() {
    viewModelScope.launch {
      val (uri, name) =
        withContext(Dispatchers.IO) {
          val prefs =
            getApplication<Application>()
              .getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
          val saved =
            prefs.getString(KEY_TREE_URI, null)
              ?: return@withContext null to null

          val uri = Uri.parse(saved)
          val stillGranted =
            getApplication<Application>()
              .contentResolver
              .persistedUriPermissions
              .any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
              }

          if (stillGranted) {
            // Resolve .name on IO — it is a SAF IPC call.
            val name = DocumentFile.fromTreeUri(getApplication(), uri)?.name
            uri to name
          } else {
            prefs.edit().remove(KEY_TREE_URI).apply()
            null to null
          }
        } ?: return@launch

      _uiState.update { it.copy(folderUri = uri, folderName = name) }
    }
  }

  /**
   * Call after the SAF folder picker returns successfully. Persists the
   * permission so it survives app restarts, clears any stale preview from a
   * previously-selected folder, and dismisses the folder validation error.
   * DocumentFile.name is resolved on IO to avoid a SAF IPC call on the main
   * thread.
   */
  fun onFolderSelected(uri: Uri) {
    val resolver = getApplication<Application>().contentResolver
    resolver.takePersistableUriPermission(
      uri,
      Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )

    getApplication<Application>()
      .getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
      .edit()
      .putString(KEY_TREE_URI, uri.toString())
      .apply()

    viewModelScope.launch {
      // Resolve the display name off the main thread — DocumentFile.name
      // is a SAF IPC call and should not run on the main thread.
      val name =
        withContext(Dispatchers.IO) {
          DocumentFile.fromTreeUri(getApplication(), uri)?.name
        }

      _uiState.update {
        it.copy(
          folderUri = uri,
          folderName = name,
          previews = emptyList(),
          result = null,
          error = null,
          showFolderError = false, // user fixed it — clear the inline error
        )
      }
    }
  }

  fun onBaseNameChanged(value: String) {
    _uiState.update {
      it.copy(
        baseName = value,
        result = null,
        showBaseNameError = false, // clear as soon as the user starts typing
      )
    }
  }

  fun onOrderChanged(order: RenameOrder) {
    _uiState.update { it.copy(order = order, result = null) }
  }

  fun clearPreview() {
    _uiState.update {
      it.copy(previews = emptyList(), result = null, error = null)
    }
  }

  /**
   * Validates inputs inline (no dialog), then builds the rename preview off the
   * main thread. SAF directory listing can be slow for large folders.
   */
  fun generatePreview() {
    val state = _uiState.value

    // Validate both fields before touching the filesystem, and surface errors
    // inline rather than via a blocking dialog.
    val folderMissing = state.folderUri == null
    val baseNameBlank = state.baseName.isBlank()

    if (folderMissing || baseNameBlank) {
      _uiState.update {
        it.copy(
          showFolderError = folderMissing,
          showBaseNameError = baseNameBlank,
        )
      }
      return
    }

    _uiState.update {
      it.copy(
        isLoadingPreview = true,
        error = null,
        result = null,
        showFolderError = false,
        showBaseNameError = false,
      )
    }

    viewModelScope.launch {
      val previews =
        withContext(Dispatchers.IO) {
          FileRenamer.buildPreview(
            context = getApplication(),
            treeUri = state.folderUri!!,
            baseName = state.baseName.trim(),
            order = state.order,
          )
        }

      _uiState.update {
        it.copy(
          previews = previews,
          isLoadingPreview = false,
          error =
            if (previews.isEmpty()) "No files found in this folder." else null,
        )
      }
    }
  }

  fun confirmRenames() {
    val state = _uiState.value
    val uri = state.folderUri ?: return
    if (state.previews.isEmpty()) return

    _uiState.update { it.copy(isApplying = true, error = null) }

    viewModelScope.launch {
      val result =
        withContext(Dispatchers.IO) {
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
