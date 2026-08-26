package com.mediavault.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediavault.app.R
import com.mediavault.app.settings.ThemeMode
import com.mediavault.app.settings.ThemeViewModel
import com.mediavault.app.ui.components.EmptyStateCard
import com.mediavault.app.ui.components.MediaVaultCard
import com.mediavault.app.ui.components.MediaVaultTopBar
import com.mediavault.app.ui.components.SectionLabel

@Composable
fun SettingsScreen(themeViewModel: ThemeViewModel = hiltViewModel()) {
    val themeMode by themeViewModel.themeMode.collectAsState()

    SettingsScreenContent(
        themeMode = themeMode,
        onThemeModeSelected = themeViewModel::setThemeMode,
    )
}

@Composable
private fun SettingsScreenContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { MediaVaultTopBar(title = stringResource(R.string.settings_title)) }
        item { AppearanceSection(selected = themeMode, onSelected = onThemeModeSelected) }
        item {
            EmptyStateCard(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.settings_title),
                description = stringResource(R.string.settings_placeholder_body),
            )
        }
    }
}

@Composable
private fun AppearanceSection(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = stringResource(R.string.settings_appearance_section))
        MediaVaultCard {
            Text(text = stringResource(R.string.settings_theme_label), style = MaterialTheme.typography.labelLarge)
            ThemeOptionRow(ThemeMode.LIGHT, Icons.Default.LightMode, R.string.theme_mode_light, selected, onSelected)
            ThemeOptionRow(ThemeMode.DARK, Icons.Default.DarkMode, R.string.theme_mode_dark, selected, onSelected)
            ThemeOptionRow(ThemeMode.SYSTEM, Icons.Default.Brightness6, R.string.theme_mode_system, selected, onSelected)
        }
    }
}

@Composable
private fun ThemeOptionRow(
    mode: ThemeMode,
    icon: ImageVector,
    labelRes: Int,
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    val isSelected = mode == selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected(mode) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = isSelected, onClick = { onSelected(mode) })
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}
