package com.mediavault.app.ui.screens.home

import com.mediavault.core.domain.download.FormatSelectionModel
import com.mediavault.core.domain.download.QualityTier
import com.mediavault.core.domain.download.ResolvedSelection
import com.mediavault.core.domain.download.resolveSelection

/**
 * Pure: turns a [SelectedQualityState] into a real [ResolvedSelection] against [this] model, or
 * null while nothing selectable has actually been picked yet (a fresh analysis, or a tier with
 * no matching group — shouldn't normally happen since [withTierSelected] only ever sets a tier
 * that exists in the model, but never assumed here). Shared by both the single-item screen and
 * the playlist quality-setup step — see each `HomeViewModel` call site — so there is exactly one
 * place a picker selection becomes a download-ready shape.
 */
fun FormatSelectionModel.resolve(selection: SelectedQualityState): ResolvedSelection? {
    val video = selection.tier?.let { tier ->
        val group = videoQualityGroups.firstOrNull { it.tier == tier } ?: return null
        group.variants.firstOrNull { it.formatId == selection.videoVariantFormatId } ?: group.bestVariant
    }
    val audios = selection.selectedAudioFormatIds.mapNotNull { id -> audioTracks.firstOrNull { it.formatId == id } }
    if (video == null && audios.isEmpty()) return null
    return resolveSelection(video, audios)
}

/**
 * A fresh selection for [tier]: its own best variant, and — only when that variant has no
 * embedded audio and separate audio tracks actually exist — one pre-picked audio track (the
 * first one the model reports; not sourced as any kind of "default" the extractor flagged, just
 * a deterministic, always-something-selected starting point the user is free to change) so
 * picking a quality never leaves the screen in a dead state requiring an extra, easy-to-miss tap
 * before Download enables. Always resets any previous audio-track selection — switching tiers
 * starts the audio pick fresh rather than trying to carry a choice across tiers that may not
 * offer the same tracks.
 */
fun SelectedQualityState.withTierSelected(tier: QualityTier, model: FormatSelectionModel): SelectedQualityState {
    val group = model.videoQualityGroups.firstOrNull { it.tier == tier }
        ?: return copy(tier = tier, videoVariantFormatId = null, includeMultipleAudio = false, selectedAudioFormatIds = emptySet())

    val defaultAudioId = if (!group.bestVariant.hasAudio && model.audioTracks.isNotEmpty()) {
        model.audioTracks.first().formatId
    } else {
        null
    }

    return SelectedQualityState(
        tier = tier,
        videoVariantFormatId = null,
        includeMultipleAudio = false,
        selectedAudioFormatIds = setOfNotNull(defaultAudioId),
    )
}

/**
 * Bundles the format-selection screen's picker callbacks into one parameter instead of four
 * growing `HomeScreenContent`'s already-long lambda-parameter list — reused identically for the
 * single-item picker and the playlist quality-setup step's own copy of the same picker UI.
 */
data class QualityPickerActions(
    val onTierSelected: (QualityTier) -> Unit,
    val onVariantSelected: (String) -> Unit,
    val onIncludeMultipleAudioToggled: (Boolean) -> Unit,
    val onAudioTrackToggled: (String) -> Unit,
)
