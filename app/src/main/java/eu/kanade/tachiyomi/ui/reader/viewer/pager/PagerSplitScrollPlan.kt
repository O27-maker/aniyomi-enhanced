package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Source-preserving two-column projection for paged-reader Split Scroll.
 *
 * The pager adapter's effective sequence is authoritative (including R2L reversal,
 * inserted pages and chapter transitions). This plan only assigns ordinary pages
 * alternately to independently scrollable columns and retains transitions as barriers.
 */
internal data class PagerSplitScrollPlan<T>(
    val segments: List<Segment<T>>,
    val sourceSize: Int,
    val firstColumn: Column,
) {
    internal enum class Column { LEFT, RIGHT, FULL_WIDTH }

    internal data class Placement<T>(
        val sourceIndex: Int,
        val item: T,
        val column: Column,
    )

    internal data class Segment<T>(
        val left: List<Placement<T>>,
        val right: List<Placement<T>>,
        val boundaryAfter: Placement<T>?,
    )

    fun segmentIndexForSourceIndex(index: Int): Int {
        if (index !in 0 until sourceSize) return -1
        return segments.indexOfFirst { segment ->
            segment.left.any { it.sourceIndex == index } ||
                segment.right.any { it.sourceIndex == index }
        }
    }

    fun columnPositionForSourceIndex(index: Int): Pair<Column, Int>? {
        if (index !in 0 until sourceSize) return null
        segments.forEach { segment ->
            val leftPosition = segment.left.indexOfFirst { it.sourceIndex == index }
            if (leftPosition >= 0) return Column.LEFT to leftPosition
            val rightPosition = segment.right.indexOfFirst { it.sourceIndex == index }
            if (rightPosition >= 0) return Column.RIGHT to rightPosition
        }
        return null
    }

    fun placementForSourceIndex(index: Int): Placement<T>? {
        if (index !in 0 until sourceSize) return null
        return segments.asSequence().flatMap { segment ->
            sequence {
                yieldAll(segment.left)
                yieldAll(segment.right)
                segment.boundaryAfter?.let { yield(it) }
            }
        }.firstOrNull { it.sourceIndex == index }
    }

    fun matchesSource(source: List<T>): Boolean {
        if (source.size != sourceSize || segments.isEmpty()) return false
        var expectedIndex = 0
        segments.forEachIndexed { segmentIndex, segment ->
            val last = segmentIndex == segments.lastIndex
            if (last == (segment.boundaryAfter != null)) return false
            val ordered = (segment.left + segment.right).sortedBy { it.sourceIndex }
            var expectedColumn = firstColumn
            ordered.forEach { placement ->
                if (placement.sourceIndex != expectedIndex ||
                    placement.column != expectedColumn ||
                    source.getOrNull(expectedIndex) !== placement.item
                ) return false
                expectedIndex++
                expectedColumn = expectedColumn.other()
            }
            segment.boundaryAfter?.let { boundary ->
                if (boundary.sourceIndex != expectedIndex ||
                    boundary.column != Column.FULL_WIDTH ||
                    source.getOrNull(expectedIndex) !== boundary.item
                ) return false
                expectedIndex++
            }
        }
        return expectedIndex == source.size
    }

    companion object {
        fun <T> create(
            source: List<T>,
            isBoundary: (T) -> Boolean,
            firstColumn: Column,
        ): PagerSplitScrollPlan<T> {
            val result = mutableListOf<Segment<T>>()
            var left = mutableListOf<Placement<T>>()
            var right = mutableListOf<Placement<T>>()
            var next = firstColumn

            fun close(boundary: Placement<T>?) {
                result += Segment(left.toList(), right.toList(), boundary)
                left = mutableListOf()
                right = mutableListOf()
                next = firstColumn
            }

            source.forEachIndexed { index, item ->
                if (isBoundary(item)) {
                    close(Placement(index, item, Column.FULL_WIDTH))
                } else {
                    val placement = Placement(index, item, next)
                    if (next == Column.LEFT) left += placement else right += placement
                    next = next.other()
                }
            }
            close(null)
            return PagerSplitScrollPlan(result, source.size, firstColumn)
        }

        private fun Column.other(): Column = when (this) {
            Column.LEFT -> Column.RIGHT
            Column.RIGHT -> Column.LEFT
            Column.FULL_WIDTH -> error("A boundary cannot be an alternating column")
        }
    }
}
