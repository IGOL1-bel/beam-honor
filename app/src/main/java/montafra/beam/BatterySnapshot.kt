package montafra.beam


class BatterySnapshot(
    // configuration
    private val currentScalar: Double,
    private val invertCurrent: Boolean,

    // data
    val chargeStatus: ChargeStatus,
    val chargeTimeRemainingRaw: Long,
    val currentRaw: Long?,
    val energyRaw: Long?,
    val level: Double?,
    val isChargingRaw: Boolean,
    val pluggedRaw: Long?,
    val tempRaw: Long?,
    val voltsRaw: Long?,

    // Charger side: voltage at the charging controller (mV) and input current magnitude (raw
    // node unit, scaled by currentScalar like the battery current). Both null when the device
    // does not expose them or the option is off.
    private val chargerMillivolts: Double? = null,
    private val chargerCurrentRaw: Double? = null,
) {
    private fun fromMicros(v: Double?) : Double? {
        return v?.div(1_000_000.0)
    }

    private fun fromMillis(v: Double?) : Double? {
        return v?.div(1_000.0)
    }

    // While a charger is plugged in and its input is readable, the displayed voltage, current
    // and power describe the charger input. Everything about the battery's own state (stored
    // energy, time to full) keeps using the battery-side values below.
    private val chargerActive : Boolean get() =
        plugged == true && chargerMillivolts != null && chargerCurrentRaw != null

    private val batteryMicroamps : Double? get() {
        val sign = if (invertCurrent) 1.0 else -1.0
        return currentRaw?.times(currentScalar)?.times(sign)
    }
    private val batteryMillivolts : Double? get() = voltsRaw?.toDouble()
    private val batteryVolts : Double? get() = fromMillis(batteryMillivolts)
    private val batteryWatts : Double? get() = fromMicros(batteryMicroamps).times(batteryVolts)

    // Same sign convention as the battery: negative means power flowing into the device. The
    // charger input is always inbound, so its sign is fixed rather than device-dependent.
    val microamps : Double? get() =
        if (chargerActive) chargerCurrentRaw?.let { -(it * currentScalar) } else batteryMicroamps
    val milliamps : Double? get() = microamps?.div(1_000.0)
    val amps : Double? get() = fromMicros(microamps)

    val millivolts : Double? get() = if (chargerActive) chargerMillivolts else batteryMillivolts
    val volts : Double? get() = fromMillis(millivolts)

    val watts : Double? get() = amps.times(volts)

    // True when the figures above come from the charger input rather than the battery.
    val fromCharger : Boolean get() = chargerActive

    val energyAmpHours : Double? get() = fromMicros(energyRaw?.toDouble())
    val energyWattHours : Double? get() = batteryVolts?.times(energyAmpHours)

    val levelPercent : Double? get() = level?.times(100.0)

    val celsius : Double? get() = tempRaw?.toDouble()?.div(10.0)

    val plugType : PlugType? get() = PlugType.fromRaw(pluggedRaw?.toInt())

    // Null when the battery broadcast carried no plug state at all. That is "don't know", not
    // "not plugged", and the two must not be confused: acting on this resets the screen-time
    // session. Kept separate from plugType, which folds "not plugged" into null as well.
    val plugged : Boolean? get() = pluggedRaw?.let { it != 0L }

    // Prefer the OS-reported charge status; some devices misreport isCharging or the
    // current sign, so only fall back to those heuristics when the status is unknown.
    val charging : Boolean get() {
        when (chargeStatus) {
            ChargeStatus.Charging, ChargeStatus.Full -> return true
            ChargeStatus.Discharging, ChargeStatus.NotCharging -> return false
            ChargeStatus.Unknown -> {} // fall through to the heuristics below
        }

        if (isChargingRaw)
            return true

        val ma = batteryMicroamps?.div(1_000.0)
        return ma != null && ma < 0.0
    }

    val secondsUntilCharged: Double? get() {
        // Some devices incorrectly report "0 seconds to full" when not charging,
        // so ensure we are actually charging first.
        if (!charging)
            return null

        val ms = chargeTimeRemainingRaw
        if (ms == -1L)
            return null

        // on some devices, chargeTimeRemainingRaw is always 0, even when the device is charging
        // in this case, we calculate the expected charge duration manually by
        // assuming a linear curve (which is not really correct practically, but
        // modeling battery charge curves is too complex for doing it here
        if (ms == 0L && level != null && level < 0.99) {
            // Whether the current is reported positive or negative while charging depends
            // on the device and on the invertCurrent workaround, so only its magnitude is
            // usable here; the charging check above has already established the direction.
            // Battery-side power on purpose: charger input power includes conversion losses and
            // would make the estimate too short.
            val chargePower = batteryWatts?.let { kotlin.math.abs(it) }

            // Below ~1% the capacity extrapolation divides by a near-zero level and yields
            // Infinity/NaN, and a zero charge counter yields a bogus 0s. Give up in both
            // cases: falling through to fromMillis(0) below would report "fully charged".
            val energy = energyWattHours
            if (chargePower != null && chargePower > 0 && level >= 0.01 && energy != null && energy > 0) {
                // energyWattHours contains the energy currently charged in the battery
                val batteryCapacityWattHours = energy / level
                val fullChargeDurationHours = batteryCapacityWattHours / chargePower

                val remainingChargePercentage = 1.0 - level
                val hoursUntilCharged = fullChargeDurationHours * remainingChargePercentage
                return hoursUntilCharged * 3600
            }

            return null
        }

        return fromMillis(ms.toDouble())
    }
}
