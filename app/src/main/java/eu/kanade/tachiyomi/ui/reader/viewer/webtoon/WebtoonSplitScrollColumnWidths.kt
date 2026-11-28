package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import kotlin.math.roundToInt

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

    /**
     * Convert a screen-space divider coordinate into a constrained left-column percentage.
     * A missing/zero-width layout retains the current normalized value.
     */
    fun percentAtPosition(
        positionX: Float,
        containerLeft: Float,
        containerWidth: Float,
        currentPercent: Int,
    ): Int {
        if (containerWidth <= 0f) return normalize(currentPercent)
        return normalize(((positionX - containerLeft) * 100f / containerWidth).roundToInt())
    }
}
