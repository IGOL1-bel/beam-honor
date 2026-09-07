package montafra.beam.ui.theme.hct

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a colour is being looked at: the illuminant, how bright the surroundings are, and how far
 * the eye has adapted to them. CAM16 needs all of it to say what a colour *appears* as, rather
 * than what its pixel values are.
 *
 * Only [Default] is ever used here - the standard sRGB viewing environment Material's own colour
 * pipeline assumes.
 */
internal class ViewingConditions private constructor(
    val n: Double,
    val aw: Double,
    val nbb: Double,
    val ncb: Double,
    val c: Double,
    val nc: Double,
    val rgbD: DoubleArray,
    val fl: Double,
    val flRoot: Double,
    val z: Double,
) {
    companion object {
        val Default: ViewingConditions = make()

        private fun make(
            whitePoint: DoubleArray = WhitePointD65,
            adaptingLuminance: Double = (200.0 / PI) * yFromLstar(50.0) / 100.0,
            backgroundLstar: Double = 50.0,
            surround: Double = 2.0,
            discountingIlluminant: Boolean = false,
        ): ViewingConditions {
            // Transform the white point into the CAM16 cone response space.
            val rW = whitePoint[0] * 0.401288 + whitePoint[1] * 0.650173 + whitePoint[2] * -0.051461
            val gW = whitePoint[0] * -0.250268 + whitePoint[1] * 1.204414 + whitePoint[2] * 0.045854
            val bW = whitePoint[0] * -0.002079 + whitePoint[1] * 0.048952 + whitePoint[2] * 0.953127

            val f = 0.8 + surround / 10.0
            val c = if (f >= 0.9) {
                lerpDouble(0.59, 0.69, (f - 0.9) * 10.0)
            } else {
                lerpDouble(0.525, 0.59, (f - 0.8) * 10.0)
            }
            val d = if (discountingIlluminant) {
                1.0
            } else {
                clampDouble(0.0, 1.0, f * (1.0 - (1.0 / 3.6) * exp((-adaptingLuminance - 42.0) / 92.0)))
            }
            val rgbD = doubleArrayOf(
                d * (100.0 / rW) + 1.0 - d,
                d * (100.0 / gW) + 1.0 - d,
                d * (100.0 / bW) + 1.0 - d,
            )

            val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
            val k4 = k * k * k * k
            val k4F = 1.0 - k4
            val fl = k4 * adaptingLuminance + 0.1 * k4F * k4F * Math.cbrt(5.0 * adaptingLuminance)

            val n = yFromLstar(backgroundLstar) / whitePoint[1]
            val z = 1.48 + sqrt(n)
            val nbb = 0.725 / n.pow(0.2)

            val rgbAFactors = doubleArrayOf(
                (fl * rgbD[0] * rW / 100.0).pow(0.42),
                (fl * rgbD[1] * gW / 100.0).pow(0.42),
                (fl * rgbD[2] * bW / 100.0).pow(0.42),
            )
            val rgbA = doubleArrayOf(
                400.0 * rgbAFactors[0] / (rgbAFactors[0] + 27.13),
                400.0 * rgbAFactors[1] / (rgbAFactors[1] + 27.13),
                400.0 * rgbAFactors[2] / (rgbAFactors[2] + 27.13),
            )
            val aw = (2.0 * rgbA[0] + rgbA[1] + 0.05 * rgbA[2]) * nbb

            return ViewingConditions(
                n = n,
                aw = aw,
                nbb = nbb,
                ncb = nbb,
                c = c,
                nc = f,
                rgbD = rgbD,
                fl = fl,
                flRoot = fl.pow(0.25),
                z = z,
            )
        }
    }
}

/**
 * A colour in the CIECAM16 appearance model. [Hct] uses it as its engine: hue and chroma come
 * straight from here, and the CAM16-UCS coordinates ([jstar]/[astar]/[bstar]) give the perceptual
 * [distance] the gamut search needs to tell a faithful answer from a clipped one.
 */
internal class Cam16 private constructor(
    val hue: Double,
    val chroma: Double,
    val j: Double,
    val jstar: Double,
    val astar: Double,
    val bstar: Double,
) {
    /** Perceptual difference in CAM16-UCS. Below ~1.0 two colours are effectively the same. */
    fun distance(other: Cam16): Double {
        val dJ = jstar - other.jstar
        val dA = astar - other.astar
        val dB = bstar - other.bstar
        return 1.41 * sqrt(dJ * dJ + dA * dA + dB * dB).pow(0.63)
    }

    /** Back to sRGB, clipping into gamut on the way. */
    fun toInt(): Int = viewed(ViewingConditions.Default)

    private fun viewed(vc: ViewingConditions): Int {
        val alpha = if (chroma == 0.0 || j == 0.0) 0.0 else chroma / sqrt(j / 100.0)
        val t = (alpha / (1.64 - 0.29.pow(vc.n)).pow(0.73)).pow(1.0 / 0.9)
        val hRad = hue * PI / 180.0
        val eHue = 0.25 * (cos(hRad + 2.0) + 3.8)
        val ac = vc.aw * (j / 100.0).pow(1.0 / vc.c / vc.z)
        val p1 = eHue * (50000.0 / 13.0) * vc.nc * vc.ncb
        val p2 = ac / vc.nbb

        val hSin = sin(hRad)
        val hCos = cos(hRad)
        val gamma = 23.0 * (p2 + 0.305) * t / (23.0 * p1 + 11.0 * t * hCos + 108.0 * t * hSin)
        val a = gamma * hCos
        val b = gamma * hSin

        val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
        val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
        val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0

        val rC = inverseAdapt(rA, vc.fl) / vc.rgbD[0]
        val gC = inverseAdapt(gA, vc.fl) / vc.rgbD[1]
        val bC = inverseAdapt(bA, vc.fl) / vc.rgbD[2]

        val x = 1.86206786 * rC - 1.01125463 * gC + 0.14918677 * bC
        val y = 0.38752654 * rC + 0.62144744 * gC - 0.00897398 * bC
        val z = -0.01584150 * rC - 0.03412294 * gC + 1.04996444 * bC

        return argbFromXyz(x, y, z)
    }

    companion object {
        fun fromInt(argb: Int): Cam16 {
            val vc = ViewingConditions.Default

            val redL = linearized(redFromArgb(argb))
            val greenL = linearized(greenFromArgb(argb))
            val blueL = linearized(blueFromArgb(argb))
            val x = 0.41233895 * redL + 0.35762064 * greenL + 0.18051042 * blueL
            val y = 0.2126 * redL + 0.7152 * greenL + 0.0722 * blueL
            val z = 0.01932141 * redL + 0.11916382 * greenL + 0.95034478 * blueL

            val rC = 0.401288 * x + 0.650173 * y - 0.051461 * z
            val gC = -0.250268 * x + 1.204414 * y + 0.045854 * z
            val bC = -0.002079 * x + 0.048952 * y + 0.953127 * z

            val rA = adapt(vc.rgbD[0] * rC, vc.fl)
            val gA = adapt(vc.rgbD[1] * gC, vc.fl)
            val bA = adapt(vc.rgbD[2] * bC, vc.fl)

            val a = (11.0 * rA - 12.0 * gA + bA) / 11.0
            val b = (rA + gA - 2.0 * bA) / 9.0
            val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
            val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0

            val atanDegrees = atan2(b, a) * 180.0 / PI
            val hue = sanitizeDegreesDouble(atanDegrees)
            val hueRadians = hue * PI / 180.0

            val j = 100.0 * (p2 * vc.nbb / vc.aw).pow(vc.c * vc.z)
            val huePrime = if (hue < 20.14) hue + 360.0 else hue
            val eHue = 0.25 * (cos(huePrime * PI / 180.0 + 2.0) + 3.8)
            val p1 = 50000.0 / 13.0 * eHue * vc.nc * vc.ncb
            val t = p1 * hypot(a, b) / (u + 0.305)
            val alpha = (1.64 - 0.29.pow(vc.n)).pow(0.73) * t.pow(0.9)
            val chroma = alpha * sqrt(j / 100.0)

            return ucs(hue, chroma, j, chroma * vc.flRoot, hueRadians)
        }

        fun fromJch(j: Double, chroma: Double, hue: Double): Cam16 {
            val vc = ViewingConditions.Default
            return ucs(hue, chroma, j, chroma * vc.flRoot, hue * PI / 180.0)
        }

        /** The shared tail of both constructors: the CAM16-UCS coordinates. */
        private fun ucs(hue: Double, chroma: Double, j: Double, m: Double, hueRadians: Double): Cam16 {
            val jstar = (1.0 + 100.0 * 0.007) * j / (1.0 + 0.007 * j)
            val mstar = 1.0 / 0.0228 * ln(1.0 + 0.0228 * m)
            return Cam16(
                hue = hue,
                chroma = chroma,
                j = j,
                jstar = jstar,
                astar = mstar * cos(hueRadians),
                bstar = mstar * sin(hueRadians),
            )
        }

        private fun adapt(component: Double, fl: Double): Double {
            val af = (fl * abs(component) / 100.0).pow(0.42)
            return sign(component) * 400.0 * af / (af + 27.13)
        }

        private fun inverseAdapt(adapted: Double, fl: Double): Double {
            val base = (27.13 * abs(adapted) / (400.0 - abs(adapted))).coerceAtLeast(0.0)
            return sign(adapted) * (100.0 / fl) * base.pow(1.0 / 0.42)
        }
    }
}
