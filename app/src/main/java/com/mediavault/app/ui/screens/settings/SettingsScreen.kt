package com.mediavault.app.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediavault.app.AppConfig
import com.mediavault.app.R
import com.mediavault.app.player.PlayerPreferences
import com.mediavault.app.player.SubtitleStyle
import com.mediavault.app.policy.NetworkPolicySettings
import com.mediavault.app.settings.ThemeMode
import com.mediavault.app.settings.ThemeViewModel
import com.mediavault.app.ui.components.MediaVaultCard
import com.mediavault.app.ui.components.MediaVaultTopBar
import com.mediavault.app.ui.components.SectionLabel
import com.mediavault.app.ui.components.support.SupportSection
import com.mediavault.app.ui.components.support.openExternalUrl
import com.mediavault.app.ui.components.support.shareMediaVault
import com.mediavault.app.ui.screens.home.formatFileSizeLabel
import androidx.compose.ui.platform.LocalContext

private val PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val PER_DOWNLOAD_LIMIT_OPTIONS_BYTES = listOf(100L, 250L, 500L, 1024L, 2048L).map { it * 1024 * 1024 }
private val DAILY_BUDGET_OPTIONS_BYTES = listOf(500L, 1024L, 2048L, 5120L, 10240L).map { it * 1024 * 1024 }

@Composable
fun SettingsScreen(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {},
    onNavigateToLicenses: () -> Unit = {},
) {
    val themeMode by themeViewModel.themeMode.collectAsState()
    val uiState by settingsViewModel.uiState.collectAsState()

    SettingsScreenContent(
        themeMode = themeMode,
        onThemeModeSelected = themeViewModel::setThemeMode,
        uiState = uiState,
        viewModel = settingsViewModel,
        onNavigateToLibrary = onNavigateToLibrary,
        onNavigateToPrivacy = onNavigateToPrivacy,
        onNavigateToTerms = onNavigateToTerms,
        onNavigateToLicenses = onNavigateToLicenses,
    )
}

@Composable
private fun SettingsScreenContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToLicenses: () -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { MediaVaultTopBar(title = stringResource(R.string.settings_title)) }
        item { AppearanceSection(selected = themeMode, onSelected = onThemeModeSelected) }
        item { DownloadsSection(freeStorageBytes = uiState.freeStorageBytes) }
        item {
            NetworkSection(
                settings = uiState.networkPolicy,
                mobileBytesUsedToday = uiState.mobileBytesUsedToday,
                onMobileDownloadsEnabledChanged = viewModel::setMobileDownloadsEnabled,
                onPerDownloadLimitSelected = viewModel::setPerDownloadLimitBytes,
                onDailyBudgetSelected = viewModel::setDailyBudgetBytes,
            )
        }
        item {
            PlayerSection(
                preferences = uiState.playerPreferences,
                onDefaultSpeedSelected = viewModel::setDefaultPlaybackSpeed,
                onResumePlaybackChanged = viewModel::setResumePlaybackEnabled,
                onAutoFullscreenChanged = viewModel::setAutoFullscreenLandscape,
                onAutoEnterPipChanged = viewModel::setAutoEnterPip,
                onAutoAdvanceChanged = viewModel::setAutoAdvancePlaylist,
            )
        }
        item {
            SubtitlesSection(
                selected = uiState.subtitleStyle,
                onSelected = viewModel::setSubtitleStyle,
            )
        }
        item {
            StorageSection(
                freeStorageBytes = uiState.freeStorageBytes,
                onManageLibraryClick = onNavigateToLibrary,
            )
        }
        item { SupportSection(config = AppConfig.support) }
        item {
            PrivacyLegalSection(
                onPrivacyClick = onNavigateToPrivacy,
                onTermsClick = onNavigateToTerms,
                onLicensesClick = onNavigateToLicenses,
            )
        }
        item { FeedbackSection(context = context, appVersionName = uiState.appVersionName) }
        item {
            UpdatesSection(
                versionName = uiState.appVersionName,
                versionCode = uiState.appVersionCode,
                engineVersion = uiState.extractionEngineVersion,
                context = context,
            )
        }
        item {
            AboutSection(
                versionName = uiState.appVersionName,
                onLicensesClick = onNavigateToLicenses,
                context = context,
            )
        }
    }
}

// --- Appearance -------------------------------------------------------------------------------

@Composable
private fun AppearanceSection(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    SettingsCardSection(titleRes = R.string.settings_appearance_section) {
        Text(text = stringResource(R.string.settings_theme_label), style = MaterialTheme.typography.labelLarge)
        ThemeOptionRow(ThemeMode.LIGHT, Icons.Default.LightMode, R.string.theme_mode_light, selected, onSelected)
        ThemeOptionRow(ThemeMode.DARK, Icons.Default.DarkMode, R.string.theme_mode_dark, selected, onSelected)
        ThemeOptionRow(ThemeMode.SYSTEM, Icons.Default.Brightness6, R.string.theme_mode_system, selected, onSelected)
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
        modifier = Modifier.fillMaxWidth().clickable { onSelected(mode) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = isSelected, onClick = { onSelected(mode) })
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}

// --- Downloads ----------------------------------------------------------------------------

/**
 * Deliberately informational, not editable: the current download engine is sequential
 * (one active transfer at a time), retry is already a direct per-item action on the Downloads
 * screen, and quality is always chosen per-download — none of "concurrent downloads",
 * "auto-retry policy", or "default quality" are real, engine-backed settings yet, so no toggle
 * is shown for any of them rather than a control that would silently do nothing.
 */
@Composable
private fun DownloadsSection(freeStorageBytes: Long) {
    SettingsCardSection(titleRes = R.string.settings_downloads_section) {
        InfoRow(icon = Icons.Default.Download, text = stringResource(R.string.settings_downloads_sequential_info))
        InfoRow(icon = Icons.Default.RestartAlt, text = stringResource(R.string.settings_downloads_retry_info))
        val freeSpaceLabel = formatFileSizeLabel(freeStorageBytes)
        if (freeSpaceLabel != null) {
            InfoRow(icon = Icons.Default.Storage, text = stringResource(R.string.settings_downloads_storage_info, freeSpaceLabel))
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

// --- Network & Mobile Data ------------------------------------------------------------------

@Composable
private fun NetworkSection(
    settings: NetworkPolicySettings,
    mobileBytesUsedToday: Long,
    onMobileDownloadsEnabledChanged: (Boolean) -> Unit,
    onPerDownloadLimitSelected: (Long) -> Unit,
    onDailyBudgetSelected: (Long) -> Unit,
) {
    SettingsCardSection(titleRes = R.string.settings_network_section) {
        SwitchRow(
            icon = Icons.Default.Wifi,
            title = stringResource(R.string.settings_network_mobile_downloads),
            checked = settings.mobileDownloadsEnabled,
            onCheckedChange = onMobileDownloadsEnabledChanged,
        )
        if (settings.mobileDownloadsEnabled) {
            Text(text = stringResource(R.string.settings_network_per_download_limit), style = MaterialTheme.typography.labelLarge)
            ByteOptionChipRow(
                options = PER_DOWNLOAD_LIMIT_OPTIONS_BYTES,
                selected = settings.perDownloadLimitBytes,
                onSelected = onPerDownloadLimitSelected,
            )
            Text(text = stringResource(R.string.settings_network_daily_budget), style = MaterialTheme.typography.labelLarge)
            ByteOptionChipRow(
                options = DAILY_BUDGET_OPTIONS_BYTES,
                selected = settings.dailyBudgetBytes,
                onSelected = onDailyBudgetSelected,
            )
            val usedLabel = formatFileSizeLabel(mobileBytesUsedToday) ?: "0 MB"
            val budgetLabel = formatFileSizeLabel(settings.dailyBudgetBytes) ?: ""
            Text(
                text = stringResource(R.string.settings_network_used_today, usedLabel, budgetLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ByteOptionChipRow(options: List<Long>, selected: Long, onSelected: (Long) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { bytes ->
            FilterChip(
                selected = bytes == selected,
                onClick = { onSelected(bytes) },
                label = { Text(formatFileSizeLabel(bytes) ?: "") },
            )
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// --- Player -------------------------------------------------------------------------------

@Composable
private fun PlayerSection(
    preferences: PlayerPreferences,
    onDefaultSpeedSelected: (Float) -> Unit,
    onResumePlaybackChanged: (Boolean) -> Unit,
    onAutoFullscreenChanged: (Boolean) -> Unit,
    onAutoEnterPipChanged: (Boolean) -> Unit,
    onAutoAdvanceChanged: (Boolean) -> Unit,
) {
    SettingsCardSection(titleRes = R.string.settings_player_section) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = stringResource(R.string.settings_player_default_speed), style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                FilterChip(
                    selected = speed == preferences.defaultPlaybackSpeed,
                    onClick = { onDefaultSpeedSelected(speed) },
                    label = { Text("${speed}x") },
                )
            }
        }
        HorizontalDivider()
        SwitchRow(
            icon = Icons.Default.RestartAlt,
            title = stringResource(R.string.settings_player_resume_playback),
            checked = preferences.resumePlaybackEnabled,
            onCheckedChange = onResumePlaybackChanged,
        )
        SwitchRow(
            icon = Icons.Default.Fullscreen,
            title = stringResource(R.string.settings_player_auto_fullscreen),
            checked = preferences.autoFullscreenLandscape,
            onCheckedChange = onAutoFullscreenChanged,
        )
        SwitchRow(
            icon = Icons.Default.SkipNext,
            title = stringResource(R.string.settings_player_auto_advance),
            checked = preferences.autoAdvancePlaylist,
            onCheckedChange = onAutoAdvanceChanged,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow(
                icon = Icons.Default.PictureInPictureAlt,
                title = stringResource(R.string.settings_player_auto_pip),
                checked = preferences.autoEnterPip,
                onCheckedChange = onAutoEnterPipChanged,
            )
        }
    }
}

// --- Subtitles ----------------------------------------------------------------------------

/**
 * Font-size is a documented future extension point on `SubtitleStyleStore`'s DataStore file
 * (a second key alongside style, added when there's UI for it) — deliberately not built here
 * per this milestone's "without implementing unnecessary extras."
 */
@Composable
private fun SubtitlesSection(selected: SubtitleStyle, onSelected: (SubtitleStyle) -> Unit) {
    SettingsCardSection(titleRes = R.string.settings_subtitles_section) {
        Text(text = stringResource(R.string.settings_subtitles_default_style), style = MaterialTheme.typography.labelLarge)
        SubtitleStyleOptionRow(SubtitleStyle.CLASSIC, R.string.player_subtitle_style_classic, selected, onSelected)
        SubtitleStyleOptionRow(SubtitleStyle.CLEAN, R.string.player_subtitle_style_clean, selected, onSelected)
        SubtitleStyleOptionRow(SubtitleStyle.OUTLINED, R.string.player_subtitle_style_outlined, selected, onSelected)
    }
}

@Composable
private fun SubtitleStyleOptionRow(style: SubtitleStyle, labelRes: Int, selected: SubtitleStyle, onSelected: (SubtitleStyle) -> Unit) {
    val isSelected = style == selected
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelected(style) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = isSelected, onClick = { onSelected(style) })
        Icon(Icons.Default.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}

// --- Storage --------------------------------------------------------------------------------

@Composable
private fun StorageSection(freeStorageBytes: Long, onManageLibraryClick: () -> Unit) {
    SettingsCardSection(titleRes = R.string.settings_storage_section) {
        Text(
            text = stringResource(R.string.settings_storage_private_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val freeSpaceLabel = formatFileSizeLabel(freeStorageBytes)
        if (freeSpaceLabel != null) {
            InfoRow(icon = Icons.Default.Storage, text = stringResource(R.string.settings_storage_free_space, freeSpaceLabel))
        }
        SettingsActionRow(
            icon = Icons.Default.Storage,
            title = stringResource(R.string.settings_storage_manage_library),
            external = false,
            onClick = onManageLibraryClick,
        )
    }
}

// --- Privacy & Legal --------------------------------------------------------------------------

@Composable
private fun PrivacyLegalSection(onPrivacyClick: () -> Unit, onTermsClick: () -> Unit, onLicensesClick: () -> Unit) {
    SettingsCardSection(titleRes = R.string.settings_legal_section) {
        SettingsActionRow(icon = Icons.Default.Shield, title = stringResource(R.string.settings_legal_privacy), external = false, onClick = onPrivacyClick)
        SettingsActionRow(
            icon = Icons.Default.Gavel,
            title = stringResource(R.string.settings_legal_terms),
            subtitle = stringResource(R.string.settings_legal_terms_subtitle),
            external = false,
            onClick = onTermsClick,
        )
        SettingsActionRow(icon = Icons.Default.Policy, title = stringResource(R.string.settings_legal_licenses), external = false, onClick = onLicensesClick)
    }
}

// --- Feedback & Contact -----------------------------------------------------------------------

/**
 * Feedback is always sent as a plain, user-reviewed email the user's own client composes and
 * sends — nothing is collected or transmitted automatically. `ACTION_SENDTO` with a bare
 * `mailto:` [Uri] (recipient/subject/body passed as extras, not URI-encoded into the query
 * string) is the standard, most broadly-compatible way to target only email apps.
 */
@Composable
private fun FeedbackSection(context: Context, appVersionName: String) {
    var showEmailFallback by remember { mutableStateOf(false) }
    var emailCopied by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val subject = stringResource(R.string.settings_feedback_email_subject)
    val device = remember { "${Build.MANUFACTURER} ${Build.MODEL}".trim() }
    val body = stringResource(R.string.settings_feedback_email_body, appVersionName, device, Build.VERSION.RELEASE ?: "")

    SettingsCardSection(titleRes = R.string.settings_feedback_section) {
        SettingsActionRow(
            icon = Icons.Default.Email,
            title = stringResource(R.string.settings_feedback_send_email),
            subtitle = AppConfig.FEEDBACK_EMAIL,
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConfig.FEEDBACK_EMAIL))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                showEmailFallback = runCatching { context.startActivity(intent) }.isFailure
            },
        )
        if (showEmailFallback) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_feedback_no_email_app, AppConfig.FEEDBACK_EMAIL),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(AppConfig.FEEDBACK_EMAIL))
                    emailCopied = true
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.settings_feedback_copy_email))
                }
            }
            if (emailCopied) {
                Text(
                    text = stringResource(R.string.settings_support_copied),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        SettingsActionRow(
            icon = Icons.Default.Code,
            title = stringResource(R.string.settings_feedback_github_issues),
            onClick = { openExternalUrl(context, AppConfig.GITHUB_ISSUES_URL) },
        )
    }
}

// --- Updates ------------------------------------------------------------------------------

@Composable
private fun UpdatesSection(versionName: String, versionCode: Int, engineVersion: String?, context: Context) {
    SettingsCardSection(titleRes = R.string.settings_updates_section) {
        InfoRow(icon = Icons.Default.Info, text = stringResource(R.string.settings_updates_app_version, versionName, versionCode))
        if (engineVersion != null) {
            InfoRow(icon = Icons.Default.Code, text = stringResource(R.string.settings_updates_engine_version, engineVersion))
        }
        SettingsActionRow(
            icon = Icons.Default.SystemUpdateAlt,
            title = stringResource(R.string.settings_updates_check),
            subtitle = stringResource(R.string.settings_updates_check_subtitle),
            onClick = { openExternalUrl(context, AppConfig.GITHUB_RELEASES_URL) },
        )
        SettingsActionRow(
            icon = Icons.Default.Code,
            title = stringResource(R.string.settings_updates_repository),
            onClick = { openExternalUrl(context, AppConfig.GITHUB_REPOSITORY_URL) },
        )
    }
}

// --- About --------------------------------------------------------------------------------

@Composable
private fun AboutSection(versionName: String, onLicensesClick: () -> Unit, context: Context) {
    SettingsCardSection(titleRes = R.string.settings_about_section) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_about_version, versionName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsActionRow(
            icon = Icons.Default.Code,
            title = stringResource(R.string.settings_about_github),
            onClick = { openExternalUrl(context, AppConfig.GITHUB_REPOSITORY_URL) },
        )
        SettingsActionRow(
            icon = Icons.Default.Star,
            title = stringResource(R.string.settings_about_star_github),
            subtitle = stringResource(R.string.settings_about_star_github_subtitle),
            onClick = { openExternalUrl(context, AppConfig.GITHUB_REPOSITORY_URL) },
        )
        SettingsActionRow(
            icon = Icons.Default.Share,
            title = stringResource(R.string.settings_about_share),
            onClick = { shareMediaVault(context) },
        )
        SettingsActionRow(
            icon = Icons.Default.Article,
            title = stringResource(R.string.settings_about_credits),
            external = false,
            onClick = onLicensesClick,
        )
    }
}

// --- Shared row/section building blocks -------------------------------------------------------

@Composable
private fun SettingsCardSection(titleRes: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = stringResource(titleRes))
        MediaVaultCard(content = content)
    }
}

/** [external] picks the trailing icon: a chevron for in-app navigation, an "opens outside the app" glyph for anything that leaves it (browser, mail app, UPI chooser). */
@Composable
private fun SettingsActionRow(icon: ImageVector, title: String, subtitle: String? = null, external: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val trailingIcon = if (external) Icons.Default.OpenInNew else Icons.AutoMirrored.Filled.KeyboardArrowRight
        Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
