package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Normalized horizontal bounds for pages inside a Spread display item.
 *
 * This is intentionally presentation-only: page order is supplied by the effective
 * reader sequence (which PagerViewerAdapter already reverses for R2L), and this
 * helper never changes page identity or navigation indices.
 */
internal object SpreadLayoutGeometry {
    internal data class Slot(val left: Float, val right: Float) {
        val width: Float get() = right - left
    }

    fun single(): Slot = Slot(0f, 1f)

    fun pair(isR2L: Boolean): List<Slot> = if (isR2L) {
        listOf(Slot(0.5f, 1f), Slot(0f, 0.5f))
    } else {
        listOf(Slot(0f, 0.5f), Slot(0.5f, 1f))
    }
}
