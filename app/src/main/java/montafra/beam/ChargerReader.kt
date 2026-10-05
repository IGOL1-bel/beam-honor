package montafra.beam

import java.io.File

/**
 * Voltage and current at the charger input (the charging controller side), as opposed to the
 * battery terminals that BatteryManager reports.
 *
 * Android has no public API for this, so it is read from the kernel's power_supply class. Which
 * node carries it differs per device and per kernel (usb, pc_port, charger, wireless, ...), so
 * every supply that is not the battery and is currently online is tried. SELinux may deny some
 * nodes to third-party apps; a denied or missing node simply yields null and the caller falls
 * back to the battery readings.
 */
object ChargerReader {
    class Sample(
        /** Directory name under /sys/class/power_supply the values came from. */
        val source: String,
        val millivolts: Double?,
        /** Magnitude, in the raw unit of the node (microamps on a conforming kernel). */
        val currentRaw: Double?,
    )

    private const val ROOT = "/sys/class/power_supply"
    private const val RESCAN_MS = 15_000L

    // Candidate node names, in order of preference.
    private val voltageNodes = listOf("voltage_now", "input_voltage_now", "vbus_voltage", "voltage_vbus")
    private val currentNodes = listOf("current_now", "input_current_now", "input_current", "current_avg")

    // Not a charger input: the battery itself and the fuel gauge.
    private val skipTypes = setOf("battery", "bms")

    @Volatile private var dirs: List<File> = emptyList()
    @Volatile private var scannedAt = 0L

    private fun readText(file: File): String? = try {
        file.bufferedReader().use { it.readLine()?.trim() }
    } catch (_: Exception) {
        null
    }

    private fun readLong(dir: File, name: String): Long? = readText(File(dir, name))?.toLongOrNull()

    private fun candidates(): List<File> {
        val now = System.currentTimeMillis()
        if (now - scannedAt < RESCAN_MS && dirs.isNotEmpty()) return dirs
        val found = try {
            File(ROOT).listFiles()?.filter { d ->
                val type = readText(File(d, "type"))?.lowercase()
                type != null && type !in skipTypes
            }.orEmpty().sortedBy { it.name }
        } catch (_: Exception) {
            emptyList()
        }
        dirs = found
        scannedAt = now
        return found
    }

    private fun toMillivolts(raw: Long): Double =
        // 5-20 V is 5 000-20 000 mV or 5 000 000-20 000 000 uV; nothing real sits in between.
        if (raw > 100_000L) raw / 1000.0 else raw.toDouble()

    /** The first online supply that exposes a usable voltage, or null when none does. */
    fun read(): Sample? {
        var voltageOnly: Sample? = null
        for (dir in candidates()) {
            val online = readLong(dir, "online")
            if (online != null && online == 0L) continue

            val volts = voltageNodes.firstNotNullOfOrNull { readLong(dir, it) }
                ?.takeIf { it > 0L }
                ?.let { toMillivolts(it) }
            val amps = currentNodes.firstNotNullOfOrNull { readLong(dir, it) }
                ?.let { kotlin.math.abs(it).toDouble() }

            if (volts == null) continue
            if (amps != null) return Sample(dir.name, volts, amps)
            if (voltageOnly == null) voltageOnly = Sample(dir.name, volts, null)
        }
        return voltageOnly
    }
}
