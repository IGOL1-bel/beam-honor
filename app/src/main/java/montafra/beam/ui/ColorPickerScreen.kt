package montafra.beam.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import montafra.beam.R
import montafra.beam.settingsName
import montafra.beam.ui.theme.BeamCard
import montafra.beam.ui.theme.ColorStyle
import montafra.beam.ui.theme.LocalCardSpacing
import montafra.beam.ui.theme.cardShapeSingle
import montafra.beam.ui.theme.defaultSeedColor
import montafra.beam.ui.theme.hct.Hct
import montafra.beam.ui.theme.stylePreview

/**
 * The full seed picker, on its own page because a dragging finger has nowhere to go inside a
 * scrolling settings list.
 *
 * Two sliders rather than the usual saturation/brightness square, because those are exactly the
 * two axes that reach the theme. A tonal palette regenerates every tone from 0 to 100 by
 * construction, so a seed's own brightness only names a point on a ladder that gets rebuilt
 * anyway - a brightness control here would move the swatch and change nothing else. Hue and
 * chroma are the whole story.
 */
@Composable
fun ColorPickerScreen(navController: BeamNavController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    val initial = remember {
        prefs.getInt("themeColorValue", defaultSeedColor).takeIf { it != -1 } ?: defaultSeedColor
    }
    val style = remember { ColorStyle.forKey(prefs.getString("themeColorStyle", null)) }
    val initialHct = remember { Hct.fromInt(initial) }

    var hue by remember { mutableFloatStateOf(initialHct.hue.toFloat()) }
    var chroma by remember { mutableFloatStateOf(initialHct.chroma.toFloat().coerceAtMost(MaxChroma)) }
    // Carried along but never given a control: it keeps a hex the user typed round-tripping back
    // to itself instead of snapping to some canonical lightness.
    var tone by remember { mutableFloatStateOf(initialHct.tone.toFloat()) }
    var hexText by remember { mutableStateOf(hexOf(initial)) }

    val argb = Hct.from(hue.toDouble(), chroma.toDouble(), tone.toDouble()).argb

    // Written on release, not per frame: a commit() here recomposes the whole app synchronously,
    // which is the same reason the alarm sliders save in onValueChangeFinished.
    fun persist() {
        prefs.edit().putInt(
            "themeColorValue",
            Hct.from(hue.toDouble(), chroma.toDouble(), tone.toDouble()).argb,
        ).commit()
    }

    fun settle() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        hexText = hexOf(Hct.from(hue.toDouble(), chroma.toDouble(), tone.toDouble()).argb)
        persist()
    }

    SettingsScaffold(
        title = stringResource(R.string.themeColorPicker),
        onBack = { navController.popBackStack() },
    ) {
        item {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val preview = stylePreview(argb, style, isDark)
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
            // A full-strength rainbow rather than the ramp at the current chroma and tone: this
            // track picks a hue, and at zero saturation the honest version would be 37 identical
            // greys with nothing to aim at. Fixed stops also mean no gamut searches at all here.
            val hueStops = remember {
                List(37) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 10f, 1f, 1f))) }
            }
            SubLabel(stringResource(R.string.colorHue))
            Spacer(Modifier.height(8.dp))
            GradientSlider(
                colors = hueStops,
                fraction = hue / 360f,
                onChange = { hue = it * 360f },
                onChangeFinished = { settle() },
            )
            Spacer(Modifier.height(LocalCardSpacing.current.group))

            // Grey through to as much colour as this hue can hold. Rebuilt only when the hue or
            // tone moves, never on a drag along this track itself.
            val chromaStops = remember(hue, tone) {
                List(9) { Color(Hct.from(hue.toDouble(), it * MaxChroma / 8.0, tone.toDouble()).argb) }
            }
            SubLabel(stringResource(R.string.colorSaturation))
            Spacer(Modifier.height(8.dp))
            GradientSlider(
                colors = chromaStops,
                fraction = chroma / MaxChroma,
                onChange = { chroma = it * MaxChroma },
                onChangeFinished = { settle() },
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
                        prefs.edit().putInt("themeColorValue", parsed).commit()
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
 * Where the saturation track tops out. Past roughly this much chroma the palette generator stops
 * responding, so carrying the slider further would be a control that moves nothing.
 */
private const val MaxChroma = 72f

private fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/**
 * A colour ramp you drag a thumb along. Both the hue and the saturation track are this - they
 * differ only in the stops they're given and what they do with the fraction that comes back.
 */
@Composable
private fun GradientSlider(
    colors: List<Color>,
    fraction: Float,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    onChange((pos.x / size.width).coerceIn(0f, 1f))
                    onChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos -> onChange((pos.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = onChangeFinished,
                    onDragCancel = onChangeFinished,
                ) { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(colors))
        val half = 4.dp.toPx()
        val inset = 3.dp.toPx()
        val x = (fraction.coerceIn(0f, 1f) * size.width).coerceIn(half + inset, size.width - half - inset)
        // A dark bar under a light one, so the thumb survives both the pale end of a ramp and the
        // near-black one.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.45f),
            topLeft = Offset(x - half - 1.dp.toPx(), inset - 1.dp.toPx()),
            size = Size(half * 2 + 2.dp.toPx(), size.height - inset * 2 + 2.dp.toPx()),
            cornerRadius = CornerRadius(half + 1.dp.toPx()),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(x - half, inset),
            size = Size(half * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(half),
        )
    }
}
