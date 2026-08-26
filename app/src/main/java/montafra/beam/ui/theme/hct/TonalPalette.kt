package montafra.beam.ui.theme.hct

/**
 * One hue and chroma sampled at any tone - the ladder every Material colour role is picked off.
 * A palette's tones are what make `primary` and `onPrimary` contrast by construction rather than
 * by luck.
 *
 * Tones are cached because a scheme reads the same handful (10, 20, 30, 40, 80, 90, ...) several
 * times over, and each miss costs a gamut search in [Hct].
 */
internal class TonalPalette private constructor(val hue: Double, val chroma: Double) {
    private val cache = HashMap<Int, Int>()

    fun tone(tone: Int): Int = cache.getOrPut(tone) { Hct.from(hue, chroma, tone.toDouble()).argb }

    companion object {
        fun fromHueAndChroma(hue: Double, chroma: Double): TonalPalette =
            TonalPalette(sanitizeDegreesDouble(hue), chroma)
    }
}
