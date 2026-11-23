package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Pure page distribution for the experimental Split Scroll reader.
 *
 * Items are assigned alternately to left and right columns in original order.
 * Original indices are retained so callers can map placements back to chapter
 * pages without losing page identity or progress information.
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
