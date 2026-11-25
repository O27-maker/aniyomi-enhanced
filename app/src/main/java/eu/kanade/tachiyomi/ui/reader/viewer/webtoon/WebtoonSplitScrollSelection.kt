package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Resolves reader selection from a column's own scroll callback back to the
 * authoritative WebtoonAdapter.items sequence.
 *
 * The callback's column is significant: the most recently scrolled column supplies
 * the active page. This avoids guessing from the numerically greatest index across
 * two independently moving columns, which could select a page the user has not
 * reached in the column they are currently reading.
 */
internal object WebtoonSplitScrollSelection {

    internal data class Selection<T>(
        val sourceIndex: Int,
        val item: T,
        val isBoundary: Boolean,
    )

    /**
     * Resolve the end-visible item reported by one column.
     * Returns null for stale segment callbacks, wrong-column indices, and invalid indices.
     */
    fun <T> fromColumn(
        plan: WebtoonSplitScrollPlan<T>,
        callbackSegmentIndex: Int,
        column: WebtoonSplitScrollPlan.Column,
        endVisibleSourceIndex: Int,
    ): Selection<T>? {
        if (column == WebtoonSplitScrollPlan.Column.FULL_WIDTH) return null
        val segment = plan.segments.getOrNull(callbackSegmentIndex) ?: return null
        val placements = when (column) {
            WebtoonSplitScrollPlan.Column.LEFT -> segment.left
            WebtoonSplitScrollPlan.Column.RIGHT -> segment.right
            WebtoonSplitScrollPlan.Column.FULL_WIDTH -> return null
        }
        val placement = placements.firstOrNull { it.originalIndex == endVisibleSourceIndex } ?: return null
        return Selection(
            sourceIndex = placement.originalIndex,
            item = placement.item,
            isBoundary = false,
        )
    }

    /** A chapter transition is selectable only after both columns finish its segment. */
    fun <T> boundary(
        plan: WebtoonSplitScrollPlan<T>,
        state: WebtoonSplitScrollBarrierCoordinator.State,
    ): Selection<T>? {
        if (!WebtoonSplitScrollBarrierCoordinator(plan).isBoundaryReady(state)) return null
        val boundary = plan.segments.getOrNull(state.segmentIndex)?.boundaryAfter ?: return null
        return Selection(
            sourceIndex = boundary.originalIndex,
            item = boundary.item,
            isBoundary = true,
        )
    }
}
