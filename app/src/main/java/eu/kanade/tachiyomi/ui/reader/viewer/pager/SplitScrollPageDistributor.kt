package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Pure page distribution for the experimental Split Scroll reader.
 *
 * Items are assigned alternately to left/right columns in the supplied effective
 * sequence. Original indices are retained for selection/progress mapping.
 */
internal object SplitScrollPageDistributor {

    internal enum class Column {
        LEFT,
        RIGHT,
    }

    internal data class Placement<T>(
        val originalIndex: Int,
        val item: T,
        val column: Column,
    )

    internal data class Distribution<T>(
        val left: List<Placement<T>>,
        val right: List<Placement<T>>,
    ) {
        /** All placements, ordered by their original source index. */
        fun inSourceOrder(): List<Placement<T>> =
            (left + right).sortedBy { it.originalIndex }

        /** Resolve a page's index in the effective source sequence to its placement. */
        fun placementForOriginalIndex(originalIndex: Int): Placement<T>? {
            if (originalIndex < 0) return null
            return (left + right).firstOrNull { it.originalIndex == originalIndex }
        }
    }

    fun <T> distribute(items: List<T>): Distribution<T> {
        val left = mutableListOf<Placement<T>>()
        val right = mutableListOf<Placement<T>>()

        items.forEachIndexed { index, item ->
            val placement = Placement(
                originalIndex = index,
                item = item,
                column = if (index % 2 == 0) Column.LEFT else Column.RIGHT,
            )
            if (placement.column == Column.LEFT) left += placement else right += placement
        }

        return Distribution(left = left, right = right)
    }
}
