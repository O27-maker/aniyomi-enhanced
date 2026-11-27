package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Coordinates independent column scroll callbacks without synchronizing either column.
 *
 * Every accepted callback is translated through the active segment plan to an original
 * WebtoonAdapter.items index. Lifecycle bindings reject callbacks from detached/recreated
 * column views. A chapter boundary becomes selectable only after both columns finish.
 */
internal class WebtoonSplitScrollSelectionCoordinator(
    private val plan: WebtoonSplitScrollPlan<Any>,
) {
    internal data class State(
        val host: WebtoonSplitScrollHostState = WebtoonSplitScrollHostState(),
        val barrier: WebtoonSplitScrollBarrierCoordinator.State,
        val selectedSourceIndex: Int? = null,
    )

    internal data class Result(
        val state: State,
        val selection: WebtoonSplitScrollSelection.Selection<Any>?,
        val boundaryReady: Boolean,
        val accepted: Boolean,
    )

    private val barriers = WebtoonSplitScrollBarrierCoordinator(plan)

    fun initialState(segmentIndex: Int = 0): State =
        State(barrier = barriers.initialState(segmentIndex))

    /**
     * Process one column's end-visible item. reachedSegmentEnd must be based on
     * that column's own last item in the active segment, not the other column.
     */
    fun onColumnScrolled(
        state: State,
        binding: WebtoonSplitScrollHostState.Binding,
        segmentIndex: Int,
        endVisibleSourceIndex: Int,
        scrollOffsetPx: Int,
        reachedSegmentEnd: Boolean,
    ): Result {
        if (segmentIndex != state.barrier.segmentIndex) return rejected(state)

        val hostColumn = when (binding.column) {
            WebtoonSplitScrollHostState.Column.LEFT -> WebtoonSplitScrollPlan.Column.LEFT
            WebtoonSplitScrollHostState.Column.RIGHT -> WebtoonSplitScrollPlan.Column.RIGHT
        }
        val columnState = state.host.stateFor(binding.column)
        if (!columnState.attached || columnState.generation != binding.generation) return rejected(state)

        val selection = WebtoonSplitScrollSelection.fromColumn(
            plan = plan,
            callbackSegmentIndex = segmentIndex,
            column = hostColumn,
            endVisibleSourceIndex = endVisibleSourceIndex,
        ) ?: return rejected(state)

        val updatedHost = state.host.recordScroll(binding, scrollOffsetPx)
        val updatedBarrier = if (reachedSegmentEnd) {
            barriers.markColumnAtEnd(state.barrier, segmentIndex, hostColumn)
        } else {
            barriers.markColumnNotAtEnd(state.barrier, segmentIndex, hostColumn)
        }
        val boundaryReady = barriers.isBoundaryReady(updatedBarrier)
        val finalSelection = if (boundaryReady) {
            WebtoonSplitScrollSelection.boundary(plan, updatedBarrier) ?: selection
        } else {
            selection
        }
        val updatedState = state.copy(
            host = updatedHost,
            barrier = updatedBarrier,
            selectedSourceIndex = finalSelection.sourceIndex,
        )
        return Result(updatedState, finalSelection, boundaryReady, accepted = true)
    }

    /**
     * Resolve a boundary that is already ready when a segment is initialized.
     * This is important for leading/consecutive boundaries where one or both
     * columns are empty and therefore will never emit a scroll callback.
     */
    fun readyBoundary(state: State): Result? {
        if (!barriers.isBoundaryReady(state.barrier)) return null
        val selection = WebtoonSplitScrollSelection.boundary(plan, state.barrier) ?: return null
        val updatedState = state.copy(selectedSourceIndex = selection.sourceIndex)
        return Result(updatedState, selection, boundaryReady = true, accepted = true)
    }

    /**
     * Resolve an explicit page navigation through the active segment's source identity.
     * RecyclerView may not dispatch a scroll callback when the requested page is already
     * at the target offset, so navigation must update progress synchronously as well.
     */
    fun selectSourceIndex(state: State, sourceIndex: Int): Result? {
        val segment = plan.segments.getOrNull(state.barrier.segmentIndex) ?: return null
        val placement = (segment.left + segment.right).firstOrNull { it.originalIndex == sourceIndex }
            ?: return null
        val selection = WebtoonSplitScrollSelection.Selection(
            sourceIndex = placement.originalIndex,
            item = placement.item,
            isBoundary = false,
        )
        val updatedState = state.copy(selectedSourceIndex = sourceIndex)
        return Result(updatedState, selection, boundaryReady = false, accepted = true)
    }

    /** Mark both independent columns complete for an explicit transition-page navigation. */
    fun selectBoundary(state: State): Result? {
        val segmentIndex = state.barrier.segmentIndex
        val left = barriers.markColumnAtEnd(
            state.barrier,
            segmentIndex,
            WebtoonSplitScrollPlan.Column.LEFT,
        )
        val ready = barriers.markColumnAtEnd(
            left,
            segmentIndex,
            WebtoonSplitScrollPlan.Column.RIGHT,
        )
        val updatedState = state.copy(barrier = ready)
        return readyBoundary(updatedState)
    }

    /** Advance only after the active segment's full-width boundary is ready. */
    fun advancePastBoundary(state: State): State? {
        val nextBarrier = barriers.advance(state.barrier) ?: return null
        return state.copy(
            barrier = nextBarrier,
            selectedSourceIndex = null,
        )
    }

    private fun rejected(state: State) =
        Result(state, selection = null, boundaryReady = barriers.isBoundaryReady(state.barrier), accepted = false)
}
