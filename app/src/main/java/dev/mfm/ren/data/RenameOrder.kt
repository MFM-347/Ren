package dev.mfm.ren.data

/**
 * Mirrors the `-r/--order` choices from the original Python CLI:
 * - ALPHABET: sort by filename, ascending
 * - NEWEST_FIRST: sort by last-modified time, descending
 * - OLDEST_FIRST: sort by last-modified time, ascending (CLI default)
 */
enum class RenameOrder(val label: String) {
  ALPHABET("Alphabetical"),
  NEWEST_FIRST("Newest first"),
  OLDEST_FIRST("Oldest first"),
}
