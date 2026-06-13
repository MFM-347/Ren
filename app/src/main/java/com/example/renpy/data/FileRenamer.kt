package com.example.renpy.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Core renaming logic, ported from the original Python CLI's `rename_files`
 * function. Two responsibilities are split apart on purpose:
 *
 *  - [buildPreview] is pure / read-only. It lists the files under the chosen
 *    SAF tree, sorts them per [RenameOrder], and computes what each file's
 *    new name *would* be -- without touching anything on disk. This backs
 *    the "Simulate" mode and the confirmation screen.
 *
 *  - [applyRenames] takes a (possibly user-edited) list of [RenamePreview]
 *    and actually performs the renames via DocumentFile.
 *
 * Collision handling: the original Python script renames in a single pass
 * with `os.rename`, which can throw or silently clobber a file if a target
 * name (e.g. "photo-3.jpg") already exists among the *source* files. SAF's
 * DocumentFile.renameTo behaves similarly -- some providers will throw,
 * others will create a "(1)" suffix you don't expect. To avoid that
 * entirely, [applyRenames] renames every file to a temporary, guaranteed-
 * unique name first, then renames each temp file to its final target name
 * in a second pass. This makes the whole operation safe even when the
 * target name set overlaps with the source name set.
 */
object FileRenamer {

    /**
     * Lists files directly inside [treeUri], sorts them according to
     * [order], and returns the proposed rename for each one. Subdirectories
     * are skipped (DocumentFile.isFile == false), matching the original
     * script's `os.path.isfile` filter.
     */
    fun buildPreview(
        context: Context,
        treeUri: Uri,
        baseName: String,
        order: RenameOrder,
    ): List<RenamePreview> {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return emptyList()

        val files = root.listFiles()
            .filter { it.isFile }
            .mapNotNull { doc ->
                val name = doc.name ?: return@mapNotNull null
                Triple(doc, name, doc.lastModified())
            }

        val sorted = when (order) {
            RenameOrder.ALPHABET ->
                files.sortedBy { (_, name, _) -> name.lowercase() }

            RenameOrder.NEWEST_FIRST ->
                files.sortedByDescending { (_, _, modified) -> modified }

            RenameOrder.OLDEST_FIRST ->
                files.sortedBy { (_, _, modified) -> modified }
        }

        return sorted.mapIndexed { index, (doc, name, modified) ->
            val extension = name.substringAfterLast('.', missingDelimiterValue = "")
            val newName = if (extension.isEmpty()) {
                "$baseName-${index + 1}"
            } else {
                "$baseName-${index + 1}.$extension"
            }

            RenamePreview(
                uri = doc.uri,
                originalName = name,
                newName = newName,
                lastModified = modified,
            )
        }
    }

    /**
     * Result of [applyRenames]: how many files were renamed successfully,
     * and the original names of any files that failed (so the UI can
     * report them).
     */
    data class RenameResult(
        val successCount: Int,
        val failures: List<String>,
    )

    /**
     * Applies the renames described by [previews]. Entries where
     * [RenamePreview.originalName] already equals [RenamePreview.newName]
     * are skipped (no-op, counted as success).
     *
     * Two-pass strategy to avoid name collisions between source and target
     * names:
     *  1. Rename every file that needs changing to a unique temporary name.
     *  2. Rename each temp file to its real target name.
     *
     * If a rename fails partway through pass 2, the affected file is left
     * with its temporary name rather than silently reverted, and reported
     * in [RenameResult.failures] -- this mirrors the original script's
     * approach of printing an error and continuing rather than rolling
     * back everything.
     */
    fun applyRenames(
        context: Context,
        treeUri: Uri,
        previews: List<RenamePreview>,
    ): RenameResult {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return RenameResult(0, previews.map { it.originalName })

        val toProcess = previews.filter { it.originalName != it.newName }
        if (toProcess.isEmpty()) {
            return RenameResult(previews.size, emptyList())
        }

        val tempPrefix = "__renpy_tmp_${System.currentTimeMillis()}_"

        // Pass 1: move everything that needs renaming to a unique temp name.
        // `Pending.doc` tracks the resolved DocumentFile for each preview;
        // it's set to null if that document can't be found or its rename
        // fails, so pass 2 can skip it.
        data class Pending(val preview: RenamePreview, var doc: DocumentFile?)

        val pending = toProcess.map { preview ->
            val doc = root.findDocumentByUri(preview.uri)
            Pending(preview, doc)
        }

        val failures = mutableListOf<String>()

        pending.forEachIndexed { index, item ->
            val doc = item.doc
            if (doc == null) {
                failures.add(item.preview.originalName)
                return@forEachIndexed
            }
            val tempName = "$tempPrefix$index"
            val renamed = try {
                doc.renameTo(tempName)
            } catch (e: Exception) {
                false
            }
            if (!renamed) {
                failures.add(item.preview.originalName)
                item.doc = null
            }
        }

        // Pass 2: rename each successfully-moved temp file to its final name.
        // Re-resolve by the known temp name rather than reusing `item.doc`,
        // since some SAF providers change a document's Uri after renameTo
        // without updating the existing DocumentFile instance.
        pending.forEachIndexed { index, item ->
            if (item.doc == null) return@forEachIndexed

            val tempName = "$tempPrefix$index"
            val current = root.findFile(tempName)
            if (current == null) {
                failures.add(item.preview.originalName)
                return@forEachIndexed
            }

            val renamed = try {
                current.renameTo(item.preview.newName)
            } catch (e: Exception) {
                false
            }
            if (!renamed) {
                failures.add(item.preview.originalName)
            }
        }

        val successCount = previews.size - failures.size
        return RenameResult(successCount, failures)
    }

    /**
     * Helper to find a child DocumentFile by its content Uri. DocumentFile
     * doesn't expose this directly, so we scan the listing.
     */
    private fun DocumentFile.findDocumentByUri(uri: Uri): DocumentFile? =
        listFiles().firstOrNull { it.uri == uri }
}
