package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Pure gate for advancing past a Split Scroll segment's full-width boundary.
 *
 * Empty columns are considered complete immediately. Non-empty columns scroll
 * independently and must each report reaching their segment end. Stale callbacks
 * from another segment are ignored.
 */
internal class WebtoonSplitScrollBarrierCoordinator<T>(
    private val plan: WebtoonSplitScrollPlan<T>,
) {
    internal data class State(
        val segmentIndex: Int,
        val leftAtEnd: Boolean = false,
        val rightAtEnd: Boolean = false,
    ) {
        val bothColumnsAtEnd: Boolean
            get() = leftAtEnd && rightAtEnd
    }

    fun initialState(segmentIndex: Int = 0): State {
        val safeIndex = segmentIndex.coerceIn(0, (plan.segments.size - 1).coerceAtLeast(0))
        val segment = plan.segments.getOrNull(safeIndex)
        return State(
            segmentIndex = safeIndex,
            leftAtEnd = segment?.left.isNullOrEmpty(),
            rightAtEnd = segment?.right.isNullOrEmpty(),
        )
    }

    fun markColumnAtEnd(
        state: State,
        callbackSegmentIndex: Int,
        column: WebtoonSplitScrollPlan.Column,
    ): State {
        if (callbackSegmentIndex != state.segmentIndex) return state
        return when (column) {
            WebtoonSplitScrollPlan.Column.LEFT -> state.copy(leftAtEnd = true)
            WebtoonSplitScrollPlan.Column.RIGHT -> state.copy(rightAtEnd = true)
            WebtoonSplitScrollPlan.Column.FULL_WIDTH -> state
        }
    }

    fun isBoundaryReady(state: State): Boolean {
        val segment = plan.segments.getOrNull(state.segmentIndex) ?: return false
        return segment.boundaryAfter != null && state.bothColumnsAtEnd
    }

    fun advance(state: State): State? {
        if (!isBoundaryReady(state)) return null
        val nextIndex = state.segmentIndex + 1
        if (nextIndex !in plan.segments.indices) return null
        return initialState(nextIndex)
    }
}
