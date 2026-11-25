package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Immutable, source-index-preserving projection for a future Split Scroll host.
 *
 * Each segment owns two independently scrollable page sequences. A chapter
 * transition is attached to the segment before it as a full-width barrier.
 * Empty segments are retained, so leading and consecutive barriers are not lost.
 *
 * This is a projection only: WebtoonAdapter.items remains the source of truth.
 */
internal data class WebtoonSplitScrollPlan<T>(
    val segments: List<Segment<T>>,
    val sourceSize: Int,
) {
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

    internal data class Segment<T>(
        val left: List<Placement<T>>,
        val right: List<Placement<T>>,
        val boundaryAfter: Placement<T>?,
    )

    /** Resolve an original WebtoonAdapter.items index without depending on display order. */
    fun placementForSourceIndex(index: Int): Placement<T>? {
        if (index !in 0 until sourceSize) return null
        return segments.asSequence()
            .flatMap { segment ->
                sequence {
                    yieldAll(segment.left)
                    yieldAll(segment.right)
                    segment.boundaryAfter?.let { yield(it) }
                }
            }
            .firstOrNull { it.originalIndex == index }
    }

    /**
     * Verify that this projection still describes the exact authoritative source snapshot.
     *
     * Identity is intentional: ReaderPage and ChapterTransition instances are the objects
     * consumed by the active reader. An equal-looking replacement still requires a rebuild.
     */
    fun matchesSource(items: List<T>): Boolean {
        if (items.size != sourceSize) return false
        if (segments.isEmpty()) return false
        var nextSourceIndex = 0
        for ((segmentIndex, segment) in segments.withIndex()) {
            val isLastSegment = segmentIndex == segments.lastIndex
            if (isLastSegment == (segment.boundaryAfter != null)) return false

            var nextColumn = Column.LEFT
            val pagesInSourceOrder = (segment.left + segment.right).sortedBy { it.originalIndex }
            for (placement in pagesInSourceOrder) {
                if (placement.originalIndex != nextSourceIndex) return false
                if (placement.column != nextColumn) return false
                if (items.getOrNull(nextSourceIndex) !== placement.item) return false

                nextSourceIndex++
                nextColumn = if (nextColumn == Column.LEFT) Column.RIGHT else Column.LEFT
            }

            segment.boundaryAfter?.let { boundary ->
                if (boundary.originalIndex != nextSourceIndex) return false
                if (boundary.column != Column.FULL_WIDTH) return false
                if (items.getOrNull(nextSourceIndex) !== boundary.item) return false
                nextSourceIndex++
            }
        }
        return nextSourceIndex == items.size
    }

    /** A stable source-order view for progress and selection mapping. */
    fun inSourceOrder(): List<Placement<T>> =
        segments.flatMap { segment ->
            buildList {
                addAll(segment.left)
                addAll(segment.right)
                segment.boundaryAfter?.let(::add)
            }
        }.sortedBy { it.originalIndex }

    companion object {
        /**
         * Pages alternate left/right inside each segment. A boundary closes the
         * current segment, is retained as a full-width item, and resets the next
         * segment to the left column. The trailing segment is always retained.
         */
        fun <T> create(
            items: List<T>,
            isBoundary: (T) -> Boolean,
        ): WebtoonSplitScrollPlan<T> {
            val segments = mutableListOf<Segment<T>>()
            var left = mutableListOf<Placement<T>>()
            var right = mutableListOf<Placement<T>>()
            var nextColumn = Column.LEFT

            fun closeSegment(boundary: Placement<T>?) {
                segments += Segment(
                    left = left.toList(),
                    right = right.toList(),
                    boundaryAfter = boundary,
                )
                left = mutableListOf()
                right = mutableListOf()
                nextColumn = Column.LEFT
            }

            items.forEachIndexed { index, item ->
                if (isBoundary(item)) {
                    closeSegment(Placement(index, item, Column.FULL_WIDTH))
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

            closeSegment(null)
            return WebtoonSplitScrollPlan(segments = segments, sourceSize = items.size)
        }
    }
}
