package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.WebtoonLayoutManager

/**
 * Bridges independent column scroll events to the source-index-aware selection coordinator.
 *
 * The bridge deliberately does not synchronize scroll positions or call ReaderActivity itself.
 * Its owner receives accepted selections and decides when/how to forward them to reader progress.
 * The two adapters remain projections of the authoritative WebtoonAdapter.items sequence.
 */
internal class WebtoonSplitScrollScrollBridge(
    private val plan: WebtoonSplitScrollPlan<Any>,
    private val leftRecycler: WebtoonRecyclerView,
    private val rightRecycler: WebtoonRecyclerView,
    private val leftAdapter: WebtoonSplitScrollColumnAdapter,
    private val rightAdapter: WebtoonSplitScrollColumnAdapter,
    private val onSelection: (WebtoonSplitScrollSelectionCoordinator.Result) -> Unit,
    private val sourceItems: () -> List<Any>,
    initialSegmentIndex: Int = 0,
) {

    private val coordinator = WebtoonSplitScrollSelectionCoordinator(plan)

    private var state = coordinator.initialState(initialSegmentIndex)
    private var invalidated = false
    private lateinit var leftBinding: WebtoonSplitScrollHostState.Binding
    private lateinit var rightBinding: WebtoonSplitScrollHostState.Binding

    private val leftListener = createListener(
        recycler = leftRecycler,
        adapter = leftAdapter,
        binding = WebtoonSplitScrollHostState.Column.LEFT,
    )

    private val rightListener = createListener(
        recycler = rightRecycler,
        adapter = rightAdapter,
        binding = WebtoonSplitScrollHostState.Column.RIGHT,
    )

    init {
        val left = state.host.attach(WebtoonSplitScrollHostState.Column.LEFT)
        state = state.copy(host = left.first)
        leftBinding = left.second

        val right = state.host.attach(WebtoonSplitScrollHostState.Column.RIGHT)
        state = state.copy(host = right.first)
        rightBinding = right.second

        if (!plan.matchesSource(sourceItems())) {
            invalidate()
            return
        }

        leftRecycler.addOnScrollListener(leftListener)
        rightRecycler.addOnScrollListener(rightListener)

        coordinator.readyBoundary(state)?.let { result ->
            state = result.state
            onSelection(result)
        }
    }

    /**
     * Advance to the next segment after the owner has displayed/handled the full-width boundary.
     * The owner must submit the next segment's placements to both column adapters afterwards.
     */
    fun advancePastBoundary(): Boolean {
        if (!isSourceCurrent()) return false
        val next = coordinator.advancePastBoundary(state) ?: return false
        state = next
        coordinator.readyBoundary(state)?.let { result ->
            state = result.state
            onSelection(result)
        }
        return true
    }

    /** Invalidate delayed callbacks before the host or either column is discarded. */
    fun dispose() {
        invalidate()
    }

    /**
     * A source mutation makes every original index in this projection unsafe.
     * Stop both listeners and let the owner build a fresh plan and host from the
     * new authoritative adapter snapshot; never attempt an in-place index remap.
     */
    private fun isSourceCurrent(): Boolean {
        if (invalidated) return false
        if (plan.matchesSource(sourceItems())) return true
        invalidate()
        return false
    }

    private fun invalidate() {
        if (invalidated) return
        invalidated = true
        leftRecycler.removeOnScrollListener(leftListener)
        rightRecycler.removeOnScrollListener(rightListener)
        state = state.copy(
            host = state.host
                .detach(WebtoonSplitScrollHostState.Column.LEFT)
                .detach(WebtoonSplitScrollHostState.Column.RIGHT),
        )
    }

    private fun createListener(
        recycler: RecyclerView,
        adapter: WebtoonSplitScrollColumnAdapter,
        binding: WebtoonSplitScrollHostState.Column,
    ): RecyclerView.OnScrollListener =
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!isSourceCurrent()) return
                val layoutManager = recycler.layoutManager as? WebtoonLayoutManager ?: return
                val localPosition = layoutManager.findLastEndVisibleItemPosition()
                if (localPosition == RecyclerView.NO_POSITION) return

                val sourceIndex = adapter.originalIndexAt(localPosition) ?: return
                val lifecycleBinding = when (binding) {
                    WebtoonSplitScrollHostState.Column.LEFT -> leftBinding
                    WebtoonSplitScrollHostState.Column.RIGHT -> rightBinding
                }
                val result = coordinator.onColumnScrolled(
                    state = state,
                    binding = lifecycleBinding,
                    segmentIndex = state.barrier.segmentIndex,
                    endVisibleSourceIndex = sourceIndex,
                    scrollOffsetPx = recycler.computeVerticalScrollOffset(),
                    reachedSegmentEnd = localPosition == adapter.itemCount - 1,
                )
                if (result.accepted) {
                    state = result.state
                    onSelection(result)
                }
            }
        }

}
