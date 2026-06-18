package dev.mfm.renpy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.Icons.AutoMirrored.Filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mfm.renpy.data.RenamePreview

/**
 * Single row in the rename preview list: original filename, an arrow, and the
 * proposed new filename. If the names are identical (no-op), the arrow and new
 * name are de-emphasized.
 */
@Composable
fun PreviewItem(preview: RenamePreview, modifier: Modifier = Modifier) {
  val unchanged = preview.originalName == preview.newName

  Row(
    modifier =
      modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = preview.originalName,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowForward,
      contentDescription = "renames to",
      tint =
        if (unchanged) {
          MaterialTheme.colorScheme.outline
        } else {
          MaterialTheme.colorScheme.primary
        },
    )

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = preview.newName,
        style = MaterialTheme.typography.bodyMedium,
        color =
          if (unchanged) {
            MaterialTheme.colorScheme.onSurfaceVariant
          } else {
            MaterialTheme.colorScheme.primary
          },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (unchanged) {
        Text(
          text = "unchanged",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
    }
  }
}
