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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.ui.ambient.CardFocusBorderWidth
import app.picnic.player.ui.ambient.rememberCardFocusAccent
import app.picnic.player.ui.ambient.rememberCardFocusGlow
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.ArtworkImage
import app.picnic.player.ui.common.DialogCornerRadius
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.common.PicnicDialog
import app.picnic.player.ui.common.panelSurface
import app.picnic.player.ui.common.rememberIdentityBrush
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors

data class PickerEntry(
    val id: String,
    val label: String,
    val imageUrl: String? = null,
    val fallbackInitial: String? = null,
    val icon: ImageVector? = null,
    val editable: Boolean = true,
    val errorText: String? = null
)

private fun pickerTileSlotSize(tileSize: Int) = tileSize.dp * 1.1f + 2.dp + 12.dp

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
    var editExitAnchorId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(items, addTile?.id, editId) {
        val anchorId = editExitAnchorId?.takeIf { id -> items.any { it.id == id } }
        val targetId = editId ?: anchorId ?: firstFocusId ?: return@LaunchedEffect
        editExitAnchorId = null
        repeat(2) { withFrameNanos { } }
        runCatching { focuserFor(targetId).requestFocus() }
    }

    fun exitEdit() {
        editExitAnchorId = editId
        editId = null
        deleteHighlight = false
    }

    BackHandler(enabled = editing) { exitEdit() }

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
                if (firstFocusId != null) {
                    Modifier
                        .focusRestorer { focuserFor(firstFocusId) }
                        .focusGroup()
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
    val hasError = entry.errorText != null
    val focusBorderColor = when {
        hasError -> PicnicColors.Error
        else -> focusAccent.borderColor
    }
    val focusGlowColor = when {
        hasError -> PicnicColors.Error
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
                        Box(Modifier.fillMaxSize().background(rememberIdentityBrush(entry.label)))
                        var avatarFailed by remember(entry.id) { mutableStateOf(false) }
                        if (entry.imageUrl == null || avatarFailed) {
                            Text(
                                (entry.fallbackInitial ?: entry.label.firstOrNull()?.toString().orEmpty()).uppercase(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (entry.imageUrl != null) {
                            ArtworkImage(
                                url = entry.imageUrl,
                                contentDescription = entry.label,
                                stableCacheKey = PICKER_AVATAR_KEY_PREFIX + entry.id,
                                crossfade = true,
                                label = "avatar='${entry.label}'",
                                onSettled = { failed -> avatarFailed = failed },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    if (hasError) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = entry.errorText,
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

private val ForgetContentInset = 24.dp

private const val PICKER_AVATAR_KEY_PREFIX = "picker-avatar/"

@Composable
private fun ForgetConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }

    PicnicDialog(onDismiss = onCancel) {
        Column(
            modifier = Modifier
                .panelSurface(width = PanelWidth.Form, corner = DialogCornerRadius)
                .padding(horizontal = ForgetContentInset)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PicnicColors.OnDark
            )
            Spacer(Modifier.height(8.dp))
            ActionButton(
                label = "Remove",
                onActivate = onConfirm,
                modifier = Modifier.fillMaxWidth()
            )
            ActionButton(
                label = "Cancel",
                onActivate = onCancel,
                focusRequester = cancelFocus,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    LaunchedEffect(Unit) { cancelFocus.requestFocusWhenAttached(maxFrames = 20) }
}
