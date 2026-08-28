package app.picnic.player.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors
import java.util.Locale
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ChapterInfo
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

/** Translucent glass fill shared with the long-press context menus, so this dialog matches them. */
private val ContextMenuGlassFill = PicnicColors.GlassFill

/**
 * "View media info" — a scrollable, Jellyfin-web-style breakdown of a movie/episode file's media
 * source(s): container/size plus every video, audio and subtitle track with full technical detail.
 * Each track is a collapsible section; video and the primary (default) audio open by default,
 * additional audio and subtitle tracks start collapsed. Multi-version items expose a version picker.
 */
@Composable
fun MediaInfoDialog(
    item: BaseItemDto,
    onDismiss: () -> Unit,
    viewModel: MediaInfoViewModel = hiltViewModel(key = "mediainfo-${item.id}")
) {
    LaunchedEffect(item.id) { viewModel.load(item.id) }
    val state by viewModel.state.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            colors = SurfaceDefaults.colors(
                containerColor = ContextMenuGlassFill,
                contentColor = Color.White
            ),
            modifier = Modifier.width(540.dp).heightIn(max = 640.dp).padding(24.dp)
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = "Media info",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                item.name?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.height(20.dp))

                when (val s = state) {
                    is MediaInfoViewModel.State.Loading ->
                        Text(
                            text = "Loading…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                    is MediaInfoViewModel.State.Error ->
                        Text(
                            text = "Media info unavailable for this item.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                    is MediaInfoViewModel.State.Loaded ->
                        MediaInfoContent(sources = s.sources, chapters = s.chapters)
                }
            }
        }
    }
}

@Composable
private fun MediaInfoContent(sources: List<MediaSourceInfo>, chapters: List<ChapterInfo>) {
    var selected by remember { mutableStateOf(0) }
    val source = sources[selected.coerceIn(sources.indices)]
    val sections = remember(source, chapters) { buildSections(source, chapters) }
    val hasPicker = sources.size > 1
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (hasPicker) {
            VersionPicker(
                sources = sources,
                selected = selected,
                firstFocus = firstFocus,
                onSelect = { selected = it }
            )
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(sections) { index, section ->
                SectionCard(
                    section = section,
                    rowFocus = if (index == 0 && !hasPicker) firstFocus else null,
                    blockUp = index == 0 && !hasPicker,
                    blockDown = index == sections.lastIndex
                )
            }
        }
    }

    LaunchedEffect(source) { firstFocus.requestFocusWhenAttached(maxFrames = 30) }
}

@Composable
private fun VersionPicker(
    sources: List<MediaSourceInfo>,
    selected: Int,
    firstFocus: FocusRequester,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sources.forEachIndexed { index, source ->
            var focused by remember { mutableStateOf(false) }
            val active = index == selected
            Text(
                text = source.name?.takeIf { it.isNotBlank() } ?: "Version ${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = if (active || focused) Color.White else Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            focused -> Color.White.copy(alpha = 0.20f)
                            active -> Color.White.copy(alpha = 0.10f)
                            else -> Color.White.copy(alpha = 0.04f)
                        }
                    )
                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                    .focusProperties { if (index == 0) up = FocusRequester.Cancel }
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionCard(
    section: InfoSection,
    rowFocus: FocusRequester?,
    blockUp: Boolean,
    blockDown: Boolean
) {
    var expanded by remember(section) { mutableStateOf(section.expanded) }
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f)
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (rowFocus != null) Modifier.focusRequester(rowFocus) else Modifier)
                .focusProperties {
                    if (blockUp) up = FocusRequester.Cancel
                    if (blockDown && !expanded) down = FocusRequester.Cancel
                }
                .onFocusChanged { focused = it.isFocused }
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                section.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            ) {
                section.rows.forEach { (label, value) -> PropertyRow(label, value) }
            }
        }
    }
}

// Rows are focusable purely so D-pad traversal can walk (and thus scroll) through a section taller
// than the dialog — inside a Dialog window focus is contained, so there is no risk of it escaping.
@Composable
private fun PropertyRow(label: String, value: String) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.width(150.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f)
        )
    }
}

// --- Section / row assembly ---------------------------------------------------------------------

private data class InfoSection(
    val title: String,
    val subtitle: String?,
    val rows: List<Pair<String, String>>,
    val expanded: Boolean
)

private fun buildSections(source: MediaSourceInfo, chapters: List<ChapterInfo>): List<InfoSection> {
    val streams = source.mediaStreams.orEmpty()
    val sections = mutableListOf<InfoSection>()

    fileRows(source).let {
        if (it.isNotEmpty()) sections += InfoSection("File", null, it, expanded = true)
    }

    val videos = streams.filter { it.type == MediaStreamType.VIDEO }
    videos.forEachIndexed { i, s ->
        sections += InfoSection(
            title = if (videos.size > 1) "Video ${i + 1}" else "Video",
            subtitle = s.displayTitle?.takeIf { it.isNotBlank() },
            rows = videoRows(s),
            expanded = true
        )
    }

    val audios = streams.filter { it.type == MediaStreamType.AUDIO }
    val primaryAudio = audios.indexOfFirst { it.isDefault }.takeIf { it >= 0 } ?: 0
    audios.forEachIndexed { i, s ->
        sections += InfoSection(
            title = if (audios.size > 1) "Audio ${i + 1}" else "Audio",
            subtitle = s.displayTitle?.takeIf { it.isNotBlank() },
            rows = audioRows(s),
            expanded = i == primaryAudio
        )
    }

    val subtitles = streams.filter { it.type == MediaStreamType.SUBTITLE }
    subtitles.forEachIndexed { i, s ->
        sections += InfoSection(
            title = if (subtitles.size > 1) "Subtitle ${i + 1}" else "Subtitle",
            subtitle = s.displayTitle?.takeIf { it.isNotBlank() },
            rows = subtitleRows(s),
            expanded = false
        )
    }

    if (chapters.isNotEmpty()) {
        val rows = chapters.mapIndexed { i, chapter ->
            formatTicks(chapter.startPositionTicks) to
                (chapter.name?.takeIf { it.isNotBlank() } ?: "Chapter ${i + 1}")
        }
        sections += InfoSection(
            title = "Chapters",
            subtitle = "${chapters.size} chapters",
            rows = rows,
            expanded = false
        )
    }

    return sections
}

private fun fileRows(source: MediaSourceInfo): List<Pair<String, String>> = buildRows {
    row("Container", source.container?.uppercase(Locale.US))
    row("Path", source.path)
    row("Size", source.size?.let(::formatBytes))
    row("Bitrate", source.bitrate?.toLong()?.let(::formatBitrate))
}

private fun videoRows(s: MediaStream): List<Pair<String, String>> = buildRows {
    row("Title", s.title)
    row("Codec", s.codec?.uppercase(Locale.US))
    row("Codec tag", s.codecTag)
    row("Profile", s.profile)
    row("Level", s.level?.toString())
    if (s.width != null && s.height != null) row("Resolution", "${s.width} × ${s.height}")
    row("Aspect ratio", s.aspectRatio)
    row("Anamorphic", s.isAnamorphic?.let(::yesNo))
    if (s.isInterlaced) row("Interlaced", "Yes")
    val fps = s.realFrameRate ?: s.averageFrameRate
    row("Frame rate", fps?.let { String.format(Locale.US, "%.3f fps", it) })
    row("Bit depth", s.bitDepth?.let { "$it bit" })
    row("Video range", s.videoRange?.serialName?.takeUnless { it.equals("Unknown", ignoreCase = true) })
    row("Range type", s.videoRangeType?.serialName?.takeUnless { it.equals("Unknown", ignoreCase = true) })
    row("Dolby Vision", s.videoDoViTitle)
    row("Color space", s.colorSpace)
    row("Color transfer", s.colorTransfer)
    row("Color primaries", s.colorPrimaries)
    row("Color range", s.colorRange)
    row("Pixel format", s.pixelFormat)
    row("Reference frames", s.refFrames?.toString())
    row("NAL", s.nalLengthSize)
    row("Bitrate", s.bitRate?.toLong()?.let(::formatBitrate))
}

private fun audioRows(s: MediaStream): List<Pair<String, String>> = buildRows {
    row("Title", s.title)
    row("Codec", s.codec?.uppercase(Locale.US))
    row("Codec tag", s.codecTag)
    row("Language", s.language)
    row("Layout", s.channelLayout)
    row("Channels", s.channels?.let { "$it ch" })
    row("Bitrate", s.bitRate?.toLong()?.let(::formatBitrate))
    row("Sample rate", s.sampleRate?.let { "$it Hz" })
    row("Bit depth", s.bitDepth?.let { "$it bit" })
    row("Spatial audio", s.audioSpatialFormat?.serialName?.takeUnless { it.equals("None", ignoreCase = true) })
    row("Default", yesNo(s.isDefault))
}

private fun subtitleRows(s: MediaStream): List<Pair<String, String>> = buildRows {
    row("Title", s.title)
    row("Codec", s.codec?.uppercase(Locale.US))
    row("Language", s.language)
    row("Default", yesNo(s.isDefault))
    row("Forced", yesNo(s.isForced))
    row("External", yesNo(s.isExternal))
    if (s.isHearingImpaired) row("Hearing impaired", "Yes")
    row("Delivery", s.deliveryMethod?.serialName)
}

private class RowBuilder {
    val rows = mutableListOf<Pair<String, String>>()
    fun row(label: String, value: String?) {
        if (!value.isNullOrBlank()) rows += label to value
    }
}

private inline fun buildRows(block: RowBuilder.() -> Unit): List<Pair<String, String>> = RowBuilder().apply(block).rows

private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GiB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.2f MiB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.2f KiB", kb)
        else -> "$bytes B"
    }
}

/** Ticks (100 ns units) → H:MM:SS, dropping the hours field when zero. */
internal fun formatTicks(ticks: Long): String {
    val totalSeconds = ticks / 10_000_000L
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

private fun formatBitrate(bitrate: Long): String {
    val kbps = bitrate / 1000.0
    val mbps = kbps / 1000.0
    return when {
        mbps >= 1.0 -> String.format(Locale.US, "%.2f Mbps", mbps)
        else -> String.format(Locale.US, "%.0f Kbps", kbps)
    }
}
