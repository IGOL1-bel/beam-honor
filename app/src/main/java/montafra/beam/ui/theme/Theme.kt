package montafra.beam.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import montafra.beam.BeamFont
import montafra.beam.defaultFontKey
import montafra.beam.settingsName
import montafra.beam.ui.theme.hct.Hct

/**
 * The colour re-landed at [tone] with its hue and chroma kept. OLED mode uses it to darken the
 * surface roles without discarding the tint the scheme's neutral palette carries.
 */
private fun Color.withTone(tone: Double): Color {
    val hct = Hct.fromInt(toArgb())
    return Color(Hct.from(hct.hue, hct.chroma, tone).argb)
}

data class ThemePrefs(val mode: String = "system", val customColor: Int? = null, val colorStyle: ColorStyle = ColorStyle.TonalSpot, val fontFamily: String = defaultFontKey, val outlineOnlyCards: Boolean = false, val cardSpacing: CardSpacing = CardSpacing.Compact)

/**
 * The seed a fresh install themes itself with, and what "Custom" falls back to when it has no
 * colour of its own yet. Also the anchor of `colorSwatches` - the whole preset grid is this
 * colour's hue rotated around it.
 */
internal val defaultSeedColor: Int = 0xFF00AEB3.toInt()

/**
 * What the app themes itself with below API 31, where there is no wallpaper palette to read.
 * Beam's own seed: with no wallpaper to answer to, there is nothing else it should be.
 */
internal val fallbackSeedColor: Int = defaultSeedColor

/** Every pref [ThemePrefs] is built from - a key missing here just won't recompose the UI. */
private val themeKeys = setOf("themeMode", "themeColorValue", "themeColorAuto", "themeColorStyle", "fontFamily", "outlineOnlyCards", "cardSpacing")

/**
 * Whether the palette comes from the wallpaper rather than a seed of the user's own.
 *
 * A flag of its own rather than a sentinel inside themeColorValue, because every opaque ARGB is a
 * colour someone might pick - the old sentinel, -1, is pure white, so choosing white silently
 * turned auto mode on. Keeping the two apart also means auto no longer overwrites the seed, so
 * switching back to custom returns the colour that was there.
 *
 * Prefs written before this key existed still carry the sentinel, so fall back to reading it.
 */
internal fun SharedPreferences.themeColorIsAuto(): Boolean =
    getBoolean("themeColorAuto", getInt("themeColorValue", defaultSeedColor) == -1)

/** The seed "Custom" wears, whether or not auto is currently on top of it. */
internal fun SharedPreferences.customSeed(): Int {
    val value = getInt("themeColorValue", defaultSeedColor)
    // -1 only means "auto" on prefs written before themeColorAuto existed - there it is a seed
    // that was destroyed, with nothing left to recover. Once the flag is present it is just white.
    return if (value == -1 && !contains("themeColorAuto")) defaultSeedColor else value
}

/**
 * Applies a custom seed and leaves auto, in one edit so no reader can catch the flag and the seed
 * disagreeing. Not for the sake of one rebuild: OnSharedPreferenceChangeListener fires per changed
 * key, so [rememberThemePrefs] re-reads twice either way - harmlessly, since the second read
 * compares equal. Every write of a seed goes through here: it is what stamps themeColorAuto, and
 * [customSeed] needs that key present before it will read -1 as the colour white instead of the
 * legacy sentinel.
 */
internal fun SharedPreferences.applyCustomSeed(color: Int) {
    edit().putInt("themeColorValue", color).putBoolean("themeColorAuto", false).commit()
}

@Composable
fun rememberThemePrefs(): State<ThemePrefs> {
    val context = LocalContext.current

    fun read(): ThemePrefs {
        val p = context.getSharedPreferences(settingsName, android.content.Context.MODE_PRIVATE)
        return ThemePrefs(
            mode = p.getString("themeMode", "system") ?: "system",
            customColor = if (p.themeColorIsAuto()) null else p.customSeed(),
            colorStyle = ColorStyle.forKey(p.getString("themeColorStyle", null)),
            fontFamily = p.getString("fontFamily", defaultFontKey) ?: defaultFontKey,
            outlineOnlyCards = p.getBoolean("outlineOnlyCards", false),
            cardSpacing = CardSpacing.forKey(p.getString("cardSpacing", null)),
        )
    }

    val state = remember { mutableStateOf(read()) }

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences(settingsName, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in themeKeys) state.value = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return state
}

@Composable
fun BeamTheme(prefs: ThemePrefs, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDark = when (prefs.mode) {
        "light" -> false
        "dark", "oled" -> true
        else -> isSystemInDarkTheme()
    }

    // Building a scheme walks a gamut search per tone, so keep it off every recomposition.
    val baseScheme = remember(prefs.customColor, prefs.colorStyle, useDark, context) {
        when {
            prefs.customColor != null -> dynamicColorScheme(prefs.customColor, prefs.colorStyle, useDark)
            Build.VERSION.SDK_INT >= 31 ->
                if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // The pre-31 fallback is ours to generate, so the colour style applies here too -
            // only the wallpaper palette above is the system's to decide.
            else -> dynamicColorScheme(fallbackSeedColor, prefs.colorStyle, useDark)
        }
    }

    // OLED pulls the surface stack down to (near-)black by rewriting tones only: hue and chroma
    // come from the scheme's own neutrals, so cards keep the seed's tint instead of collapsing
    // to one neutral grey for every theme. The tones match the greys this used to hard-code
    // (L* ≈ 4/7/10/13). Remembered because each re-landed tone runs a small gamut solve.
    val colorScheme = remember(baseScheme, prefs.mode) {
        if (prefs.mode == "oled") {
            baseScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = baseScheme.surfaceVariant.withTone(10.0),
                surfaceContainer = baseScheme.surfaceContainer.withTone(7.0),
                surfaceContainerLow = baseScheme.surfaceContainerLow.withTone(4.0),
                surfaceContainerLowest = Color.Black,
                surfaceContainerHigh = baseScheme.surfaceContainerHigh.withTone(10.0),
                surfaceContainerHighest = baseScheme.surfaceContainerHighest.withTone(13.0),
                // The full role mapping fills these two in now; left alone, surfaceBright would
                // come back a light grey and sit on the black as a visible band.
                surfaceDim = Color.Black,
                surfaceBright = baseScheme.surfaceBright.withTone(13.0),
            )
        } else {
            baseScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Both bars follow the app's own theme, not the system night config that
            // enableEdgeToEdge() reads - otherwise a light app on a dark-mode device gets a white
            // gesture pill on a white background, now that the bar has no scrim to hide it.
            val lightBars = colorScheme.surface.luminance() > 0.5f
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    CompositionLocalProvider(
        LocalOutlineOnlyCards provides prefs.outlineOnlyCards,
        LocalCardSpacing provides prefs.cardSpacing,
        LocalBeamFont provides BeamFont.forKey(prefs.fontFamily),
    ) {
        // MaterialExpressiveTheme would go here, but the material3 on the classpath still has it
        // internal and ships no MotionScheme, so there is nothing public to call yet. Everything
        // expressive the app does today it does itself.
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typographyForFont(prefs.fontFamily),
            content = content,
        )
    }
}
