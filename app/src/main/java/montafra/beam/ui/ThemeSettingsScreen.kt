package montafra.beam.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import montafra.beam.BeamFont
import montafra.beam.R
import montafra.beam.applyNightMode
import montafra.beam.settingsName
import montafra.beam.settingsUpdateInd
import montafra.beam.ui.theme.BeamCard
import montafra.beam.ui.theme.CardGap
import montafra.beam.ui.theme.CardSpacing
import montafra.beam.ui.theme.ColorStyle
import montafra.beam.ui.theme.LocalCardSpacing
import montafra.beam.ui.theme.LocalOutlineOnlyCards
import montafra.beam.ui.theme.StylePreview
import montafra.beam.ui.theme.cardShapeBottom
import montafra.beam.ui.theme.cardShapeMiddle
import montafra.beam.ui.theme.cardShapeSingle
import montafra.beam.ui.theme.cardShapeTop
import montafra.beam.ui.theme.defaultSeedColor
import montafra.beam.ui.theme.fallbackSeedColor
import montafra.beam.ui.theme.fontFamilyFor
import montafra.beam.ui.theme.rememberCardInteraction
import montafra.beam.ui.theme.stylePreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(navController: BeamNavController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    var themeMode by remember { mutableStateOf(prefs.getString("themeMode", "system") ?: "system") }
    var customColorValue by remember { mutableIntStateOf(prefs.getInt("themeColorValue", defaultSeedColor)) }
    var colorStyle by remember { mutableStateOf(ColorStyle.forKey(prefs.getString("themeColorStyle", null))) }
    var heroBacklight by remember { mutableStateOf(prefs.getBoolean("heroBacklight", true)) }
    var showChargeLevel by remember { mutableStateOf(prefs.getBoolean("showChargeLevel", true)) }
    var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean("hapticsEnabled", true)) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("soundEnabled", true)) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keepScreenOn", false)) }
    var fontFamily by remember { mutableStateOf(prefs.getString("fontFamily", "default") ?: "default") }
    var outlineOnlyCards by remember { mutableStateOf(prefs.getBoolean("outlineOnlyCards", false)) }

    SettingsScaffold(
        title = stringResource(R.string.theme),
        onBack = { navController.popBackStack() },
    ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                SubLabel(stringResource(R.string.themeMode))
                Spacer(Modifier.height(8.dp))
                val modeOptions = listOf(
                    stringResource(R.string.themeModeSystem),
                    stringResource(R.string.themeModeLight),
                    stringResource(R.string.themeModeDark),
                    stringResource(R.string.themeModeOled),
                )
                val modeKeys = listOf("system", "light", "dark", "oled")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modeOptions.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = themeMode == modeKeys[i],
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                themeMode = modeKeys[i]
                                prefs.edit().putString("themeMode", themeMode).commit()
                                applyNightMode(themeMode)
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, modeOptions.size),
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(LocalCardSpacing.current.group))
                SubLabel(stringResource(R.string.themeColor))
                Spacer(Modifier.height(8.dp))
                val colorOptions = listOf(
                    stringResource(R.string.themeColorAuto),
                    stringResource(R.string.themeColorCustom),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    colorOptions.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = if (i == 0) customColorValue == -1 else customColorValue != -1,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (i == 0) {
                                    customColorValue = -1
                                    prefs.edit().putInt("themeColorValue", -1).commit()
                                } else {
                                    val color = if (customColorValue != -1) customColorValue else defaultSeedColor
                                    customColorValue = color
                                    prefs.edit().putInt("themeColorValue", color).commit()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, colorOptions.size),
                            label = { Text(label) },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = customColorValue != -1,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        ColorSwatchPicker(
                            selectedColor = customColorValue.takeIf { it != -1 },
                            onColorSelected = { color ->
                                customColorValue = color
                                prefs.edit().putInt("themeColorValue", color).commit()
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                        CustomColorButton(
                            color = customColorValue.takeIf { it != -1 } ?: defaultSeedColor,
                            onClick = { navController.navigate("settings/theme/color") },
                        )
                    }
                }
                Spacer(Modifier.height(LocalCardSpacing.current.group))
                SubLabel(stringResource(R.string.colorStyle))
                Spacer(Modifier.height(8.dp))
                // Below API 31 "Auto" still resolves to a scheme the app generates itself, so a
                // style genuinely applies there. Only the wallpaper palette is the system's to
                // decide, and restyling it would stop it matching the wallpaper.
                val styleEnabled = customColorValue != -1 || Build.VERSION.SDK_INT < 31
                ColorStyleRow(
                    // On Auto below API 31 the real seed is the fallback, and previewing off the
                    // live primary instead would shift an already-shifted hue a second time.
                    // Above it the wallpaper palette has no seed to recover, so its primary is the
                    // closest honest stand-in - and the row is dimmed there anyway.
                    seedColor = customColorValue.takeIf { it != -1 }
                        ?: if (Build.VERSION.SDK_INT < 31) {
                            fallbackSeedColor
                        } else {
                            MaterialTheme.colorScheme.primary.toArgb()
                        },
                    selected = colorStyle,
                    enabled = styleEnabled,
                    onStyleSelected = { style ->
                        colorStyle = style
                        prefs.edit().putString("themeColorStyle", style.key).commit()
                    },
                )
                AnimatedVisibility(
                    visible = !styleEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Text(
                        stringResource(R.string.colorStyleAutoHint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item {
                SubLabel(stringResource(R.string.customization))
                Spacer(Modifier.height(8.dp))
                val fontKeys = remember { listOf("default") + BeamFont.entries.map { it.key } }
                val fontLabels = listOf(stringResource(R.string.fontDefault)) + BeamFont.entries.map { it.label }
                // Each entry previews itself, so the families are built once rather than on
                // every recomposition of the row and of every open menu item.
                val fontFamilies = remember(context) { fontKeys.map { fontFamilyFor(context, it) } }
                var fontMenuExpanded by remember { mutableStateOf(false) }
                val selectedFontIndex = fontKeys.indexOf(fontFamily).takeIf { it >= 0 } ?: 0
                val selectedFontLabel = fontLabels[selectedFontIndex]
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShapeTop(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = rememberCardInteraction(),
                                indication = LocalIndication.current,
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                fontMenuExpanded = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.font), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.fontDesc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(modifier = Modifier.padding(start = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    selectedFontLabel,
                                    fontFamily = fontFamilies[selectedFontIndex],
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ico_arrow_drop_down),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = fontMenuExpanded,
                                onDismissRequest = { fontMenuExpanded = false },
                            ) {
                                fontKeys.forEachIndexed { i, key ->
                                    DropdownMenuItem(
                                        text = { Text(fontLabels[i], fontFamily = fontFamilies[i]) },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            fontFamily = key
                                            prefs.edit().putString("fontFamily", key).commit()
                                            montafra.beam.BeamTempWidgetProvider.requestUpdate(context)
                                            // The notification icon is drawn in the :batteryStatus
                                            // process, which only re-reads settings on this.
                                            context.sendBroadcast(
                                                Intent(settingsUpdateInd).setPackage(context.packageName)
                                            )
                                            fontMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                CardGap()
                BeamCard(modifier = Modifier.fillMaxWidth(), shape = cardShapeMiddle()) {
                    ThemeToggleRow(
                        title = stringResource(R.string.outlinedCards),
                        description = stringResource(R.string.outlinedCardsDesc),
                        checked = outlineOnlyCards,
                        onToggle = {
                            outlineOnlyCards = it
                            prefs.edit().putBoolean("outlineOnlyCards", it).commit()
                        },
                    )
                }
                CardGap()
                BeamCard(modifier = Modifier.fillMaxWidth(), shape = cardShapeMiddle()) {
                    // The CompositionLocal is the single source of truth, so the row can never
                    // disagree with the spacing the screen below it is actually drawing.
                    val spacing = LocalCardSpacing.current
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.cardSpacing), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.cardSpacingDesc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            CardSpacing.entries.forEachIndexed { i, option ->
                                SegmentedButton(
                                    selected = spacing == option,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        prefs.edit().putString("cardSpacing", option.key).commit()
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(i, CardSpacing.entries.size),
                                    label = { Text(stringResource(option.labelRes)) },
                                )
                            }
                        }
                    }
                }
                CardGap()
                BeamCard(modifier = Modifier.fillMaxWidth(), shape = cardShapeMiddle()) {
                    ThemeToggleRow(
                        title = stringResource(R.string.hapticsEnabled),
                        description = stringResource(R.string.hapticsEnabledDesc),
                        checked = hapticsEnabled,
                        onToggle = {
                            hapticsEnabled = it
                            prefs.edit().putBoolean("hapticsEnabled", it).commit()
                        },
                    )
                }
                CardGap()
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShapeBottom(),
                ) {
                    ThemeToggleRow(
                        title = stringResource(R.string.soundEnabled),
                        description = stringResource(R.string.soundEnabledDesc),
                        checked = soundEnabled,
                        onToggle = {
                            soundEnabled = it
                            prefs.edit().putBoolean("soundEnabled", it).commit()
                        },
                    )
                }
            }
            item {
                SubLabel(stringResource(R.string.home))
                Spacer(Modifier.height(8.dp))
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShapeTop(),
                ) {
                    ThemeToggleRow(
                        title = stringResource(R.string.heroBacklight),
                        description = stringResource(R.string.heroBacklightDesc),
                        checked = heroBacklight,
                        onToggle = {
                            heroBacklight = it
                            prefs.edit().putBoolean("heroBacklight", it).commit()
                        },
                    )
                }
                CardGap()
                BeamCard(modifier = Modifier.fillMaxWidth(), shape = cardShapeMiddle()) {
                    ThemeToggleRow(
                        title = stringResource(R.string.chargeLevel),
                        description = stringResource(R.string.heroChargeLevelDesc),
                        checked = showChargeLevel,
                        onToggle = {
                            showChargeLevel = it
                            prefs.edit().putBoolean("showChargeLevel", it).commit()
                        },
                    )
                }
                CardGap()
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShapeBottom(),
                ) {
                    ThemeToggleRow(
                        title = stringResource(R.string.keepScreenOn),
                        description = stringResource(R.string.keepScreenOnDesc),
                        checked = keepScreenOn,
                        onToggle = {
                            keepScreenOn = it
                            prefs.edit().putBoolean("keepScreenOn", it).commit()
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * Opens the full picker. The leading dot is the live seed, so the button doubles as an answer to
 * "which colour is Custom on right now" when the seed isn't one of the twelve swatches above.
 */
@Composable
private fun CustomColorButton(color: Int, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val outlineOnly = LocalOutlineOnlyCards.current
    val seed = Color(color)
    FilledTonalButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        // Card colours, card radius, and it follows Outlined Cards the way BeamCard does - the
        // button belongs to the page it sits on, not to Material's tonal button palette.
        shape = cardShapeSingle(),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (outlineOnly) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = if (outlineOnly) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline) else null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(seed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ico_palette),
                contentDescription = null,
                // The seed is whatever the user picked, so the glyph takes whichever of black or
                // white survives on it rather than a theme colour that might vanish.
                tint = if (seed.luminance() > 0.5f) Color.Black.copy(alpha = 0.7f) else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.themeColorCustomPick),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ico_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A preview per colour style of what it would make of the current seed. Scrolls rather than wraps
 * so the styles stay in one comparable line, the way the system wallpaper picker shows them.
 */
@Composable
private fun ColorStyleRow(
    seedColor: Int,
    selected: ColorStyle,
    enabled: Boolean,
    onStyleSelected: (ColorStyle) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // Every preview is a handful of gamut searches; only redo them when the seed or the
    // light/dark mode actually moves, never on a scroll.
    val previews = remember(seedColor, isDark) {
        ColorStyle.entries.associateWith { stylePreview(seedColor, it, isDark) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .alpha(if (enabled) 1f else 0.38f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ColorStyle.entries.forEach { style ->
            ColorStyleChip(
                preview = previews.getValue(style),
                label = stringResource(style.labelRes),
                selected = style == selected,
                enabled = enabled,
                onClick = { onStyleSelected(style) },
            )
        }
    }
}

@Composable
private fun ColorStyleChip(
    preview: StylePreview,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            // Same double-clip ring the colour swatches use, squared off: an onSurface disc
            // behind, inset, then the preview clipped over the top of it.
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                .padding(if (selected) 3.dp else 0.dp)
                .clip(RoundedCornerShape(if (selected) 17.dp else 20.dp))
                .clickable(enabled = enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
        ) {
            // A quadrant each for the three accents the style moves, over the grey it tints -
            // enough to tell Vibrant from Neutral from Monochrome at a glance. The seams are the
            // page's own surface rather than a drawn line, so they read as gaps between tiles and
            // stay right in OLED, where anything else would glow against the black.
            val seam = MaterialTheme.colorScheme.surface
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(preview.primary))
                    Box(Modifier.width(1.5.dp).fillMaxHeight().background(seam))
                    Box(Modifier.weight(1f).fillMaxHeight().background(preview.secondary))
                }
                Box(Modifier.height(1.5.dp).fillMaxWidth().background(seam))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(preview.tertiary))
                    Box(Modifier.width(1.5.dp).fillMaxHeight().background(seam))
                    Box(Modifier.weight(1f).fillMaxHeight().background(preview.surface))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun ThemeToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = rememberCardInteraction(),
                indication = LocalIndication.current,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle(it)
            },
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
