package montafra.beam.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import montafra.beam.BeamFont
import montafra.beam.mapHeroWeight
import montafra.beam.typeface

private val m3 = Typography()

// Only the two roles Beam restyles; everything else keeps the Material3 defaults, which is
// why these copy() the baseline rather than building a TextStyle from scratch — a fresh
// TextStyle would drop the baseline letterSpacing, lineHeightStyle and explicit fontWeight.
val WattzTypography = m3.copy(
    displayLarge = m3.displayLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 68.sp,
    ),
    bodyLarge = m3.bodyLarge.copy(
        fontSize = 20.sp,
        lineHeight = 24.sp,
    ),
)

/** The selected font, so weight overrides outside the typography roles can re-pin correctly. */
val LocalBeamFont = staticCompositionLocalOf<BeamFont?> { null }

// One entry per (font, weight) actually drawn — a handful for the typography roles plus, at
// most, the hero morph's quantised ladder. Keyed by value so a weight sweep reuses instances
// across frames instead of rebuilding a family, and re-resolving a typeface, on every one.
private val familyCache = HashMap<Int, FontFamily>()

/**
 * A family holding exactly one typeface, already pinned to [weight] on the font's `wght` axis.
 *
 * The bundled fonts are single variable files whose default instance is *not* Regular — Space
 * Grotesk's is Light 300 — so anything that draws them without applying `wght` comes out
 * visibly under-weighted. Declaring a multi-entry family and letting Compose match a weight
 * does not reliably land the axis on the rendered typeface, so the weight is baked into the
 * typeface here instead, through the same [BeamFont.typeface] path the notification icon and
 * the widget already use. Callers must pin the TextStyle's fontWeight to the same value.
 */
private fun familyFor(context: Context, font: BeamFont, weight: Int): FontFamily? {
    val w = font.clampWeight(weight)
    val key = (font.ordinal shl 16) or w
    synchronized(familyCache) { familyCache[key] }?.let { return it }
    // Loaded outside the lock: ResourcesCompat.getFont is disk I/O on the first call per file.
    val family = font.typeface(context.applicationContext, w)
        ?.let { FontFamily(Typeface(it)) }
        ?: return null
    return synchronized(familyCache) { familyCache.getOrPut(key) { family } }
}

fun fontFamilyFor(context: Context, key: String, weight: Int = 400): FontFamily? =
    BeamFont.forKey(key)?.let { familyFor(context, it, weight) }

/**
 * The system "Bold text" accessibility bump, which Compose normally folds into font resolution
 * itself. A single-typeface family ignores the requested weight, so it has to be applied to the
 * weight *before* the typeface is built or the setting would silently stop working.
 */
@Composable
private fun fontWeightAdjustment(): Int =
    if (Build.VERSION.SDK_INT >= 31) {
        LocalConfiguration.current.fontWeightAdjustment
            .takeIf { it != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED } ?: 0
    } else {
        0
    }

private fun TextStyle.pinned(
    context: Context,
    font: BeamFont,
    adjust: Int,
    override: FontWeight? = null,
): TextStyle {
    val requested = (override ?: fontWeight ?: FontWeight.Normal).weight + adjust
    val w = font.clampWeight(requested)
    val family = familyFor(context, font, w) ?: return this
    // The weight is baked into the typeface, so nothing downstream may re-derive it: synthetic
    // bolding on top of a real 700 face would both double up and drop the wght axis.
    return copy(fontFamily = family, fontWeight = FontWeight(w), fontSynthesis = FontSynthesis.None)
}

private fun Typography.pinnedTo(context: Context, font: BeamFont, adjust: Int): Typography = copy(
    displayLarge = displayLarge.pinned(context, font, adjust),
    displayMedium = displayMedium.pinned(context, font, adjust),
    displaySmall = displaySmall.pinned(context, font, adjust),
    headlineLarge = headlineLarge.pinned(context, font, adjust),
    headlineMedium = headlineMedium.pinned(context, font, adjust),
    headlineSmall = headlineSmall.pinned(context, font, adjust),
    titleLarge = titleLarge.pinned(context, font, adjust),
    titleMedium = titleMedium.pinned(context, font, adjust),
    titleSmall = titleSmall.pinned(context, font, adjust),
    bodyLarge = bodyLarge.pinned(context, font, adjust),
    bodyMedium = bodyMedium.pinned(context, font, adjust),
    bodySmall = bodySmall.pinned(context, font, adjust),
    labelLarge = labelLarge.pinned(context, font, adjust),
    labelMedium = labelMedium.pinned(context, font, adjust),
    labelSmall = labelSmall.pinned(context, font, adjust),
)

/**
 * Re-pins a themed style onto a different weight. A call site that only sets `fontWeight` would
 * be ignored, because the style's family is a single typeface baked at the role's own weight.
 */
@Composable
fun TextStyle.beamWeight(weight: FontWeight): TextStyle {
    val font = LocalBeamFont.current ?: return copy(fontWeight = weight)
    val context = LocalContext.current
    val adjust = fontWeightAdjustment()
    return remember(this, font, weight, adjust) { pinned(context, font, adjust, weight) }
}

// Quantised so a press produces ~20 distinct typefaces instead of ~500. Compose caches
// typefaces in a small LRU, and an unquantised sweep thrashes it badly enough to rebuild
// faces mid-animation; 25-point steps are indistinguishable by eye. It also bounds how many
// entries the hero morph can add to familyCache.
private const val heroWeightStep = 25

/**
 * The weight to draw the hero number at for a raw animation value, remapped onto the
 * selected font's axis. Callers must feed the result to both the family and the TextStyle
 * so the requested weight and the declared entry always agree.
 */
fun heroWeightFor(key: String, authored: Int): Int {
    val stepped = (authored.toFloat() / heroWeightStep).roundToInt() * heroWeightStep
    return BeamFont.forKey(key)?.mapHeroWeight(stepped)
        ?: stepped.coerceIn(1, 1000)
}

// The hero number's tap effect morphs weight frame by frame; "default" falls back to the
// system font, whose weight animates best-effort.
fun heroNumberFontFamily(context: Context, key: String, weight: Int): FontFamily {
    val font = BeamFont.forKey(key) ?: return FontFamily.Default
    return familyFor(context, font, weight) ?: FontFamily.Default
}

@Composable
fun typographyForFont(key: String): Typography {
    val font = BeamFont.forKey(key) ?: return WattzTypography
    val context = LocalContext.current
    val adjust = fontWeightAdjustment()
    // MaterialTheme publishes typography through a static CompositionLocal, so the instance has
    // to stay identity-stable across recompositions or every text node re-resolves its typeface.
    return remember(font, adjust) { WattzTypography.pinnedTo(context, font, adjust) }
}
