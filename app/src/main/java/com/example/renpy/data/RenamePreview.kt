package com.example.renpy.data

import android.net.Uri

/**
 * Represents a single proposed rename: one source document and the new display
 * name it would receive if the rename is confirmed.
 *
 * [uri] is the content:// URI for the existing document (obtained via
 * DocumentFile under the SAF tree), and [originalName] / [newName] are display
 * names only -- SAF does not expose raw filesystem paths.
 */
data class RenamePreview(
  val uri: Uri,
  val originalName: String,
  val newName: String,
  val lastModified: Long,
)
