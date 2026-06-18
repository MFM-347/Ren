# RenPy (Android)
  
> Android Version of [RenPy](https://github.com/mfm-347/awesome-automations/tree/dev/Automations/RenPy)

A bulk file-renaming app, ported from the original Python CLI to Jetpack Compose.

## Setup

1. Open this folder in Android Studio (Ladybug or newer recommended for AGP 8.9.x / Kotlin 2.0.x).
2. Android Studio will detect there's no Gradle wrapper jar and offer to generate
   one automatically — accept that prompt. (Wrapper binaries can't be produced
   outside a Gradle/Studio environment, so they're not included in this export.)
   Alternatively, from a machine with Gradle installed: `gradle wrapper --gradle-version 8.9`.
3. Sync, then run on a device or emulator running **Android 8.0 (API 26)** or higher.

## What changed vs. the CLI

| CLI concept                                     | Android equivalent                                                                                                                              |
| ----------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `directory` positional arg, raw filesystem path | SAF folder picker (`ActivityResultContracts.OpenDocumentTree`); access persisted via `takePersistableUriPermission` so it survives app restarts |
| `base_name` positional arg                      | Text field                                                                                                                                      |
| `-r/--order` (`alphabet`/`new`/`old`)           | Dropdown, same three options, "old" (oldest-first) remains the default                                                                          |
| `-s/--simulate`                                 | Removed as a flag — **preview is now always shown first**, and renames only happen after the user taps "Confirm renames"                        |
| `os.rename` per file                            | Two-pass rename via `DocumentFile.renameTo` (see below)                                                                                         |

## Why a two-pass rename

The original script does `os.rename(old, new)` in a single pass. If the target
name set overlaps the source name set — e.g. renaming `b.jpg` to `photo-1.jpg`
when `a.jpg` is _also_ being renamed to `photo-1.jpg` momentarily, or when a
leftover `photo-3.jpg` already exists in the folder — this can throw or silently
clobber a file, and SAF's `DocumentFile.renameTo` has similar provider-dependent
quirks (some providers append a `(1)` suffix instead of failing).

`FileRenamer.applyRenames` avoids this by:

1. Renaming every file that needs to change to a unique temporary name
   (`__renpy_tmp_<timestamp>_<index>`).
2. Renaming each temp file to its real target name.

If anything fails in step 2, that file is left with its temp name and reported
in the result dialog rather than silently reverted.

## Architecture

```
data/
  RenameOrder.kt    - enum: ALPHABET, NEWEST_FIRST, OLDEST_FIRST
  RenamePreview.kt  - one row: uri, originalName, newName, lastModified
  FileRenamer.kt    - buildPreview() [read-only] + applyRenames() [mutates]
viewmodel/
  RenPyViewModel.kt - holds UI state, persists the chosen folder URI in
                      SharedPreferences, runs FileRenamer on Dispatchers.IO
ui/
  RenPyScreen.kt    - folder picker, base name field, order dropdown,
                      preview list, confirm button, result/error dialogs
  components/
    PreviewItem.kt  - one "old name -> new name" row
```

## Known limitations / next steps

- **Performance on very large folders**: `applyRenames` does two
  `DocumentFile` lookups per renamed file (each a content-provider round
  trip). Fine for tens-to-hundreds of files; folders with several thousand
  files will be noticeably slower. If that's a real use case, switch
  `FileRenamer` to operate on a single cached `listFiles()` snapshot instead
  of re-querying per file.
- **No undo**: once "Confirm renames" succeeds, there's no built-in revert.
  Consider logging the old↔new name pairs to allow a one-tap undo.
- **Subdirectories are skipped**, matching the original script's
  `os.path.isfile` filter. If recursive renaming is wanted, `buildPreview`
  would need to walk `DocumentFile.listFiles()` recursively and decide how
  to handle naming collisions across subfolders.
