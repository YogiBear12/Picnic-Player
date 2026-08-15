package app.picnic.player.ui.settings

import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.data.playback.quality.defaultQualityLabel
import app.picnic.player.data.playback.resolveLanguageCode
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SegmentAction
import app.picnic.player.data.settings.ThemeMusicVolume

enum class SettingsCategory(val label: String) {
    REQUESTS("Requests"),
    EXPERIENCE("Experience"),
    PLAYBACK("Playback"),
    ADVANCED("Advanced"),
    ACCOUNT("Account"),
    ABOUT("About")
}

/** One tunable setting rendered in the detail panel. [description] shows a muted
 *  helper line under the label; null hides it. */
internal data class SettingItem(
    val label: String,
    val value: String,
    val description: String? = null,
    val onActivate: () -> Unit,
    /** When false the row is greyed out and cannot be activated (still focusable). */
    val enabled: Boolean = true,
    /** Set when the row opens a full-screen sub-page — the focus-restore key on the way back. */
    val subPage: SubPageRow? = null
)

/** A group of rows within a category's detail panel. [title] draws a section
 *  header above the rows; null renders the rows with no header. */
internal data class SettingSection(
    val title: String?,
    val items: List<SettingItem>
)

/** An open multi-option picker (standard glass dialog). */
internal data class ActivePicker(
    val title: String,
    val options: List<PickerOption>
)

private fun ThemeMusicVolume.display(): String = when (this) {
    ThemeMusicVolume.DISABLED -> "Off"
    ThemeMusicVolume.QUIET -> "Quiet"
    ThemeMusicVolume.LOW -> "Low"
    ThemeMusicVolume.MEDIUM -> "Medium"
    ThemeMusicVolume.HIGH -> "High"
    ThemeMusicVolume.LOUD -> "Loud"
}

private fun SegmentAction.display(): String = when (this) {
    SegmentAction.ASK_TO_SKIP -> "Ask to skip"
    SegmentAction.SKIP_AUTOMATICALLY -> "Skip automatically"
    SegmentAction.DO_NOT_SKIP -> "Do not skip"
}

private fun secondsPicker(
    title: String,
    options: List<Int>,
    current: Int,
    onSelect: (Int) -> Unit
) = ActivePicker(
    title = title,
    options = options.map { s ->
        PickerOption(label = "${s}s", selected = s == current, onSelect = { onSelect(s) })
    }
)

private fun defaultQualityPicker(
    current: QualityRung?,
    onSelect: (QualityRung?) -> Unit
) = ActivePicker(
    title = "Default video quality",
    options = (listOf(null) + QualityRung.entries).map { quality ->
        PickerOption(
            label = defaultQualityLabel(quality),
            selected = quality == current,
            onSelect = { onSelect(quality) }
        )
    }
)

private fun segmentPicker(
    title: String,
    current: SegmentAction,
    onSelect: (SegmentAction) -> Unit
) = ActivePicker(
    title = title,
    options = SegmentAction.entries.map { action ->
        PickerOption(label = action.display(), selected = action == current, onSelect = { onSelect(action) })
    }
)

internal fun sectionsFor(
    category: SettingsCategory,
    settings: PlaybackSettings,
    imageCacheSize: Long,
    context: Context,
    viewModel: SettingsViewModel,
    youtubeApps: List<YouTubeAppInfo>,
    pictureInPictureSupported: Boolean,
    serverAudioLanguage: String?,
    serverSubtitleLanguage: String?,
    showPicker: (ActivePicker) -> Unit,
    onShowAudioLanguagePicker: () -> Unit,
    onShowSubtitleLanguagePicker: () -> Unit,
    onOpenSubtitleAppearance: () -> Unit
): List<SettingSection> = when (category) {
    // Account, Requests and About render dedicated panels, not generic rows.
    SettingsCategory.ACCOUNT, SettingsCategory.REQUESTS, SettingsCategory.ABOUT -> emptyList()
    SettingsCategory.EXPERIENCE -> listOf(
        SettingSection(
            null,
            buildList {
                add(
                    SettingItem(
                        "Sign in automatically",
                        if (settings.autoLoginLastUser) "On" else "Off",
                        "Skip the profile picker and sign in as the last-used profile",
                        viewModel::toggleAutoLoginLastUser
                    )
                )
                add(
                    SettingItem(
                        "Ambient backgrounds",
                        if (settings.ambientBackgrounds) "On" else "Off",
                        "Tint the background with colors extracted from the backdrop",
                        viewModel::toggleAmbientBackgrounds
                    )
                )
                add(
                    SettingItem(
                        "Ambient focus indicators",
                        if (settings.colouredFocus) "On" else "Off",
                        "Focus indicators dynamically change colors based on the focused card",
                        viewModel::toggleColouredFocus
                    )
                )
                add(
                    SettingItem(
                        "Live focus indicators",
                        if (settings.pulseFocusGlow) "On" else "Off",
                        "The glow from focus indicators gently pulse behind the card",
                        viewModel::togglePulseFocusGlow
                    )
                )
                add(
                    SettingItem(
                        "Limit episode badges",
                        if (settings.capBadgeCount) "On" else "Off",
                        "Unwatched episode counts are displayed as 99+ when the count exceeds 100",
                        viewModel::toggleCapBadgeCount
                    )
                )
                add(
                    SettingItem(
                        "Theme music",
                        settings.themeMusicVolume.display(),
                        "Play an item's theme music while browsing its details",
                        onActivate = {
                            showPicker(
                                ActivePicker(
                                    title = "Theme music",
                                    options = ThemeMusicVolume.entries.map { volume ->
                                        PickerOption(
                                            label = volume.display(),
                                            selected = volume == settings.themeMusicVolume,
                                            onSelect = { viewModel.setThemeMusicVolume(volume) }
                                        )
                                    }
                                )
                            )
                        }
                    )
                )
            }
        )
    )
    SettingsCategory.PLAYBACK -> listOf(
        SettingSection(
            "User Preferences",
            listOf(
                SettingItem(
                    "Default video quality",
                    defaultQualityLabel(settings.defaultVideoQuality),
                    onActivate = {
                        showPicker(
                            defaultQualityPicker(settings.defaultVideoQuality, viewModel::setDefaultVideoQuality)
                        )
                    }
                ),
                SettingItem(
                    "Preferred audio language",
                    if (settings.preferDefaultAudioTrack) {
                        "Original language"
                    } else {
                        viewModel.languageLabel(
                            resolveLanguageCode(
                                settings.preferredAudioLanguage,
                                serverAudioLanguage,
                                viewModel.deviceLanguage
                            )
                        )
                    },
                    onActivate = onShowAudioLanguagePicker
                ),
                SettingItem(
                    "Preferred subtitle language",
                    viewModel.languageLabel(
                        resolveLanguageCode(
                            settings.preferredSubtitleLanguage,
                            serverSubtitleLanguage,
                            viewModel.deviceLanguage
                        )
                    ),
                    onActivate = onShowSubtitleLanguagePicker
                ),
                SettingItem(
                    "Always display subtitles",
                    if (settings.alwaysDisplaySubtitles) "On" else "Off",
                    onActivate = viewModel::toggleAlwaysDisplaySubtitles
                ),
                SettingItem(
                    "Subtitle appearance",
                    "",
                    onActivate = onOpenSubtitleAppearance,
                    subPage = SubPageRow.SUBTITLE_APPEARANCE
                )
            )
        ),
        SettingSection(
            "Controls",
            listOf(
                SettingItem(
                    "Skip forward",
                    "${settings.skipForwardSeconds}s",
                    onActivate = {
                        showPicker(
                            secondsPicker(
                                "Skip forward",
                                SettingsViewModel.SKIP_FORWARD_OPTIONS,
                                settings.skipForwardSeconds,
                                viewModel::setSkipForwardSeconds
                            )
                        )
                    }
                ),
                SettingItem(
                    "Skip back",
                    "${settings.skipBackwardSeconds}s",
                    onActivate = {
                        showPicker(
                            secondsPicker(
                                "Skip back",
                                SettingsViewModel.SKIP_BACKWARD_OPTIONS,
                                settings.skipBackwardSeconds,
                                viewModel::setSkipBackwardSeconds
                            )
                        )
                    }
                ),
                SettingItem(
                    "Hide playback controls",
                    "${settings.osdHideSeconds}s",
                    onActivate = {
                        showPicker(
                            secondsPicker(
                                "Hide playback controls",
                                SettingsViewModel.HIDE_CONTROLS_OPTIONS,
                                settings.osdHideSeconds,
                                viewModel::setOsdHideSeconds
                            )
                        )
                    }
                )
            )
        ),
        SettingSection(
            "Next up behavior",
            listOf(
                SettingItem(
                    "Display next up during outro",
                    if (settings.displayNextUpDuringOutro) "On" else "Off",
                    onActivate = viewModel::toggleDisplayNextUpDuringOutro
                ),
                SettingItem(
                    "Next up countdown",
                    "${settings.nextUpCountdownSeconds}s",
                    onActivate = {
                        showPicker(
                            secondsPicker(
                                "Next up countdown",
                                SettingsViewModel.NEXT_UP_COUNTDOWN_OPTIONS,
                                settings.nextUpCountdownSeconds,
                                viewModel::setNextUpCountdownSeconds
                            )
                        )
                    }
                )
            )
        ),
        SettingSection(
            "Media Segments",
            listOf(
                SettingItem(
                    "Intros",
                    settings.introAction.display(),
                    onActivate = { showPicker(segmentPicker("Intros", settings.introAction, viewModel::setIntroAction)) }
                ),
                SettingItem(
                    "Recaps",
                    settings.recapAction.display(),
                    onActivate = { showPicker(segmentPicker("Recaps", settings.recapAction, viewModel::setRecapAction)) }
                ),
                SettingItem(
                    "Outros",
                    settings.outroAction.display(),
                    onActivate = { showPicker(segmentPicker("Outros", settings.outroAction, viewModel::setOutroAction)) }
                ),
                SettingItem(
                    "Previews",
                    settings.previewAction.display(),
                    onActivate = {
                        showPicker(segmentPicker("Previews", settings.previewAction, viewModel::setPreviewAction))
                    }
                ),
                SettingItem(
                    "Commercials",
                    settings.commercialAction.display(),
                    onActivate = {
                        showPicker(
                            segmentPicker("Commercials", settings.commercialAction, viewModel::setCommercialAction)
                        )
                    }
                )
            )
        )
    )
    SettingsCategory.ADVANCED -> listOf(
        SettingSection(
            null,
            buildList {
                if (pictureInPictureSupported) {
                    add(
                        SettingItem(
                            "Enable Picture-in-Picture",
                            if (settings.pictureInPicture) "On" else "Off",
                            onActivate = viewModel::togglePictureInPicture
                        )
                    )
                }
                add(
                    SettingItem(
                        "Refresh rate switching",
                        if (settings.matchRefreshRate) "On" else "Off",
                        onActivate = viewModel::toggleMatchRefreshRate
                    )
                )
                add(
                    SettingItem(
                        "Resolution switching",
                        if (settings.matchResolution) "On" else "Off",
                        onActivate = viewModel::toggleMatchResolution
                    )
                )
                add(
                    SettingItem(
                        "Force direct play",
                        if (settings.forceDirectPlay) "On" else "Off",
                        onActivate = viewModel::toggleForceDirectPlay
                    )
                )
                add(
                    SettingItem(
                        "Downmix to stereo",
                        if (settings.downmixStereo) "On" else "Off",
                        // Force direct play announces full compatibility, so this has no effect.
                        enabled = !settings.forceDirectPlay,
                        onActivate = viewModel::toggleDownmixStereo
                    )
                )
                add(
                    SettingItem(
                        "Force DoVi Profile 7 support",
                        if (settings.forceDoviProfile7) "On" else "Off",
                        enabled = !settings.forceDirectPlay,
                        onActivate = viewModel::toggleForceDoviProfile7
                    )
                )
                add(
                    SettingItem(
                        "Enable 4K transcoding",
                        if (settings.allowFourKTranscoding) "On" else "Off",
                        enabled = !settings.forceDirectPlay,
                        onActivate = viewModel::toggleAllowFourKTranscoding
                    )
                )
                add(
                    SettingItem(
                        "External application for trailers",
                        youtubeApps.find { it.packageName == settings.trailerYouTubePackage }?.name ?: "System Default",
                        onActivate = {
                            showPicker(
                                ActivePicker(
                                    title = "External application for trailers",
                                    options = buildList {
                                        add(
                                            PickerOption(
                                                label = "System Default",
                                                selected = settings.trailerYouTubePackage == null,
                                                onSelect = { viewModel.setTrailerYouTubePackage(null) }
                                            )
                                        )
                                        youtubeApps.forEach { app ->
                                            add(
                                                PickerOption(
                                                    label = app.name,
                                                    selected = app.packageName == settings.trailerYouTubePackage,
                                                    icon = app.icon,
                                                    onSelect = { viewModel.setTrailerYouTubePackage(app.packageName) }
                                                )
                                            )
                                        }
                                    }
                                )
                            )
                        }
                    )
                )
                add(
                    SettingItem(
                        "Clear image cache",
                        Formatter.formatFileSize(context, imageCacheSize),
                        onActivate = viewModel::clearImageCache
                    )
                )
            }
        )
    )
}
