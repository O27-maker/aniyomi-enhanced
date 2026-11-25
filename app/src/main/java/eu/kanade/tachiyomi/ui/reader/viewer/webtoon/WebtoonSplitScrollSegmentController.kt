package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager

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
    private var boundaryNotified = false

    val segmentIndex: Int
        get() = state.barrier.segmentIndex

    private val leftScrollListener = createScrollListener(WebtoonSplitScrollHostState.Column.LEFT)
    private val rightScrollListener = createScrollListener(WebtoonSplitScrollHostState.Column.RIGHT)

    init {
        bindSegment()
        host.leftRecycler.addOnScrollListener(leftScrollListener)
        host.rightRecycler.addOnScrollListener(rightScrollListener)
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
        if (result.boundaryReady && !boundaryNotified) {
            boundaryNotified = true
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
        // Remove the full-width transition before attaching the next segment's columns.
        host.hideBoundary()
        detachBindings()
        state = next
        bindSegment()
        return true
    }


    /**
     * Position the active segment at an original reader index without confusing
     * a column-local adapter position with the authoritative source sequence.
     */
    fun moveToSourceIndex(sourceIndex: Int): Boolean {
        if (disposed) return false
        val segment = plan.segments.getOrNull(segmentIndex) ?: return false
        val leftPosition = segment.left.indexOfFirst { it.originalIndex == sourceIndex }
        if (leftPosition >= 0) {
            (host.leftRecycler.layoutManager as? WebtoonLayoutManager)
                ?.scrollToPositionWithOffset(leftPosition, 0)
            return true
        }
        val rightPosition = segment.right.indexOfFirst { it.originalIndex == sourceIndex }
        if (rightPosition >= 0) {
            (host.rightRecycler.layoutManager as? WebtoonLayoutManager)
                ?.scrollToPositionWithOffset(rightPosition, 0)
            return true
        }
        return false
    }

    /** Invalidate callbacks and detach both column adapters before host teardown. */
    fun dispose() {
        if (disposed) return
        disposed = true
        host.leftRecycler.removeOnScrollListener(leftScrollListener)
        host.rightRecycler.removeOnScrollListener(rightScrollListener)
        detachBindings()
        host.setColumnAdapters(null, null)
        leftAdapter = null
        rightAdapter = null
    }

    private fun bindSegment() {
        boundaryNotified = false
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
            if (!boundaryNotified) {
                boundaryNotified = true
                onBoundaryReady(segment.boundaryAfter)
            }
        }
    }


    /**
     * Each RecyclerView reports only its own end-visible row and viewport offset.
     * No scroll position is copied to the opposite column.
     */
    private fun createScrollListener(
        column: WebtoonSplitScrollHostState.Column,
    ) = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val layoutManager = recyclerView.layoutManager as? WebtoonLayoutManager ?: return
            val endVisiblePosition = layoutManager.findLastEndVisibleItemPosition()
            if (endVisiblePosition == RecyclerView.NO_POSITION) return
            val offset = layoutManager.findViewByPosition(endVisiblePosition)?.top?.coerceAtLeast(0) ?: 0
            onColumnScrolled(column, endVisiblePosition, offset)
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
