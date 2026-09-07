package montafra.beam.ui.theme.hct

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The colour-science primitives CAM16 and HCT are built on, ported from Google's
 * material-color-utilities: sRGB <-> XYZ, the CIE L* transfer functions, and a few degree and
 * clamp helpers.
 *
 * Everything here is internal to this package - the rest of the app talks to [Hct] and
 * [TonalPalette] and never touches these directly.
 */

/** sRGB white point, D65, scaled to Y = 100. */
internal val WhitePointD65 = doubleArrayOf(95.047, 100.0, 108.883)

internal fun sanitizeDegreesDouble(degrees: Double): Double {
    val wrapped = degrees % 360.0
    return if (wrapped < 0) wrapped + 360.0 else wrapped
}

internal fun clampDouble(min: Double, max: Double, input: Double): Double =
    if (input < min) min else if (input > max) max else input

internal fun clampInt(min: Int, max: Int, input: Int): Int =
    if (input < min) min else if (input > max) max else input

internal fun lerpDouble(start: Double, stop: Double, amount: Double): Double =
    (1.0 - amount) * start + amount * stop

internal fun argbFromRgb(red: Int, green: Int, blue: Int): Int =
    (255 shl 24) or ((red and 255) shl 16) or ((green and 255) shl 8) or (blue and 255)

internal fun redFromArgb(argb: Int): Int = (argb shr 16) and 255

internal fun greenFromArgb(argb: Int): Int = (argb shr 8) and 255

internal fun blueFromArgb(argb: Int): Int = argb and 255

/** An sRGB channel (0..255) undone back to linear light, scaled to 0..100. */
internal fun linearized(rgbComponent: Int): Double {
    val normalized = rgbComponent / 255.0
    return if (normalized <= 0.040449936) {
        normalized / 12.92 * 100.0
    } else {
        ((normalized + 0.055) / 1.055).pow(2.4) * 100.0
    }
}

/** The inverse of [linearized], clamped back into a byte. */
internal fun delinearized(rgbComponent: Double): Int {
    val normalized = rgbComponent / 100.0
    val delinearized = if (normalized <= 0.0031308) {
        normalized * 12.92
    } else {
        1.055 * normalized.pow(1.0 / 2.4) - 0.055
    }
    return clampInt(0, 255, (delinearized * 255.0).roundToInt())
}

private const val LabE = 216.0 / 24389.0
private const val LabKappa = 24389.0 / 27.0

private fun labF(t: Double): Double =
    if (t > LabE) Math.cbrt(t) else (LabKappa * t + 16.0) / 116.0

private fun labInvf(ft: Double): Double {
    val ft3 = ft * ft * ft
    return if (ft3 > LabE) ft3 else (116.0 * ft - 16.0) / LabKappa
}

/** Relative luminance (0..100) of a given L* tone. */
internal fun yFromLstar(lstar: Double): Double = 100.0 * labInvf((lstar + 16.0) / 116.0)

internal fun lstarFromY(y: Double): Double = labF(y / 100.0) * 116.0 - 16.0

/** L*, i.e. the "tone" axis of HCT, of an sRGB colour. */
internal fun lstarFromArgb(argb: Int): Double {
    val y = 0.2126 * linearized(redFromArgb(argb)) +
        0.7152 * linearized(greenFromArgb(argb)) +
        0.0722 * linearized(blueFromArgb(argb))
    return lstarFromY(y)
}

/** The grey with the given L*. Used whenever a requested colour has no chroma to give. */
internal fun argbFromLstar(lstar: Double): Int {
    val component = delinearized(yFromLstar(lstar))
    return argbFromRgb(component, component, component)
}

internal fun argbFromXyz(x: Double, y: Double, z: Double): Int {
    val red = 3.2413774792388685 * x - 1.5376652402851851 * y - 0.49885366846268053 * z
    val green = -0.9691452513005321 * x + 1.8758853451067872 * y + 0.04156585616912061 * z
    val blue = 0.05562093689691305 * x - 0.20395524564742123 * y + 1.0571799111220335 * z
    return argbFromRgb(delinearized(red), delinearized(green), delinearized(blue))
}
