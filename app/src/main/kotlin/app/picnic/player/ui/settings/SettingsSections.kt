package app.picnic.player.ui.settings

import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.data.playback.quality.defaultQualityLabel
import app.picnic.player.data.playback.resolveLanguageCode
import app.picnic.player.data.settings.BurnInSubtitles
import app.picnic.player.data.settings.PlaybackSettings
import app.picnic.player.data.settings.SegmentAction
import app.picnic.player.data.settings.SettingKey
import app.picnic.player.data.settings.SettingKeys
import app.picnic.player.data.settings.ThemeMusicVolume

enum class SettingsCategory(val label: String) {
    REQUESTS("Requests"),
    EXPERIENCE("Experience"),
    PLAYBACK("Playback"),
    ADVANCED("Advanced"),
    ACCOUNT("Account"),
    ABOUT("About")
}

internal data class SettingItem(
    val label: String,
    val value: String,
    val description: String? = null,
    val onActivate: () -> Unit,
    val enabled: Boolean = true,
    val subPage: SubPageRow? = null
)

internal data class SettingSection(
    val title: String?,
    val items: List<SettingItem>
)

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

private fun SettingsViewModel.toggleItem(
    settings: PlaybackSettings,
    label: String,
    key: SettingKey<Boolean>,
    description: String? = null,
    enabled: Boolean = true
) = SettingItem(
    label = label,
    value = if (key.read(settings)) "On" else "Off",
    description = description,
    onActivate = { toggle(key) },
    enabled = enabled
)

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

private fun burnInSubtitlesLabel(mode: BurnInSubtitles): String = when (mode) {
    BurnInSubtitles.OFF -> "Off"
    BurnInSubtitles.AUTOMATIC -> "Automatic"
    BurnInSubtitles.ALWAYS -> "Always"
}

private fun burnInSubtitlesPicker(
    current: BurnInSubtitles,
    onSelect: (BurnInSubtitles) -> Unit
) = ActivePicker(
    title = "Burn in subtitles",
    options = BurnInSubtitles.entries.map { mode ->
        PickerOption(
            label = burnInSubtitlesLabel(mode),
            selected = mode == current,
            onSelect = { onSelect(mode) }
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
    SettingsCategory.ACCOUNT, SettingsCategory.REQUESTS, SettingsCategory.ABOUT -> emptyList()
    SettingsCategory.EXPERIENCE -> experienceSections(
        settings,
        imageCacheSize,
        context,
        viewModel,
        youtubeApps,
        pictureInPictureSupported,
        serverAudioLanguage,
        serverSubtitleLanguage,
        showPicker,
        onShowAudioLanguagePicker,
        onShowSubtitleLanguagePicker,
        onOpenSubtitleAppearance
    )
    SettingsCategory.PLAYBACK -> playbackSections(
        settings,
        imageCacheSize,
        context,
        viewModel,
        youtubeApps,
        pictureInPictureSupported,
        serverAudioLanguage,
        serverSubtitleLanguage,
        showPicker,
        onShowAudioLanguagePicker,
        onShowSubtitleLanguagePicker,
        onOpenSubtitleAppearance
    )
    SettingsCategory.ADVANCED -> advancedSections(
        settings,
        imageCacheSize,
        context,
        viewModel,
        youtubeApps,
        pictureInPictureSupported,
        serverAudioLanguage,
        serverSubtitleLanguage,
        showPicker,
        onShowAudioLanguagePicker,
        onShowSubtitleLanguagePicker,
        onOpenSubtitleAppearance
    )
}

private fun experienceSections(
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
): List<SettingSection> = listOf(
    SettingSection(
        null,
        buildList {
            add(
                viewModel.toggleItem(
                    settings,
                    "Sign in automatically",
                    SettingKeys.AutoLoginLastUser,
                    "Skip the profile picker and sign in as the last-used profile"
                )
            )
            add(
                viewModel.toggleItem(
                    settings,
                    "Ambient backgrounds",
                    SettingKeys.AmbientBackgrounds,
                    "Tint the background with colors extracted from the backdrop"
                )
            )
            add(
                viewModel.toggleItem(
                    settings,
                    "Ambient focus indicators",
                    SettingKeys.ColouredFocus,
                    "Focus indicators dynamically change colors based on the focused card"
                )
            )
            add(
                viewModel.toggleItem(
                    settings,
                    "Live focus indicators",
                    SettingKeys.PulseFocusGlow,
                    "The glow from focus indicators gently pulse behind the card"
                )
            )
            add(
                viewModel.toggleItem(
                    settings,
                    "Limit episode badges",
                    SettingKeys.CapBadgeCount,
                    "Unwatched episode counts are displayed as 99+ when the count exceeds 100"
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
                                        onSelect = { viewModel.set(SettingKeys.ThemeMusicVolume, volume) }
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

private fun playbackSections(
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
): List<SettingSection> = listOf(
    SettingSection(
        "User Preferences",
        listOf(
            SettingItem(
                "Default video quality",
                defaultQualityLabel(settings.defaultVideoQuality),
                onActivate = {
                    showPicker(
                        defaultQualityPicker(settings.defaultVideoQuality) { viewModel.set(SettingKeys.DefaultVideoQuality, it) }
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
            viewModel.toggleItem(settings, "Always display subtitles", SettingKeys.AlwaysDisplaySubtitles),
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
                            settings.skipForwardSeconds
                        ) { viewModel.set(SettingKeys.SkipForwardSeconds, it) }
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
                            settings.skipBackwardSeconds
                        ) { viewModel.set(SettingKeys.SkipBackwardSeconds, it) }
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
                            settings.osdHideSeconds
                        ) { viewModel.set(SettingKeys.OsdHideSeconds, it) }
                    )
                }
            )
        )
    ),
    SettingSection(
        "Next up behavior",
        listOf(
            viewModel.toggleItem(settings, "Display next up during outro", SettingKeys.DisplayNextUpDuringOutro),
            SettingItem(
                "Next up countdown",
                "${settings.nextUpCountdownSeconds}s",
                onActivate = {
                    showPicker(
                        secondsPicker(
                            "Next up countdown",
                            SettingsViewModel.NEXT_UP_COUNTDOWN_OPTIONS,
                            settings.nextUpCountdownSeconds
                        ) { viewModel.set(SettingKeys.NextUpCountdownSeconds, it) }
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
                onActivate = { showPicker(segmentPicker("Intros", settings.introAction) { viewModel.set(SettingKeys.IntroAction, it) }) }
            ),
            SettingItem(
                "Recaps",
                settings.recapAction.display(),
                onActivate = { showPicker(segmentPicker("Recaps", settings.recapAction) { viewModel.set(SettingKeys.RecapAction, it) }) }
            ),
            SettingItem(
                "Outros",
                settings.outroAction.display(),
                onActivate = { showPicker(segmentPicker("Outros", settings.outroAction) { viewModel.set(SettingKeys.OutroAction, it) }) }
            ),
            SettingItem(
                "Previews",
                settings.previewAction.display(),
                onActivate = {
                    showPicker(segmentPicker("Previews", settings.previewAction) { viewModel.set(SettingKeys.PreviewAction, it) })
                }
            ),
            SettingItem(
                "Commercials",
                settings.commercialAction.display(),
                onActivate = {
                    showPicker(
                        segmentPicker("Commercials", settings.commercialAction) { viewModel.set(SettingKeys.CommercialAction, it) }
                    )
                }
            )
        )
    )
)

private fun advancedSections(
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
): List<SettingSection> = listOf(
    SettingSection(
        null,
        buildList {
            if (pictureInPictureSupported) {
                add(
                    viewModel.toggleItem(settings, "Enable Picture-in-Picture", SettingKeys.PictureInPicture)
                )
            }
            add(
                viewModel.toggleItem(settings, "Refresh rate switching", SettingKeys.MatchRefreshRate)
            )
            add(
                viewModel.toggleItem(settings, "Resolution switching", SettingKeys.MatchResolution)
            )
            add(
                viewModel.toggleItem(settings, "Force direct play", SettingKeys.ForceDirectPlay)
            )
            add(
                viewModel.toggleItem(
                    settings,
                    "Downmix to stereo",
                    SettingKeys.DownmixStereo,
                    enabled = !settings.forceDirectPlay
                )
            )
            add(
                viewModel.toggleItem(
                    settings,
                    "Force DoVi Profile 7 support",
                    SettingKeys.ForceDoviProfile7,
                    enabled = !settings.forceDirectPlay
                )
            )
            add(
                viewModel.toggleItem(
                    settings,
                    "Enable 4K transcoding",
                    SettingKeys.AllowFourKTranscoding,
                    enabled = !settings.forceDirectPlay
                )
            )
            add(
                SettingItem(
                    "Burn in subtitles",
                    burnInSubtitlesLabel(settings.burnInSubtitles),
                    onActivate = {
                        showPicker(
                            burnInSubtitlesPicker(settings.burnInSubtitles) {
                                viewModel.set(SettingKeys.BurnInSubtitles, it)
                            }
                        )
                    },
                    enabled = !settings.forceDirectPlay
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
                                            onSelect = { viewModel.set(SettingKeys.TrailerYouTubePackage, null) }
                                        )
                                    )
                                    youtubeApps.forEach { app ->
                                        add(
                                            PickerOption(
                                                label = app.name,
                                                selected = app.packageName == settings.trailerYouTubePackage,
                                                icon = app.icon,
                                                onSelect = { viewModel.set(SettingKeys.TrailerYouTubePackage, app.packageName) }
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
