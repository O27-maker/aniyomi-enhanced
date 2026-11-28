package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Pure page distribution for the experimental Split Scroll reader.
 *
 * Items are assigned alternately to left/right columns in the supplied effective
 * sequence. Boundary items are kept full-width and restart the next segment in the
 * left column. Original indices are retained for selection/progress mapping.
 */
internal object SplitScrollPageDistributor {

    internal enum class Column {
        LEFT,
        RIGHT,
        FULL_WIDTH,
    }

    internal data class Placement<T>(
        val originalIndex: Int,
        val item: T,
        val column: Column,
    )

    internal data class Distribution<T>(
        val left: List<Placement<T>>,
        val right: List<Placement<T>>,
        val fullWidth: List<Placement<T>> = emptyList(),
    ) {
        /** All placements, ordered by their original source index. */
        fun inSourceOrder(): List<Placement<T>> =
            (left + right + fullWidth).sortedBy { it.originalIndex }

        /** Resolve a page's index in the effective source sequence to its placement. */
        fun placementForOriginalIndex(originalIndex: Int): Placement<T>? {
            if (originalIndex < 0) return null
            return (left + right + fullWidth).firstOrNull { it.originalIndex == originalIndex }
        }
    }

    fun <T> distribute(
        items: List<T>,
        isBoundary: (T) -> Boolean = { false },
    ): Distribution<T> {
        val left = mutableListOf<Placement<T>>()
        val right = mutableListOf<Placement<T>>()
        val fullWidth = mutableListOf<Placement<T>>()
        var nextColumn = Column.LEFT

        items.forEachIndexed { index, item ->
            if (isBoundary(item)) {
                fullWidth += Placement(index, item, Column.FULL_WIDTH)
                // A new content segment starts in the left column after a boundary.
                nextColumn = Column.LEFT
            } else {
                val placement = Placement(index, item, nextColumn)
                if (nextColumn == Column.LEFT) {
                    left += placement
                    nextColumn = Column.RIGHT
                } else {
                    right += placement
                    nextColumn = Column.LEFT
                }
            }
        }

        return Distribution(left = left, right = right, fullWidth = fullWidth)
    }
}
