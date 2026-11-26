package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Pure width constraints for the adjustable Split Scroll columns.
 *
 * The two weights always add to 100, while keeping each column wide enough
 * to remain usable on narrow reader screens.
 */
internal object WebtoonSplitScrollColumnWidths {

    const val MIN_PERCENT = 20
    const val MAX_PERCENT = 80
    const val DEFAULT_PERCENT = 50

    fun normalize(leftPercent: Int): Int =
        leftPercent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun weights(leftPercent: Int): Pair<Float, Float> {
        val left = normalize(leftPercent).toFloat()
        return left to (100f - left)
    }
}
