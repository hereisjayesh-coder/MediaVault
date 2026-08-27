package com.mediavault.app.ui.screens.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mediavault.app.ui.components.MediaVaultTopBar

/**
 * Renders one of the app's own legal documents from `assets/legal/` — Privacy Policy, Terms &
 * Conditions (which also carries the user-responsibility/acceptable-use section), or Third-Party
 * Notices. The asset copies are kept in sync with the repository-root `.md` files (the actual
 * source of truth); nothing here invents or paraphrases legal content.
 */
@Composable
fun LegalDocumentScreen(title: String, assetFileName: String) {
    val context = LocalContext.current
    val lines = remember(assetFileName) {
        val raw = context.assets.open("legal/$assetFileName").bufferedReader().use { it.readText() }
        parseSimpleMarkdown(raw)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { MediaVaultTopBar(title = title) }
        items(lines) { line -> MarkdownLineRow(line) }
    }
}

@Composable
private fun MarkdownLineRow(line: MarkdownLine) {
    when (line) {
        is MarkdownLine.Title -> Text(text = line.text, style = MaterialTheme.typography.headlineSmall)
        is MarkdownLine.Heading -> Text(
            text = line.text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        is MarkdownLine.Subtitle -> Text(
            text = line.text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is MarkdownLine.Bullet -> Text(
            text = "•  ${line.text}",
            style = MaterialTheme.typography.bodyMedium,
        )
        is MarkdownLine.TableRow -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            line.cells.getOrNull(0)?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            line.cells.getOrNull(1)?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            line.cells.getOrNull(2)?.let {
                Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        is MarkdownLine.Paragraph -> Text(text = line.text, style = MaterialTheme.typography.bodyMedium)
    }
}
