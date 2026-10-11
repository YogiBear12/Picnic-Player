package app.picnic.player.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import app.picnic.player.ui.browse.CardCornerRadius
import app.picnic.player.ui.common.ArtworkImage
import app.picnic.player.ui.detail.CardOpenPhase
import kotlin.math.max
import kotlin.math.roundToInt

private const val CARD_CROSSFADE_PROGRESS = 0.15f

internal data class CardOrigin(val itemId: String, val bounds: Rect, val backdropUrl: String?, val cardImageUrl: String?)

internal val LocalCardOrigin = staticCompositionLocalOf<(CardOrigin) -> Unit> { {} }

/**
 * Binds a card click to the Details entry it pushes, so only that entry grows from the card and
 * shrinks back into it. Plain fields: written while the back stack is composed, never observed.
 */
internal class CardOrigins {
    private var pending: CardOrigin? = null
    private var top: NavKey? = null
    private val byInstance = HashMap<String, CardOrigin>()
    private val shown = HashSet<String>()

    fun report(origin: CardOrigin) {
        pending = origin
    }

    fun sync(backStack: List<NavKey>) {
        val newTop = backStack.lastOrNull()
        if (newTop != top) {
            val claimed = pending?.takeIf { newTop is DetailKey && it.itemId == newTop.itemId }
            if (claimed != null && newTop is DetailKey) byInstance.putIfAbsent(newTop.instanceId, claimed)
            pending = null
            top = newTop
        }
        val live = backStack.mapNotNullTo(HashSet()) { (it as? DetailKey)?.instanceId }
        byInstance.keys.retainAll(live)
        shown.retainAll(live)
    }

    fun originOf(key: DetailKey): CardOrigin? = byInstance[key.instanceId]

    fun wasShown(key: DetailKey): Boolean = key.instanceId in shown

    fun markShown(key: DetailKey) {
        shown.add(key.instanceId)
    }

    /** Card-origin entries replace the host's fades; every other entry keeps them. */
    fun metadataFor(key: DetailKey): Map<String, Any> {
        if (originOf(key) == null) return emptyMap()
        val cardExit = EnterTransition.None togetherWith ExitTransition.None
        return NavDisplay.transitionSpec {
            (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished).apply {
                targetContentZIndex = 1f
            }
        } + NavDisplay.popTransitionSpec { cardExit } + NavDisplay.predictivePopTransitionSpec { cardExit }
    }
}

private enum class CardMotion { Grow, Shrink, None }

@Composable
internal fun CardMotionEntry(
    key: DetailKey,
    origins: CardOrigins,
    backStack: List<NavKey>,
    content: @Composable (seedBackdropUrl: String?, cardOpen: CardOpenPhase?) -> Unit
) {
    val origin = remember(key) { origins.originOf(key) }
    var grows by remember(key) { mutableStateOf(origin != null && !origins.wasShown(key)) }
    SideEffect { origins.markShown(key) }
    val popped = key !in backStack
    val transition = LocalNavAnimatedContentScope.current.transition
    LaunchedEffect(transition.targetState, popped) {
        if (transition.targetState != EnterExitState.Visible && !popped) grows = false
    }
    val expansion = transition.animateFloat(
        transitionSpec = { MotionScheme.standard().slowSpatialSpec() },
        label = "detailsExpansion"
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val cardOpen = when {
        !grows -> null
        transition.currentState == EnterExitState.Visible -> CardOpenPhase.Settled
        else -> CardOpenPhase.Growing
    }
    val motion = {
        when {
            transition.targetState == EnterExitState.Visible -> if (grows) CardMotion.Grow else CardMotion.None
            popped && origin != null -> CardMotion.Shrink
            else -> CardMotion.None
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (origin == null) return@graphicsLayer
                    when (motion()) {
                        CardMotion.Grow -> growFromCard(origin.bounds, expansion.value)
                        CardMotion.Shrink -> shrinkToCard(origin.bounds, expansion.value)
                        CardMotion.None -> Unit
                    }
                }
        ) {
            content(origin?.backdropUrl, cardOpen)
        }
        origin?.cardImageUrl?.let { url ->
            CardImage(url, origin.bounds, progress = { expansion.value }, visible = { motion() != CardMotion.None })
        }
    }
}

@Composable
private fun CardImage(url: String, card: Rect, progress: () -> Float, visible: () -> Boolean) {
    ArtworkImage(
        url = url,
        contentDescription = null,
        modifier = Modifier
            .layout { measurable, constraints ->
                val full = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                val window = lerp(card, full, progress())
                val placeable = measurable.measure(Constraints.fixed(window.width.roundToInt(), window.height.roundToInt()))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(window.left.roundToInt(), window.top.roundToInt())
                }
            }
            .graphicsLayer {
                alpha = if (visible()) 1f - (progress() / CARD_CROSSFADE_PROGRESS).coerceIn(0f, 1f) else 0f
                shape = RoundedCornerShape(CardCornerRadius)
                clip = true
            }
    )
}

private fun GraphicsLayerScope.growFromCard(card: Rect, progress: Float) {
    val window = lerp(card, Rect(Offset.Zero, size), progress)
    val scale = max(window.width / size.width, window.height / size.height)
    val left = window.center.x - scale * size.width / 2f
    val top = window.center.y - scale * size.height / 2f
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = scale
    scaleY = scale
    translationX = left
    translationY = top
    shape = WindowShape(
        Rect(
            offset = Offset((window.left - left) / scale, (window.top - top) / scale),
            size = Size(window.width / scale, window.height / scale)
        ),
        cornerRadius = if (progress < 1f) CardCornerRadius.toPx() / scale else 0f
    )
    clip = true
}

private fun GraphicsLayerScope.shrinkToCard(card: Rect, progress: Float) {
    val remaining = 1f - progress
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = 1f + (card.width / size.width - 1f) * remaining
    scaleY = 1f + (card.height / size.height - 1f) * remaining
    translationX = card.left * remaining
    translationY = card.top * remaining
    shape = WindowShape(
        Rect(Offset.Zero, size),
        cornerRadius = if (progress < 1f) CardCornerRadius.toPx() / max(scaleX, scaleY) else 0f
    )
    clip = true
}

private class WindowShape(private val rect: Rect, private val cornerRadius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Rounded(RoundRect(rect, CornerRadius(cornerRadius)))
}
