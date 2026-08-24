package com.mediavault.app.ui.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mediavault.app.ui.components.EmptyStateCard
import com.mediavault.app.ui.components.MediaVaultTopBar

/**
 * Consistent shell for a screen whose backend isn't implemented yet: a top bar matching the
 * rest of the app, and a single honest empty-state card instead of fake content.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    icon: ImageVector,
    body: String,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { MediaVaultTopBar(title = title) }
        item {
            EmptyStateCard(
                icon = icon,
                title = title,
                description = body,
            )
        }
    }
}
