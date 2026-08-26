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
import montafra.beam.BeamFont
import montafra.beam.settingsName

data class ThemePrefs(val mode: String = "system", val customColor: Int? = null, val colorStyle: ColorStyle = ColorStyle.TonalSpot, val fontFamily: String = "default", val outlineOnlyCards: Boolean = false, val cardSpacing: CardSpacing = CardSpacing.Compact)

/**
 * The seed a fresh install themes itself with, and what "Custom" falls back to when it has no
 * colour of its own yet. Also `colorSwatches[5]` - the green swatch is this one, selected.
 */
internal val defaultSeedColor: Int = 0xFF43A047.toInt()

/** What the app themes itself with below API 31, where there is no wallpaper palette to read. */
internal val fallbackSeedColor: Int = 0xFF0080FF.toInt()

/** Every pref [ThemePrefs] is built from - a key missing here just won't recompose the UI. */
private val themeKeys = setOf("themeMode", "themeColorValue", "themeColorStyle", "fontFamily", "outlineOnlyCards", "cardSpacing")

@Composable
fun rememberThemePrefs(): State<ThemePrefs> {
    val context = LocalContext.current

    fun read(): ThemePrefs {
        val p = context.getSharedPreferences(settingsName, android.content.Context.MODE_PRIVATE)
        return ThemePrefs(
            mode = p.getString("themeMode", "system") ?: "system",
            customColor = p.getInt("themeColorValue", defaultSeedColor).takeIf { it != -1 },
            colorStyle = ColorStyle.forKey(p.getString("themeColorStyle", null)),
            fontFamily = p.getString("fontFamily", "default") ?: "default",
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

    val colorScheme = if (prefs.mode == "oled") {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1C1C1C),
            surfaceContainer = Color(0xFF141414),
            surfaceContainerLow = Color(0xFF0C0C0C),
            surfaceContainerLowest = Color.Black,
            surfaceContainerHigh = Color(0xFF1E1E1E),
            surfaceContainerHighest = Color(0xFF242424),
            // The full role mapping fills these two in now; left alone, surfaceBright would come
            // back a light grey and sit on the black as a visible band.
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF242424),
        )
    } else {
        baseScheme
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
