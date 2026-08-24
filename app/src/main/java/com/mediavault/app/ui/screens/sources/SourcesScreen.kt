package com.mediavault.app.ui.screens.sources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.SubcomposeAsyncImage
import com.mediavault.app.R
import com.mediavault.app.ui.components.EmptyStateCard
import com.mediavault.app.ui.components.MediaVaultTopBar
import com.mediavault.core.model.Source
import com.mediavault.core.model.SourceCategory

@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel = hiltViewModel(),
    onSourceClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    SourcesScreenContent(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onCategorySelected = viewModel::onCategorySelected,
        onSourceClick = onSourceClick,
    )
}

@Composable
private fun SourcesScreenContent(
    uiState: SourcesUiState,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (SourceCategory?) -> Unit,
    onSourceClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MediaVaultTopBar(title = stringResource(R.string.sources_title)) }

        item {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.sources_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        }

        item {
            CategoryFilterRow(selected = uiState.selectedCategory, onCategorySelected = onCategorySelected)
        }

        if (!uiState.isLoading) {
            item {
                val countText = if (uiState.selectedCategory != null || uiState.query.isNotBlank()) {
                    stringResource(R.string.sources_count_filtered, uiState.visibleCount, uiState.totalCount)
                } else {
                    stringResource(R.string.sources_count, uiState.totalCount)
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (uiState.groups.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.sources_empty_title),
                    description = stringResource(R.string.sources_empty_body),
                )
            }
        } else {
            uiState.groups.keys.sorted().forEach { letter ->
                stickyLetterHeader(letter)
                items(uiState.groups.getValue(letter), key = { it.id }) { source ->
                    SourceRow(source = source, onClick = { onSourceClick(source.id) })
                }
            }
        }
    }
}

private fun LazyListScope.stickyLetterHeader(letter: Char) {
    stickyHeader(key = "header-$letter") {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selected: SourceCategory?,
    onCategorySelected: (SourceCategory?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onCategorySelected(null) },
                label = { Text(stringResource(R.string.sources_category_all)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
        items(SourceCategory.entries.toList()) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onCategorySelected(if (selected == category) null else category) },
                label = { Text(stringResource(category.labelRes())) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}

@Composable
private fun SourceRow(source: Source, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SourceIcon(source = source, modifier = Modifier.size(36.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = source.displayName, style = MaterialTheme.typography.bodyMedium)
                val subtitle = source.domain ?: stringResource(source.categories.first().labelRes())
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SourceIcon(source: Source, modifier: Modifier = Modifier) {
    if (source.faviconUrl != null) {
        SubcomposeAsyncImage(
            model = source.faviconUrl,
            contentDescription = null,
            modifier = modifier.clip(CircleShape),
            loading = { InitialsAvatar(source.displayName, modifier) },
            error = { InitialsAvatar(source.displayName, modifier) },
        )
    } else {
        InitialsAvatar(source.displayName, modifier)
    }
}

@Composable
private fun InitialsAvatar(name: String, modifier: Modifier = Modifier) {
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Surface(modifier = modifier.clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = letter,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
