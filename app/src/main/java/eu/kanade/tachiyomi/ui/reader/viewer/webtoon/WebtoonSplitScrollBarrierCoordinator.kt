package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Pure gate for advancing past a Split Scroll segment's full-width boundary.
 *
 * The two columns scroll independently. Reaching the end of one column only marks
 * that side as waiting; the boundary becomes available after both sides reach the
 * end. This prevents a short column from advancing into the next chapter while the
 * other column still contains pages from the current segment.
 *
 * Callers must supply the segment index captured when the scroll callback was
 * produced. Stale callbacks from a previous segment are ignored.
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

    fun initialState(segmentIndex: Int = 0): State =
        State(segmentIndex = segmentIndex.coerceIn(0, (plan.segments.size - 1).coerceAtLeast(0)))

    /**
     * Records that [column] reached the end of [callbackSegmentIndex].
     * A callback from any other segment cannot mutate the active state.
     */
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

    /** True only when a real boundary exists and both independent columns finished. */
    fun isBoundaryReady(state: State): Boolean {
        val segment = plan.segments.getOrNull(state.segmentIndex) ?: return false
        return segment.boundaryAfter != null && state.bothColumnsAtEnd
    }

    /**
     * Moves to the next segment only after its preceding boundary is ready.
     * The returned state resets both arrival flags for the new segment.
     */
    fun advance(state: State): State? {
        if (!isBoundaryReady(state)) return null
        val nextIndex = state.segmentIndex + 1
        if (nextIndex !in plan.segments.indices) return null
        return State(segmentIndex = nextIndex)
    }
}
