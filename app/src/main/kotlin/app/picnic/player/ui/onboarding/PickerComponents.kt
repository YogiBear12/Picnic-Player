@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package app.picnic.player.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.ui.ambient.CardFocusBorderWidth
import app.picnic.player.ui.ambient.rememberCardFocusAccent
import app.picnic.player.ui.ambient.rememberCardFocusGlow
import app.picnic.player.ui.common.rememberIdentityBrush
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

/** One entry in a picker row. [editable] tiles support long-press edit; the
 *  trailing "Add" tile is not editable. */
data class PickerEntry(
    val id: String,
    val label: String,
    val imageUrl: String? = null,
    val fallbackInitial: String? = null,
    val icon: ImageVector? = null,
    val editable: Boolean = true,
    val authError: String? = null
)

/** Outer slot so focus scale + glow are not clipped (matches browse card slots). */
private fun pickerTileSlotSize(tileSize: Int) = tileSize.dp * 1.1f + 2.dp + 12.dp

/**
 * Shared cinematic layout for the return-user pickers: the title
 * (plus optional subtitle) and tile row form one vertically centred block over
 * the ambient wash, with an optional bottom-anchored footer action. Keeping the
 * footer out of the centred block means both pickers' rows land at the same
 * height without spacer arithmetic.
 */
@Composable
fun PickerScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    footerVisible: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(modifier.fillMaxSize().padding(48.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Spacer(Modifier.height(20.dp))
                it()
            }
            Spacer(Modifier.height(56.dp))
            content()
        }
        footer?.let {
            // Fades out during row edit mode so the held tile's Remove chip
            // never collides with the footer action.
            AnimatedVisibility(
                visible = footerVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            ) {
                it()
            }
        }
    }
}

/**
 * Horizontal picker row with gate-owned edit mode: long-press an
 * editable tile to enter edit, then ←/→ reorder, ↓ highlights delete, Select on
 * the highlighted delete opens a confirm dialog, a short Select or Back exits
 * edit. Focus stays on the held tile across reorders (keyed by id).
 */
@Composable
fun EditablePickerRow(
    items: List<PickerEntry>,
    addTile: PickerEntry?,
    onActivate: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onForget: (String) -> Unit,
    confirmText: (PickerEntry) -> String,
    modifier: Modifier = Modifier,
    onEditingChanged: (Boolean) -> Unit = {}
) {
    var editId by remember { mutableStateOf<String?>(null) }
    var deleteHighlight by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<PickerEntry?>(null) }
    val editing = editId != null
    LaunchedEffect(editing) { onEditingChanged(editing) }

    val focusers = remember { mutableMapOf<String, FocusRequester>() }
    fun focuserFor(id: String) = focusers.getOrPut(id) { FocusRequester() }
    val firstFocusId = items.firstOrNull()?.id ?: addTile?.id

    // Cold-start entry (Startup → picker) leaves nothing focused until we request
    // the first tile; async load means tiles may appear a frame after compose.
    // Also re-anchors after edit-mode reorder on the held tile.
    LaunchedEffect(items, addTile?.id, editId) {
        val targetId = editId ?: firstFocusId ?: return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        runCatching { focuserFor(targetId).requestFocus() }
    }

    BackHandler(enabled = editing) {
        editId = null
        deleteHighlight = false
    }

    fun exitEdit() {
        editId = null
        deleteHighlight = false
    }

    fun move(direction: Int) {
        val ids = items.map { it.id }.toMutableList()
        val i = ids.indexOf(editId)
        val j = i + direction
        if (i < 0 || j < 0 || j >= ids.size) return
        ids.add(j, ids.removeAt(i))
        onReorder(ids)
    }

    Row(
        modifier = modifier
            .then(
                if (firstFocusId != null && !editing) {
                    Modifier
                        .focusGroup()
                        .focusProperties { enter = { focuserFor(firstFocusId) } }
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        items.forEach { entry ->
            androidx.compose.runtime.key(entry.id) {
                PickerTile(
                    entry = entry,
                    focusRequester = focuserFor(entry.id),
                    editingThis = editId == entry.id,
                    deleteHighlighted = editId == entry.id && deleteHighlight,
                    focusable = !editing || editId == entry.id,
                    onActivate = { if (!editing) onActivate(entry.id) },
                    onLongPress = {
                        if (entry.editable && !editing) {
                            editId = entry.id
                            deleteHighlight = false
                        }
                    },
                    onEditSelect = { if (deleteHighlight) confirm = entry else exitEdit() },
                    onEditKey = { ev ->
                        if (editId != entry.id || ev.type != KeyEventType.KeyDown) return@PickerTile false
                        when (ev.key) {
                            Key.DirectionLeft -> {
                                move(-1)
                                true
                            }
                            Key.DirectionRight -> {
                                move(1)
                                true
                            }
                            Key.DirectionDown -> {
                                deleteHighlight = true
                                true
                            }
                            Key.DirectionUp -> if (deleteHighlight) {
                                deleteHighlight = false
                                true
                            } else {
                                false
                            }
                            else -> false
                        }
                    }
                )
            }
        }
        addTile?.let { entry ->
            androidx.compose.runtime.key(entry.id) {
                PickerTile(
                    entry = entry,
                    focusRequester = focuserFor(entry.id),
                    editingThis = false,
                    deleteHighlighted = false,
                    focusable = !editing,
                    onActivate = { if (!editing) onActivate(entry.id) },
                    onLongPress = {},
                    onEditSelect = {},
                    onEditKey = { false }
                )
            }
        }
    }

    confirm?.let { entry ->
        ForgetConfirmDialog(
            message = confirmText(entry),
            onConfirm = {
                confirm = null
                onForget(entry.id)
                exitEdit()
            },
            onCancel = { confirm = null }
        )
    }
}

@Composable
private fun PickerTile(
    entry: PickerEntry,
    focusRequester: FocusRequester,
    editingThis: Boolean,
    deleteHighlighted: Boolean,
    focusable: Boolean,
    onActivate: () -> Unit,
    onLongPress: () -> Unit,
    onEditSelect: () -> Unit,
    onEditKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    size: Int = 140
) {
    val focusAccent = rememberCardFocusAccent(entry.imageUrl)
    val tileShape = CircleShape
    val supportsLongPress = entry.editable && !editingThis
    val hasAuthError = entry.authError != null
    val focusBorderColor = when {
        hasAuthError -> PicnicColors.Error
        else -> focusAccent.borderColor
    }
    val focusGlowColor = when {
        hasAuthError -> PicnicColors.Error
        else -> focusAccent.glowColor
    }
    var focused by remember { mutableStateOf(false) }
    val focusedGlow = rememberCardFocusGlow(focusGlowColor, focused)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(pickerTileSlotSize(size)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = { if (editingThis) onEditSelect() else onActivate() },
                onLongClick = if (supportsLongPress) onLongPress else null,
                enabled = focusable,
                shape = ClickableSurfaceDefaults.shape(tileShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = PicnicColors.SurfaceVariant,
                    focusedContainerColor = PicnicColors.SurfaceVariant
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(0.dp, Color.Transparent), shape = tileShape),
                    focusedBorder = Border(
                        BorderStroke(CardFocusBorderWidth, focusBorderColor),
                        shape = tileShape
                    )
                ),
                glow = ClickableSurfaceDefaults.glow(focusedGlow = focusedGlow),
                modifier = Modifier
                    .size(size.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .onKeyEvent { ev -> editingThis && onEditKey(ev) }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (entry.icon != null) {
                        Icon(entry.icon, contentDescription = entry.label, modifier = Modifier.size(48.dp))
                    } else {
                        // Identity gradient + initial sit under the avatar image:
                        // visible for image-less tiles, and the load-in underlay
                        // while an avatar fetch is in flight.
                        Box(Modifier.fillMaxSize().background(rememberIdentityBrush(entry.label)))
                        // A tile with an avatar URL is expected to paint it (disk cache
                        // survives restarts), so the load gap shows the bare gradient —
                        // no initial flashing under the incoming image, and transparent
                        // avatars (PNG) sit on the gradient, not on a letter. The
                        // initial appears instantly for image-less tiles and as the
                        // fallback when a load fails.
                        var avatarFailed by remember(entry.id) { mutableStateOf(false) }
                        if (entry.imageUrl == null || avatarFailed) {
                            Text(
                                (entry.fallbackInitial ?: entry.label.firstOrNull()?.toString().orEmpty()).uppercase(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (entry.imageUrl != null) {
                            val context = LocalContext.current
                            AsyncImage(
                                model = remember(entry.imageUrl) {
                                    ImageRequest.Builder(context)
                                        .data(entry.imageUrl)
                                        // Stable per-identity cache key: when a tag refresh
                                        // changes the URL, the previous avatar stays up as
                                        // the placeholder and the new one crossfades in —
                                        // no blank flash on revisit.
                                        .memoryCacheKey(PICKER_AVATAR_KEY_PREFIX + entry.id)
                                        .placeholderMemoryCacheKey(PICKER_AVATAR_KEY_PREFIX + entry.id)
                                        .crossfade(true)
                                        .build()
                                },
                                contentDescription = entry.label,
                                contentScale = ContentScale.Crop,
                                onState = { avatarFailed = it is AsyncImagePainter.State.Error },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    if (hasAuthError) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = entry.authError,
                            tint = PicnicColors.Error,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(28.dp)
                        )
                    }
                }
            }
        }

        // Reorder arrows flank the name while editing.
        if (editingThis) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    entry.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = PicnicColors.OnDark,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                Text("›", color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            // Muted at rest, bright under focus — the label answers "which tile
            // am I on" without competing with the avatars.
            val labelColor by animateColorAsState(
                targetValue = if (focused) PicnicColors.OnDark else PicnicColors.OnDarkMuted,
                label = "pickerTileLabel"
            )
            Text(
                entry.label,
                style = MaterialTheme.typography.titleMedium,
                color = labelColor
            )
        }

        // Delete affordance below the held tile (red when highlighted via ↓).
        if (editingThis) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (deleteHighlighted) PicnicColors.Error else Color.Transparent)
                    .then(
                        if (deleteHighlighted) {
                            Modifier.border(2.dp, Color.White, RoundedCornerShape(20.dp))
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                Text("Remove", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Spacer(Modifier.height(28.dp))
        }
    }
}

private const val PICKER_AVATAR_KEY_PREFIX = "picker-avatar/"

@Composable
private fun ForgetConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        androidx.tv.material3.Surface(
            shape = MaterialTheme.shapes.medium,
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = PicnicColors.Surface,
                contentColor = Color.White
            ),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(message, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = onConfirm) { Text("Remove") }
                }
            }
        }
    }
}
