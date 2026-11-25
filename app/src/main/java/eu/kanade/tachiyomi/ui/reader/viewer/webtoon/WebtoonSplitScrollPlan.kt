package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.viewer.pager.SplitScrollPageDistributor

/**
 * Immutable projection for the Webtoon Split Scroll host.
 *
 * The Webtoon adapter's effective item list remains authoritative. This plan only
 * describes which source items belong in each independently scrolling column and
 * which items must be rendered across the full host width.
 */
internal data class WebtoonSplitScrollPlan<T>(
    val left: List<SplitScrollPageDistributor.Placement<T>>,
    val right: List<SplitScrollPageDistributor.Placement<T>>,
    val fullWidth: List<SplitScrollPageDistributor.Placement<T>>,
    val sourceSize: Int,
) {
    /** Returns a placement using the original WebtoonAdapter.items index. */
    fun placementForSourceIndex(index: Int): SplitScrollPageDistributor.Placement<T>? {
        if (index !in 0 until sourceSize) return null
        return (left + right + fullWidth).firstOrNull { it.originalIndex == index }
    }

    /** Ordered projection for progress, selection, and adapter-diff coordination. */
    fun inSourceOrder(): List<SplitScrollPageDistributor.Placement<T>> =
        (left + right + fullWidth).sortedBy { it.originalIndex }

    companion object {
        /**
         * Builds a plan without changing [items]. Boundary items (chapter transitions)
         * are full-width barriers; ordinary items alternate columns within each segment.
         */
        fun <T> create(
            items: List<T>,
            isBoundary: (T) -> Boolean,
        ): WebtoonSplitScrollPlan<T> {
            val distribution = SplitScrollPageDistributor.distribute(items, isBoundary)
            return WebtoonSplitScrollPlan(
                left = distribution.left,
                right = distribution.right,
                fullWidth = distribution.fullWidth,
                sourceSize = items.size,
            )
        }
    }
}
