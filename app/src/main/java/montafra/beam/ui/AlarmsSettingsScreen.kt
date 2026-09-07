package montafra.beam.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import montafra.beam.R
import montafra.beam.alarmChannelId
import montafra.beam.alarmHighDefaultThreshold
import montafra.beam.alarmLowDefaultThreshold
import montafra.beam.alarmRepeatDefaultMin
import montafra.beam.alarmTempDefaultThreshold
import montafra.beam.cToF
import montafra.beam.settingsName
import montafra.beam.settingsUpdateInd
import montafra.beam.ui.theme.BeamCard
import montafra.beam.ui.theme.CardGap
import montafra.beam.ui.theme.cardShapeBottom
import montafra.beam.ui.theme.cardShapeMiddle
import montafra.beam.ui.theme.cardShapeSingle
import montafra.beam.ui.theme.cardShapeTop
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsSettingsScreen(navController: BeamNavController) {
    val context = LocalContext.current
    val haptic = LocalTapHaptics.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    // Threshold is stored/compared in Celsius; only its label converts for display.
    val useFahrenheit = remember { prefs.getBoolean("useFahrenheit", false) }

    // The three alarms differ only in these values; the pref keys are derived from the prefix
    // exactly like StatusService derives them, and the thresholds default to the same shared
    // constants, so the two sides cannot drift.
    val specs = remember {
        listOf(
            AlarmUiSpec("alarmLow", R.string.alarmLow, R.string.alarmLowDesc,
                alarmLowDefaultThreshold, 5f..50f, 44) { "$it%" },
            AlarmUiSpec("alarmHigh", R.string.alarmHigh, R.string.alarmHighDesc,
                alarmHighDefaultThreshold, 50f..100f, 49) { "$it%" },
            AlarmUiSpec("alarmTemp", R.string.alarmTemp, R.string.alarmTempDesc,
                alarmTempDefaultThreshold, 35f..55f, 19) { c ->
                if (useFahrenheit) "${cToF(c.toDouble()).roundToInt()}°F" else "$c°C"
            },
        )
    }
    val states = remember { specs.map { AlarmUiState(prefs, it) } }

    val repeatOptions = remember { listOf(5, 15, 30, 60) }
    val repeatLabels = remember { listOf("5m", "15m", "30m", "60m") }
    var repeatIndex by remember {
        mutableIntStateOf(
            repeatOptions.indexOf(prefs.getInt("alarmRepeatIntervalMin", alarmRepeatDefaultMin))
                .coerceAtLeast(0)
        )
    }

    // Alarms are evaluated inside the status service and delivered as notifications, so nothing
    // can alert the user while notifications are blocked or the status notification (which keeps
    // the service alive) is off. Failing silently would leave an armed alarm that never rings.
    val alarmsBlocked = remember {
        val noteMgr = NotificationManagerCompat.from(context)
        !prefs.getBoolean("notificationEnabled", true) ||
            !noteMgr.areNotificationsEnabled() ||
            noteMgr.getNotificationChannel(alarmChannelId)?.importance ==
                NotificationManagerCompat.IMPORTANCE_NONE
    }

    fun saveAlarms() {
        prefs.edit().apply {
            specs.forEachIndexed { i, spec ->
                val s = states[i]
                putBoolean("${spec.prefix}Enabled", s.enabled)
                putInt("${spec.prefix}Threshold", s.threshold)
                putBoolean("${spec.prefix}Repeat", s.repeat)
            }
            putInt("alarmRepeatIntervalMin", repeatOptions[repeatIndex])
        }.commit()
        context.sendBroadcast(
            Intent().setPackage(context.packageName).setAction(settingsUpdateInd)
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.alarms),
        onBack = { navController.popBackStack() },
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Text(
                text = stringResource(R.string.alarmsDesc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (alarmsBlocked) {
            item {
                BeamCard(modifier = Modifier.fillMaxWidth(), shape = cardShapeSingle()) {
                    Text(
                        text = stringResource(R.string.alarmsNeedNotifications),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
        item {
            specs.forEachIndexed { i, spec ->
                if (i > 0) CardGap()
                val s = states[i]
                AlarmCard(
                    shape = when (i) {
                        0 -> cardShapeTop()
                        specs.lastIndex -> cardShapeBottom()
                        else -> cardShapeMiddle()
                    },
                    title = stringResource(spec.titleRes),
                    description = stringResource(spec.descRes),
                    enabled = s.enabled,
                    onEnabledChange = { s.enabled = it; saveAlarms() },
                    valueLabel = spec.label(s.threshold),
                    sliderValue = s.threshold.toFloat(),
                    valueRange = spec.valueRange,
                    steps = spec.steps,
                    onSliderChange = { s.threshold = it.roundToInt() },
                    onSliderChangeFinished = { saveAlarms() },
                    repeat = s.repeat,
                    onRepeatChange = { s.repeat = it; saveAlarms() },
                    haptic = haptic,
                )
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                SubLabel(stringResource(R.string.alarmRepeatInterval))
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    repeatLabels.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = repeatIndex == i,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                repeatIndex = i
                                saveAlarms()
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, repeatLabels.size),
                            label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** The static half of one alarm row: pref-key prefix, strings, slider geometry, label format. */
private class AlarmUiSpec(
    val prefix: String,
    val titleRes: Int,
    val descRes: Int,
    val defaultThreshold: Int,
    val valueRange: ClosedFloatingPointRange<Float>,
    val steps: Int,
    val label: (Int) -> String,
)

/** The mutable half, seeded from the prefs the way StatusService reads them. */
private class AlarmUiState(prefs: SharedPreferences, spec: AlarmUiSpec) {
    var enabled by mutableStateOf(prefs.getBoolean("${spec.prefix}Enabled", false))
    var threshold by mutableIntStateOf(prefs.getInt("${spec.prefix}Threshold", spec.defaultThreshold))
    var repeat by mutableStateOf(prefs.getBoolean("${spec.prefix}Repeat", false))
}

@Composable
private fun AlarmCard(
    shape: Shape,
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    valueLabel: String,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    repeat: Boolean,
    onRepeatChange: (Boolean) -> Unit,
    haptic: HapticFeedback,
) {
    BeamCard(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
    ) {
        Column {
            ToggleSettingRow(
                title = title,
                description = description,
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(180)),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SubLabel(stringResource(R.string.alarmThreshold))
                        Text(
                            text = valueLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val sliderHaptic = LocalSilentHaptics.current
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            if (it.roundToInt() != sliderValue.roundToInt()) {
                                sliderHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onSliderChange(it)
                        },
                        onValueChangeFinished = onSliderChangeFinished,
                        valueRange = valueRange,
                        steps = steps,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRepeatChange(!repeat)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = stringResource(R.string.alarmRepeat),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.alarmRepeatDesc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = repeat,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRepeatChange(it)
                            },
                        )
                    }
                }
            }
        }
    }
}
