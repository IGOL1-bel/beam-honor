package montafra.beam.ui.theme.hct

import kotlin.math.abs

/**
 * A colour in Material's HCT space: CAM16 hue and chroma over CIE L* tone.
 *
 * Its point is that tone *is* L*, so two colours at the same tone have the same measured
 * lightness no matter their hue - which is what lets one set of tone numbers (40 for a light
 * primary, 80 for a dark one, and so on) produce a legible palette from any seed.
 */
internal class Hct private constructor(
    val hue: Double,
    val chroma: Double,
    val tone: Double,
    val argb: Int,
) {
    companion object {
        fun fromInt(argb: Int): Hct {
            val cam = Cam16.fromInt(argb)
            return Hct(cam.hue, cam.chroma, lstarFromArgb(argb), argb)
        }

        /**
         * The sRGB colour closest to the requested hue and tone at the most chroma sRGB can
         * actually deliver there - asking for more chroma than the gamut holds is the normal
         * case, not an error (Vibrant asks for 200).
         */
        fun from(hue: Double, chroma: Double, tone: Double): Hct = fromInt(solveToInt(hue, chroma, tone))

        private fun solveToInt(hueIn: Double, chroma: Double, toneIn: Double): Int {
            val hue = sanitizeDegreesDouble(hueIn)
            val tone = clampDouble(0.0, 100.0, toneIn)
            // Near black and near white there is no room for chroma anyway.
            if (chroma < 1.0 || tone < 1.0 || tone > 99.0) return argbFromLstar(tone)

            findCamByJ(hue, chroma, tone)?.let { return it.toInt() }

            // The request sits outside the gamut at this hue and tone. Whether a chroma is
            // reachable falls off monotonically, so bisect for the most saturated one that fits
            // instead of walking down a unit at a time the way material-color-utilities does:
            // same answer to well under a perceptible step, an order of magnitude fewer CAM16
            // round trips, and this runs during composition when the theme changes.
            var low = 0.0
            var high = chroma
            var best: Cam16? = null
            repeat(12) {
                val mid = low + (high - low) / 2.0
                val cam = findCamByJ(hue, mid, tone)
                if (cam != null) {
                    best = cam
                    low = mid
                } else {
                    high = mid
                }
            }
            return best?.toInt() ?: argbFromLstar(tone)
        }

        /**
         * Search CAM16 lightness for the colour that lands on [tone] while keeping [hue] and
         * [chroma] faithful once clipped into sRGB. Null means this hue/chroma/tone combination
         * has no honest sRGB answer.
         */
        private fun findCamByJ(hue: Double, chroma: Double, tone: Double): Cam16? {
            var low = 0.0
            var high = 100.0
            var bestdL = 1000.0
            var bestdE = 1000.0
            var best: Cam16? = null
            while (abs(low - high) > 0.01) {
                val mid = low + (high - low) / 2.0
                val clipped = Cam16.fromJch(mid, chroma, hue).toInt()
                val clippedLstar = lstarFromArgb(clipped)
                val dL = abs(tone - clippedLstar)
                if (dL < 0.2) {
                    val camClipped = Cam16.fromInt(clipped)
                    val dE = camClipped.distance(Cam16.fromJch(camClipped.j, camClipped.chroma, hue))
                    if (dE <= 1.0 && dE <= bestdE) {
                        bestdL = dL
                        bestdE = dE
                        best = camClipped
                    }
                }
                if (bestdL == 0.0 && bestdE == 0.0) break
                if (clippedLstar < tone) low = mid else high = mid
            }
            return best
        }
    }
}
