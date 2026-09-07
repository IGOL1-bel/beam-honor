package montafra.beam.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import montafra.beam.R
import montafra.beam.settingsName
import montafra.beam.ui.theme.BeamCard
import montafra.beam.ui.theme.ColorStyle
import montafra.beam.ui.theme.LocalCardSpacing
import montafra.beam.ui.theme.applyCustomSeed
import montafra.beam.ui.theme.cardShapeSingle
import montafra.beam.ui.theme.customSeed
import montafra.beam.ui.theme.hct.Hct
import montafra.beam.ui.theme.stylePreview
import kotlin.math.roundToInt

/**
 * The full seed picker, on its own page because a dragging finger has nowhere to go inside a
 * scrolling settings list.
 *
 * Three sliders, one per HCT axis, so every sRGB colour is reachable without touching the hex
 * field. The palette generator regenerates its own tone ladder from the seed, so brightness moves
 * the theme less than the other two axes - but the seed itself is user-visible (the swatch, the
 * hex, and the preset grid in SettingsScreen holds the seed's tone fixed), so it gets a control
 * like the others.
 */
@Composable
fun ColorPickerScreen(navController: BeamNavController) {
    val context = LocalContext.current
    val haptic = LocalTapHaptics.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    val initial = remember { prefs.customSeed() }
    val style = remember { ColorStyle.forKey(prefs.getString("themeColorStyle", null)) }
    val initialHct = remember { Hct.fromInt(initial) }

    // Saveable, because the sliders only reach prefs on release: without it a rotation mid-drag
    // would snap the colour back to whatever seed was last written.
    var hue by rememberSaveable { mutableFloatStateOf(initialHct.hue.toFloat()) }
    var chroma by rememberSaveable { mutableFloatStateOf(initialHct.chroma.toFloat().coerceAtMost(MaxChroma)) }
    var tone by rememberSaveable { mutableFloatStateOf(initialHct.tone.toFloat()) }
    var hexText by rememberSaveable { mutableStateOf(hexOf(initial)) }

    // What the sliders currently name, for the swatch and the style previews below. Composition
    // only - see settle().
    val argb = Hct.from(hue.toDouble(), chroma.toDouble(), tone.toDouble()).argb

    // Written on release, not per frame: a commit() here recomposes the whole app synchronously,
    // which is the same reason the alarm sliders save in onValueChangeFinished.
    //
    // The seed is solved from the sliders here, at call time, and deliberately NOT read off the
    // `argb` above, however duplicated that looks. This runs from a pointer callback, outside
    // composition, so a value captured out of the enclosing composable is whatever the last
    // completed composition computed - and the sliders sit in a LazyColumn item that recomposes on
    // its own, so that lags a whole interaction behind the finger. saveAlarms() in
    // AlarmsSettingsScreen reads its state delegates live for the same reason.
    fun settle() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val seed = Hct.from(hue.toDouble(), chroma.toDouble(), tone.toDouble()).argb
        hexText = hexOf(seed)
        prefs.applyCustomSeed(seed)
    }

    SettingsScaffold(
        title = stringResource(R.string.themeColorPicker),
        onBack = { navController.popBackStack() },
    ) {
        item {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            // Four more gamut searches, and only the seed and the light/dark mode move them.
            val preview = remember(argb, style, isDark) { stylePreview(argb, style, isDark) }
            BeamCard(modifier = Modifier.fillMaxWidth(), shape = cardShapeSingle()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(argb)),
                    )
                    Spacer(Modifier.height(12.dp))
                    // What the seed becomes once the chosen colour style has had it - the seed on
                    // its own says very little about the palette it produces.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(preview.primary, preview.secondary, preview.tertiary, preview.surface)
                            .forEach { swatch ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(swatch),
                                )
                            }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SubLabel(stringResource(R.string.colorHue))
                Text(
                    // 360 sanitizes to 0 in Hct, so the readout wraps with it rather than showing
                    // 360 degrees over a red swatch.
                    text = "${hue.roundToInt() % 360}°",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = hue,
                onValueChange = { hue = it },
                onValueChangeFinished = { settle() },
                valueRange = 0f..360f,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(LocalCardSpacing.current.group))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SubLabel(stringResource(R.string.colorSaturation))
                Text(
                    text = "${(chroma / MaxChroma * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = chroma,
                onValueChange = { chroma = it },
                onValueChangeFinished = { settle() },
                valueRange = 0f..MaxChroma,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(LocalCardSpacing.current.group))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SubLabel(stringResource(R.string.colorBrightness))
                Text(
                    text = "${tone.roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = tone,
                onValueChange = { tone = it },
                onValueChangeFinished = { settle() },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = hexText,
                onValueChange = { raw ->
                    val digits = raw
                        .removePrefix("#")
                        .filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                        .take(6)
                        .uppercase()
                    hexText = "#" + digits
                    // Six digits is the only length that names a colour; anything shorter is a
                    // half-typed one, so leave the theme where it is until it's complete.
                    if (digits.length == 6) {
                        val parsed = 0xFF000000.toInt() or digits.toInt(16)
                        val hct = Hct.fromInt(parsed)
                        hue = hct.hue.toFloat()
                        chroma = hct.chroma.toFloat().coerceAtMost(MaxChroma)
                        tone = hct.tone.toFloat()
                        prefs.applyCustomSeed(parsed)
                    }
                },
                label = { Text(stringResource(R.string.colorHex)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * Where the saturation track tops out. sRGB's most chromatic colour sits near 113 (red), so this
 * covers the whole gamut; Hct clamps chroma to what the gamut holds at a given hue and tone, so
 * the top of the track is deliberately inert for many hue/brightness combinations.
 */
private const val MaxChroma = 120f

private fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

