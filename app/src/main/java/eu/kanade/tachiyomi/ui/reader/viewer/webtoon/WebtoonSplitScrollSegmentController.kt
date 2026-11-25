package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Binds one immutable Split Scroll segment to the host's two independent RecyclerViews.
 *
 * The controller is deliberately segment-scoped: the authoritative WebtoonAdapter
 * sequence remains untouched, while each column adapter preserves original source
 * indices for selection/progress callbacks. A caller renders the full-width boundary
 * after [onBoundaryReady] and advances only after that boundary has been handled.
 */
internal class WebtoonSplitScrollSegmentController(
    private val viewer: WebtoonViewer,
    private val host: WebtoonSplitScrollHostView,
    private val plan: WebtoonSplitScrollPlan<Any>,
    private val onSelection: (WebtoonSplitScrollSelectionCoordinator.Result) -> Unit,
    private val onBoundaryReady: (WebtoonSplitScrollPlan.Placement<Any>?) -> Unit,
) {

    private val coordinator = WebtoonSplitScrollSelectionCoordinator(plan)

    private var state = coordinator.initialState()
    private var leftBinding: WebtoonSplitScrollHostState.Binding? = null
    private var rightBinding: WebtoonSplitScrollHostState.Binding? = null
    private var leftAdapter: WebtoonSplitScrollColumnAdapter? = null
    private var rightAdapter: WebtoonSplitScrollColumnAdapter? = null
    private var disposed = false

    val segmentIndex: Int
        get() = state.barrier.segmentIndex

    init {
        bindSegment()
    }

    /**
     * Called by the matching RecyclerView scroll listener with that column's
     * end-visible local adapter position and its own scroll offset.
     */
    fun onColumnScrolled(
        column: WebtoonSplitScrollHostState.Column,
        endVisiblePosition: Int,
        scrollOffsetPx: Int,
    ) {
        if (disposed) return

        val adapter = when (column) {
            WebtoonSplitScrollHostState.Column.LEFT -> leftAdapter
            WebtoonSplitScrollHostState.Column.RIGHT -> rightAdapter
        } ?: return
        val binding = when (column) {
            WebtoonSplitScrollHostState.Column.LEFT -> leftBinding
            WebtoonSplitScrollHostState.Column.RIGHT -> rightBinding
        } ?: return

        val sourceIndex = adapter.originalIndexAt(endVisiblePosition) ?: return
        val result = coordinator.onColumnScrolled(
            state = state,
            binding = binding,
            segmentIndex = segmentIndex,
            endVisibleSourceIndex = sourceIndex,
            scrollOffsetPx = scrollOffsetPx,
            reachedSegmentEnd = endVisiblePosition >= adapter.itemCount - 1,
        )
        if (!result.accepted) return

        state = result.state
        onSelection(result)
        if (result.boundaryReady) {
            onBoundaryReady(plan.segments.getOrNull(segmentIndex)?.boundaryAfter)
        }
    }

    /**
     * Advance after the caller has displayed/handled the full-width boundary.
     * Returns false when the current segment is the final segment.
     */
    fun advancePastBoundary(): Boolean {
        if (disposed) return false
        val next = coordinator.advancePastBoundary(state) ?: return false
        detachBindings()
        state = next
        bindSegment()
        return true
    }

    /** Invalidate callbacks and detach both column adapters before host teardown. */
    fun dispose() {
        if (disposed) return
        disposed = true
        detachBindings()
        host.setColumnAdapters(null, null)
        leftAdapter = null
        rightAdapter = null
    }

    private fun bindSegment() {
        val segment = plan.segments.getOrNull(segmentIndex) ?: return

        val leftAttach = state.host.attach(WebtoonSplitScrollHostState.Column.LEFT)
        state = state.copy(host = leftAttach.first)
        leftBinding = leftAttach.second

        val rightAttach = state.host.attach(WebtoonSplitScrollHostState.Column.RIGHT)
        state = state.copy(host = rightAttach.first)
        rightBinding = rightAttach.second

        leftAdapter = WebtoonSplitScrollColumnAdapter(
            viewer = viewer,
            recycler = host.leftRecycler,
            column = WebtoonSplitScrollPlan.Column.LEFT,
            placements = segment.left,
        )
        rightAdapter = WebtoonSplitScrollColumnAdapter(
            viewer = viewer,
            recycler = host.rightRecycler,
            column = WebtoonSplitScrollPlan.Column.RIGHT,
            placements = segment.right,
        )
        host.setColumnAdapters(leftAdapter, rightAdapter)

        coordinator.readyBoundary(state)?.let { result ->
            state = result.state
            onSelection(result)
            onBoundaryReady(segment.boundaryAfter)
        }
    }

    private fun detachBindings() {
        state = state.copy(
            host = state.host
                .detach(WebtoonSplitScrollHostState.Column.LEFT)
                .detach(WebtoonSplitScrollHostState.Column.RIGHT),
        )
        leftBinding = null
        rightBinding = null
    }
}
