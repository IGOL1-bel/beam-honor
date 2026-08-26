package montafra.beam.ui.theme

import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import montafra.beam.R

val LocalOutlineOnlyCards = staticCompositionLocalOf { false }

/**
 * How densely the app packs its cards, chosen by the "Card Spacing" theme setting: both the gaps
 * between cards and the corner radii, since a tight stack wants square seams and a loose one wants
 * round ones.
 *
 * Home keeps its own values throughout because it has always been tighter between cards and
 * rounder at the corners than the settings screens. The gap tiers are scaled so that [Medium]
 * matches the gaps that were hard-coded at every call site before this setting existed; the
 * corner tiers sit one step rounder than that throughout, so [Compact] - not [Medium] - is the
 * tier whose radii match the original look.
 */
enum class CardSpacing(
    val key: String,
    @StringRes val labelRes: Int,
    /** Between card groups on settings screens. */
    val group: Dp,
    /** Between cards stacked inside one group. */
    val stack: Dp,
    /** Between cards on the home screen. */
    val homeGroup: Dp,
    /** Between metric cards inside the home metric group. */
    val homeStack: Dp,
    /** Radius of the corners where two cards inside a group meet. */
    val inner: Dp,
    /** [inner] for the home metric group, which is rounder throughout. */
    val homeInner: Dp,
    /**
     * Added to a group's outer radius, from 0 on [Compact] up to 8 on [Roomy]. Call sites keep
     * their own outer radius (20 on settings, 16 on a few, 24 on home, 40 on the hero card) and
     * this shifts all of them together, so roomier spacing reads rounder without flattening the
     * differences between them.
     */
    val outerShift: Dp,
) {
    Compact(
        key = "compact", labelRes = R.string.cardSpacingCompact,
        group = 10.dp, stack = 2.dp, homeGroup = 8.dp, homeStack = 3.dp,
        inner = 4.dp, homeInner = 10.dp, outerShift = 0.dp,
    ),
    Medium(
        key = "medium", labelRes = R.string.cardSpacingMedium,
        group = 16.dp, stack = 4.dp, homeGroup = 12.dp, homeStack = 6.dp,
        inner = 8.dp, homeInner = 16.dp, outerShift = 4.dp,
    ),
    Roomy(
        key = "roomy", labelRes = R.string.cardSpacingRoomy,
        group = 24.dp, stack = 8.dp, homeGroup = 18.dp, homeStack = 12.dp,
        inner = 16.dp, homeInner = 24.dp, outerShift = 8.dp,
    );

    companion object {
        /** Anything unset or no longer recognised means the default density. */
        fun forKey(key: String?): CardSpacing = entries.firstOrNull { it.key == key } ?: Compact
    }
}

// Static, like LocalOutlineOnlyCards: the value is read from deep inside many subtrees and only
// changes on an explicit tap, so invalidating the whole tree beats tracking every read site.
val LocalCardSpacing = staticCompositionLocalOf { CardSpacing.Compact }

/**
 * The gap between two cards stacked inside one group. Prefer this over a bare [Spacer] so a card
 * gap stays greppable - the app is full of unrelated 4.dp spacers that must NOT scale with the
 * Card Spacing setting.
 */
@Composable
fun CardGap() = Spacer(Modifier.height(LocalCardSpacing.current.stack))

/** [CardGap] for the home screen's metric cards, which sit slightly further apart. */
@Composable
fun HomeCardGap() = Spacer(Modifier.height(LocalCardSpacing.current.homeStack))

/** A group's outer radius, shifted by the current Card Spacing and never negative. */
@Composable
private fun outer(radius: Dp): Dp =
    (radius + LocalCardSpacing.current.outerShift).coerceAtLeast(0.dp)

/**
 * Shapes for the app's stacked card groups: a group rounds its outside corners and squares off
 * the corners where its cards meet, so the stack reads as one block. Both radii follow the
 * Card Spacing setting - tighter spacing squares the seams, roomier spacing rounds everything.
 *
 * Pass [radius]/[seam] only to depart from the settings-screen default (the home metric group
 * is rounder: `radius = 24.dp, seam = LocalCardSpacing.current.homeInner`).
 */
@Composable
fun cardShapeSingle(radius: Dp = 20.dp): Shape = RoundedCornerShape(outer(radius))

/** Top card of a group: rounded above, squared into the card below. */
@Composable
fun cardShapeTop(radius: Dp = 20.dp, seam: Dp = LocalCardSpacing.current.inner): Shape =
    RoundedCornerShape(topStart = outer(radius), topEnd = outer(radius), bottomStart = seam, bottomEnd = seam)

/** A card with a neighbour on both sides. */
@Composable
fun cardShapeMiddle(seam: Dp = LocalCardSpacing.current.inner): Shape = RoundedCornerShape(seam)

/** Bottom card of a group: squared into the card above, rounded below. */
@Composable
fun cardShapeBottom(radius: Dp = 20.dp, seam: Dp = LocalCardSpacing.current.inner): Shape =
    RoundedCornerShape(topStart = seam, topEnd = seam, bottomStart = outer(radius), bottomEnd = outer(radius))

/**
 * The interaction source a [BeamCard] offers to whatever it wraps.
 *
 * A card can't see presses landing on its own content, and pairing every call site with its own
 * source by hand would be 30-odd chances to wire one up backwards. So the card publishes one and
 * any clickable inside picks it up - which also means only cards that actually contain something
 * tappable ever animate. Purely decorative ones never see a press and stay still.
 */
internal val LocalCardInteraction = compositionLocalOf<MutableInteractionSource?> { null }

/** The clickable's interaction source, wired to the enclosing [BeamCard] when there is one. */
@Composable
fun rememberCardInteraction(): MutableInteractionSource =
    LocalCardInteraction.current ?: remember { MutableInteractionSource() }

@Composable
fun BeamCard(
    modifier: Modifier = Modifier,
    shape: Shape = cardShapeSingle(),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    val outlineOnly = LocalOutlineOnlyCards.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The expressive press: the card shrinks a touch away from its neighbours in the stack and
    // springs back. Hand-tuned rather than taken from MotionScheme, which the material3 on the
    // classpath doesn't ship yet - the damping is just shy of critical so it settles without a
    // wobble, matching the fast spatial spec it stands in for.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "cardPress",
    )
    Card(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (outlineOnly) Color.Transparent else containerColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (outlineOnly) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        val columnScope = this
        CompositionLocalProvider(LocalCardInteraction provides interaction) {
            with(columnScope) { content() }
        }
    }
}
