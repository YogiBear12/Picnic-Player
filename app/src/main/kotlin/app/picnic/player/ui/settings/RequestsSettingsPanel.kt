package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrImages
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.seerr.SeerrMediaRequest
import app.picnic.player.data.seerr.SeerrMediaType
import app.picnic.player.data.seerr.SeerrRequestDisplay
import app.picnic.player.data.seerr.SeerrRequestStatus
import app.picnic.player.data.seerr.formatRequestedSeasonsLabel
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Partitioned Settings → Requests lists (#77). Declined/Failed omitted. */
internal data class SettingsRequestSections(
    val active: List<SeerrRequestDisplay>,
    val completed: List<SeerrRequestDisplay>
)

/**
 * Your Requests = Pending + In Progress (Approved); null/unknown status treated as
 * Pending (same as [requestRowStatusLabel]). Completed = Completed only. Sorted A–Z
 * by display title (case-insensitive, stable).
 */
internal fun partitionSettingsRequestSections(
    requests: List<SeerrRequestDisplay>
): SettingsRequestSections {
    val byTitle = compareBy(String.CASE_INSENSITIVE_ORDER) { row: SeerrRequestDisplay -> row.title }
    val active = requests
        .filter { row ->
            when (row.request.status) {
                SeerrRequestStatus.PENDING,
                SeerrRequestStatus.APPROVED,
                null
                -> true
                SeerrRequestStatus.COMPLETED,
                SeerrRequestStatus.DECLINED,
                SeerrRequestStatus.FAILED
                -> false
                else -> true // unknown → Pending chip → Your Requests
            }
        }
        .sortedWith(byTitle)
    val completed = requests
        .filter { it.request.status == SeerrRequestStatus.COMPLETED }
        .sortedWith(byTitle)
    return SettingsRequestSections(active = active, completed = completed)
}

/**
 * Settings → Requests: purely the request lists. The Seerr connection lives in
 * Account, and the whole category is hidden from the rail while unlinked — the
 * unlinked branch here is only a safety net for transient states.
 */
@Composable
internal fun RequestsSettingsPanel(
    viewModel: SettingsViewModel,
    enterFr: FocusRequester,
    leftFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onOpenSeerrDetail: ((SeerrMediaRequest) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val seerr by viewModel.seerrState.collectAsStateWithLifecycle()
    val requests by viewModel.myRequests.collectAsStateWithLifecycle()
    val sections = remember(requests) { partitionSettingsRequestSections(requests) }
    val activeListState = rememberLazyListState()
    val completedListState = rememberLazyListState()
    val rowFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val completedHeaderFr = remember { FocusRequester() }
    var completedExpanded by remember { mutableStateOf(false) }
    var contextRequest by remember { mutableStateOf<SeerrRequestDisplay?>(null) }
    val panelScope = rememberCoroutineScope()

    // Refresh then seed in one effect so Cancel→Back awaits the post-cancel list (#44).
    // Keyed on linkState (not Unit alone): Linked enter refreshes; rememberFocusedRequest on
    // activate must not retrigger seed/clear while still on this panel.
    // SettingsViewModel.selectCategory ignores non-REQUESTS while focusedRequestId is set so
    // incidental Account rail focus cannot dispose this panel before seed completes.
    // #77: seed against visible Your Requests; missing id → first active card; empty active
    // → Completed header (if any) else Requests rail. Remembered Completed row expands section.
    LaunchedEffect(seerr.linkState) {
        if (seerr.linkState != SeerrLinkState.Linked) {
            if (viewModel.focusedRequestId.value != null) {
                viewModel.clearFocusedRequest()
                leftFocus.requestFocusWhenAttached()
            }
            return@LaunchedEffect
        }
        viewModel.refreshMyRequests()

        val id = viewModel.focusedRequestId.value ?: return@LaunchedEffect
        val seeded = partitionSettingsRequestSections(viewModel.myRequests.value)
        val activeIndex = seeded.active.indexOfFirst { it.request.id == id }
        val completedIndex = seeded.completed.indexOfFirst { it.request.id == id }

        suspend fun focusRow(
            list: List<SeerrRequestDisplay>,
            index: Int,
            listState: LazyListState
        ): Boolean {
            val targetId = list[index].request.id
            listState.scrollToItem(index)
            val fr = rowFocusRequesters.getOrPut(targetId) { FocusRequester() }
            return fr.requestFocusWhenAttached(maxFrames = 20)
        }

        val focused = when {
            activeIndex >= 0 -> focusRow(seeded.active, activeIndex, activeListState)
            completedIndex >= 0 -> {
                completedExpanded = true
                // Let the collapsed LazyColumn enter composition before attaching focus.
                repeat(2) { withFrameNanos { } }
                focusRow(seeded.completed, completedIndex, completedListState)
            }
            seeded.active.isNotEmpty() -> focusRow(seeded.active, 0, activeListState)
            seeded.completed.isNotEmpty() -> {
                completedExpanded = false
                completedHeaderFr.requestFocusWhenAttached(maxFrames = 20)
            }
            else -> {
                leftFocus.requestFocusWhenAttached()
                true
            }
        }
        if (!focused) {
            leftFocus.requestFocusWhenAttached()
        }
        viewModel.clearFocusedRequest()
    }

    val hasAnyRequest = sections.active.isNotEmpty() || sections.completed.isNotEmpty()

    // Empty states centre in the panel (no scrolling needed); the populated
    // panel owns its vertical scroll. Nothing here is focusable and [enterFr] is
    // deliberately left unattached, so Right from the rail cannot enter a panel
    // with no content to land on — focus stays put on the rail (#140).
    if (seerr.linkState != SeerrLinkState.Linked || !hasAnyRequest) {
        Box(
            modifier = modifier.fillMaxSize().onFocusChanged { onFocusChanged(it.hasFocus) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (seerr.linkState != SeerrLinkState.Linked) {
                    "Connect Seerr under Account to browse and track requests."
                } else {
                    "No requests found"
                },
                color = PicnicColors.OnDarkMuted
            )
        }
        return
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .onFocusChanged { onFocusChanged(it.hasFocus) },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            sections.active.isEmpty() -> Text(
                "No active requests",
                color = PicnicColors.OnDarkMuted,
                modifier = Modifier.padding(start = 20.dp)
            )
            else -> LazyColumn(
                state = activeListState,
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sections.active, key = { it.request.id }) { row ->
                    val rowFr = remember(row.request.id) {
                        rowFocusRequesters.getOrPut(row.request.id) { FocusRequester() }
                    }
                    val isFirst = row.request.id == sections.active.first().request.id
                    val isLast = row.request.id == sections.active.last().request.id
                    RequestRow(
                        row = row,
                        seerrBaseUrl = seerr.serverUrl,
                        cacheImages = seerr.cacheImages,
                        leftFocus = leftFocus,
                        focusRequester = rowFr,
                        enterFr = if (isFirst) enterFr else null,
                        blockUp = isFirst,
                        blockDown = isLast && sections.completed.isEmpty(),
                        onActivate = {
                            viewModel.rememberFocusedRequest(row.request.id)
                            onOpenSeerrDetail?.invoke(row.request)
                        },
                        onLongActivate = {
                            contextRequest = row
                        }
                    )
                }
            }
        }
        // Hide Completed section when empty — cleaner TV chrome than a 0-count header.
        if (sections.completed.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            ActionRow(
                label = "Completed requests (${sections.completed.size})",
                value = if (completedExpanded) "Hide" else "Show",
                leftFocus = leftFocus,
                focusRequester = completedHeaderFr,
                // With no active requests this header is the first focusable thing
                // in the panel, so it must own the rail's entry target — otherwise
                // Right from the rail lands on the non-focusable empty-state text (#140).
                enterFr = if (sections.active.isEmpty()) enterFr else null,
                blockUp = sections.active.isEmpty(),
                blockDown = !completedExpanded,
                onActivate = { completedExpanded = !completedExpanded }
            )
            if (completedExpanded) {
                LazyColumn(
                    state = completedListState,
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sections.completed, key = { it.request.id }) { row ->
                        val rowFr = remember(row.request.id) {
                            rowFocusRequesters.getOrPut(row.request.id) { FocusRequester() }
                        }
                        RequestRow(
                            row = row,
                            seerrBaseUrl = seerr.serverUrl,
                            cacheImages = seerr.cacheImages,
                            leftFocus = leftFocus,
                            focusRequester = rowFr,
                            enterFr = null,
                            blockUp = false,
                            blockDown = row.request.id == sections.completed.last().request.id,
                            onActivate = {
                                viewModel.rememberFocusedRequest(row.request.id)
                                onOpenSeerrDetail?.invoke(row.request)
                            },
                            onLongActivate = {
                                contextRequest = row
                            }
                        )
                    }
                }
            }
        }
    }

    contextRequest?.let { row ->
        RequestContextMenuDialog(
            request = row.request,
            canCancel = viewModel.canCancelRequest(row.request),
            onDismiss = { contextRequest = null },
            onGoTo = {
                contextRequest = null
                viewModel.rememberFocusedRequest(row.request.id)
                onOpenSeerrDetail?.invoke(row.request)
            },
            onCancel = {
                // Cancelling drops the row from the refreshed list, disposing the
                // focused node — without an explicit target focus falls back to the
                // rail. Re-home to the top of the section the row came from, and
                // only fall back to the rail when the panel has nothing left.
                val fromCompleted = sections.completed.any { it.request.id == row.request.id }
                contextRequest = null
                panelScope.launch {
                    viewModel.cancelRequest(row.request).join()
                    val after = partitionSettingsRequestSections(viewModel.myRequests.value)

                    suspend fun focusTopOf(
                        list: List<SeerrRequestDisplay>,
                        listState: LazyListState
                    ): Boolean {
                        listState.scrollToItem(0)
                        val fr = rowFocusRequesters.getOrPut(list.first().request.id) { FocusRequester() }
                        return fr.requestFocusWhenAttached(maxFrames = 20)
                    }

                    val restored = when {
                        fromCompleted && after.completed.isNotEmpty() -> {
                            completedExpanded = true
                            focusTopOf(after.completed, completedListState)
                        }
                        after.active.isNotEmpty() -> focusTopOf(after.active, activeListState)
                        after.completed.isNotEmpty() ->
                            completedHeaderFr.requestFocusWhenAttached(maxFrames = 20)
                        else -> false
                    }
                    if (!restored) leftFocus.requestFocusWhenAttached()
                }
            }
        )
    }
}

/** Settings row meta: `MOVIE • 2019` / `SERIES • 2022 • Seasons 1–5, 7–8`. */
internal fun requestRowMetaLine(row: SeerrRequestDisplay): String {
    val parts = mutableListOf(requestRowTypeLabel(row.request))
    row.yearLabel?.let { parts += it }
    if (requestRowTypeLabel(row.request) == "SERIES") {
        val seasonNumbers = row.request.seasons.mapNotNull { it.seasonNumber }
        formatRequestedSeasonsLabel(seasonNumbers)?.let { parts += it }
    }
    return parts.joinToString(" • ")
}

/** Settings row type chip: MOVIE / SERIES (tv → SERIES), else MEDIA. */
internal fun requestRowTypeLabel(request: SeerrMediaRequest): String = when (resolvedRequestMediaType(request)) {
    SeerrMediaType.MOVIE -> "MOVIE"
    SeerrMediaType.TV -> "SERIES"
    null -> "MEDIA"
}

internal fun resolvedRequestMediaType(request: SeerrMediaRequest): SeerrMediaType? {
    val raw = (request.mediaType ?: request.media?.mediaType)?.lowercase()
    return when (raw) {
        "movie" -> SeerrMediaType.MOVIE
        "tv" -> SeerrMediaType.TV
        else -> null
    }
}

internal fun requestContextMenuLabels(
    request: SeerrMediaRequest,
    canCancel: Boolean
): List<String> = buildList {
    add(
        when (resolvedRequestMediaType(request)) {
            SeerrMediaType.MOVIE -> "Go to Movie"
            SeerrMediaType.TV -> "Go to Series"
            null -> "Go to title"
        }
    )
    if (canCancel) add("Cancel request")
}

/**
 * Status chip for Settings request rows (#58).
 * Maps Seerr [SeerrMediaRequest.status] only — not mediaInfo.status.
 */
internal fun requestRowStatusLabel(request: SeerrMediaRequest): String = when (request.status) {
    SeerrRequestStatus.PENDING -> "Pending"
    SeerrRequestStatus.APPROVED -> "In Progress"
    SeerrRequestStatus.DECLINED -> "Declined"
    SeerrRequestStatus.FAILED -> "Failed"
    SeerrRequestStatus.COMPLETED -> "Available"
    else -> "Pending"
}

/** Status → chip colour: green = ready to watch, cyan = moving, amber = waiting, red = dead. */
private fun requestRowStatusColor(request: SeerrMediaRequest): Color = when (request.status) {
    SeerrRequestStatus.COMPLETED -> PicnicColors.Success
    SeerrRequestStatus.APPROVED -> PicnicColors.Cyan
    SeerrRequestStatus.DECLINED, SeerrRequestStatus.FAILED -> PicnicColors.Error
    else -> PicnicColors.Warning
}

@Composable
private fun RequestRow(
    row: SeerrRequestDisplay,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    leftFocus: FocusRequester,
    focusRequester: FocusRequester,
    enterFr: FocusRequester?,
    blockUp: Boolean,
    blockDown: Boolean,
    onActivate: () -> Unit,
    onLongActivate: () -> Unit
) {
    val request = row.request
    var focused by remember { mutableStateOf(false) }
    var centerPressed by remember { mutableStateOf(false) }
    var longPressHandled by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val posterUrl = remember(row.posterPath, seerrBaseUrl, cacheImages) {
        SeerrImages.poster(seerrBaseUrl, row.posterPath, cacheImages, size = "w185")
    }
    DisposableEffect(Unit) {
        onDispose { holdJob?.cancel() }
    }
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f)
            )
            .focusRequester(focusRequester)
            .then(if (enterFr != null) Modifier.focusRequester(enterFr) else Modifier)
            .focusProperties {
                left = leftFocus
                right = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            // Only Select opens the request — Right is a direction, not a second
            // Select, and must not push a detail page off the Settings screen (#138).
            .onKeyEvent { event ->
                val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (!isCenter) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (!centerPressed && !longPressHandled) {
                                    centerPressed = true
                                    holdJob?.cancel()
                                    holdJob = scope.launch {
                                        delay(RequestRowLongPressMs)
                                        if (centerPressed) {
                                            centerPressed = false
                                            longPressHandled = true
                                            onLongActivate()
                                        }
                                    }
                                }
                                true
                            }
                            KeyEventType.KeyUp -> {
                                holdJob?.cancel()
                                holdJob = null
                                val activate = centerPressed && !longPressHandled
                                centerPressed = false
                                longPressHandled = false
                                if (activate) onActivate()
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) {
                    holdJob?.cancel()
                    holdJob = null
                    centerPressed = false
                    longPressHandled = false
                }
            }
            .focusable(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RequestPosterThumb(posterUrl = posterUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleMedium,
                color = PicnicColors.OnDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                requestRowMetaLine(row),
                style = MaterialTheme.typography.labelMedium,
                color = PicnicColors.OnDarkMuted
            )
        }
        Text(
            requestRowStatusLabel(request),
            style = MaterialTheme.typography.titleMedium,
            color = requestRowStatusColor(request)
        )
    }
}

private val RequestContextMenuGlassFill = Color(0xEA181E24)
private const val RequestRowLongPressMs = 550L

@Composable
private fun RequestContextMenuDialog(
    request: SeerrMediaRequest,
    canCancel: Boolean,
    onDismiss: () -> Unit,
    onGoTo: () -> Unit,
    onCancel: () -> Unit
) {
    BackHandler { onDismiss() }
    var enabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000L)
        enabled = true
    }

    data class MenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

    val items = requestContextMenuLabels(request, canCancel).map { label ->
        when (label) {
            "Cancel request" -> MenuItem(label, Icons.Default.Delete) {
                onCancel()
            }
            else -> MenuItem(label, Icons.AutoMirrored.Filled.ArrowForward) {
                onGoTo()
            }
        }
    }
    val focusRequesters = remember(items.size) { List(items.size) { FocusRequester() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .widthIn(min = 320.dp, max = 380.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(RequestContextMenuGlassFill)
                .padding(24.dp)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp &&
                        event.nativeKeyEvent.keyCode in setOf(
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                        )
                    ) {
                        enabled = true
                    }
                    false
                }
        ) {
            items.forEachIndexed { index, item ->
                ListItem(
                    selected = false,
                    enabled = enabled,
                    onClick = item.onClick,
                    headlineContent = { Text(item.label, color = Color.White) },
                    leadingContent = {
                        Icon(item.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters[index])
                )
            }
        }

        LaunchedEffect(Unit) {
            focusRequesters.firstOrNull()?.requestFocusWhenAttached()
        }
    }
}

@Composable
private fun RequestPosterThumb(posterUrl: String?) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 60.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
