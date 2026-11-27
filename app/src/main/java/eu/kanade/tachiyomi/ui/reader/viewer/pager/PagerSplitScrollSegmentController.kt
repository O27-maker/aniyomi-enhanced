package eu.kanade.tachiyomi.ui.reader.viewer.pager

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/**
 * Drives one source-ordered segment in the paged Split Scroll host.
 *
 * The two RecyclerViews keep independent scroll positions. Each column reports its
 * own end-visible source item; a chapter transition is released only after both
 * columns reach their own segment ends (empty columns count as already complete).
 */
internal class PagerSplitScrollSegmentController(
    private val viewer: PagerViewer,
    private val host: PagerSplitScrollHostView,
    private val plan: PagerSplitScrollPlan<Any>,
    private val onSelection: (Int) -> Unit,
    private val onBoundaryReady: (PagerSplitScrollPlan.Placement<Any>?) -> Unit,
) {
    private var segmentIndex = 0
    private var leftAdapter: PagerSplitScrollColumnAdapter? = null
    private var rightAdapter: PagerSplitScrollColumnAdapter? = null
    private var leftAtEnd = false
    private var rightAtEnd = false
    private var boundaryNotified = false
    private var disposed = false

    private val leftListener = createScrollListener(PagerSplitScrollPlan.Column.LEFT)
    private val rightListener = createScrollListener(PagerSplitScrollPlan.Column.RIGHT)

    init {
        host.leftRecycler.addOnScrollListener(leftListener)
        host.rightRecycler.addOnScrollListener(rightListener)
        bindSegment()
    }

    internal data class ViewportAnchor(val page: ReaderPage, val offsetPx: Int)

    internal data class ViewportSnapshot(
        val left: ViewportAnchor?,
        val right: ViewportAnchor?,
    )

    /** Capture each column's own first-visible page and decorated top offset. */
    fun captureViewportSnapshot(): ViewportSnapshot = ViewportSnapshot(
        left = captureColumnViewport(PagerSplitScrollPlan.Column.LEFT),
        right = captureColumnViewport(PagerSplitScrollPlan.Column.RIGHT),
    )

    private fun captureColumnViewport(column: PagerSplitScrollPlan.Column): ViewportAnchor? {
        val recycler = recyclerFor(column)
        val adapter = adapterFor(column) ?: return null
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return null
        val position = manager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return null
        val page = adapter.pageAt(position) ?: return null
        val child = manager.findViewByPosition(position) ?: return null
        return ViewportAnchor(page, manager.getDecoratedTop(child))
    }

    /** Restore both anchors only when they still belong to the same chapter segment. */
    fun restoreViewportSnapshot(snapshot: ViewportSnapshot): Boolean {
        if (disposed) return false
        val leftIndex = snapshot.left?.let { anchor ->
            plan.segments.asSequence().flatMap { it.left.asSequence() }
                .firstOrNull { it.item === anchor.page }?.sourceIndex
        }
        val rightIndex = snapshot.right?.let { anchor ->
            plan.segments.asSequence().flatMap { it.right.asSequence() }
                .firstOrNull { it.item === anchor.page }?.sourceIndex
        }
        val leftSegment = leftIndex?.let(plan::segmentIndexForSourceIndex)
        val rightSegment = rightIndex?.let(plan::segmentIndexForSourceIndex)
        val targetSegment = when {
            leftSegment != null && leftSegment >= 0 && leftSegment == rightSegment -> leftSegment
            leftSegment != null && leftSegment >= 0 && rightSegment == null -> leftSegment
            rightSegment != null && rightSegment >= 0 && leftSegment == null -> rightSegment
            else -> return false
        }
        if (targetSegment != segmentIndex || boundaryNotified) {
            host.hideBoundary()
            segmentIndex = targetSegment
            bindSegment(checkInitialViewport = false)
        }
        restoreColumnViewport(PagerSplitScrollPlan.Column.LEFT, leftIndex, snapshot.left)
        restoreColumnViewport(PagerSplitScrollPlan.Column.RIGHT, rightIndex, snapshot.right)
        host.leftRecycler.post { checkInitialEnd(PagerSplitScrollPlan.Column.LEFT) }
        host.rightRecycler.post { checkInitialEnd(PagerSplitScrollPlan.Column.RIGHT) }
        return true
    }

    private fun restoreColumnViewport(
        column: PagerSplitScrollPlan.Column,
        sourceIndex: Int?,
        anchor: ViewportAnchor?,
    ) {
        if (sourceIndex == null || anchor == null) return
        val position = plan.columnPositionForSourceIndex(sourceIndex) ?: return
        if (position.first != column) return
        val recycler = recyclerFor(column)
        recycler.post {
            if (disposed || segmentIndex != plan.segmentIndexForSourceIndex(sourceIndex)) return@post
            (recycler.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(position.second, anchor.offsetPx)
        }
    }

    fun moveToSourceIndex(sourceIndex: Int): Boolean {
        if (disposed) return false

        // Chapter transitions remain navigable source items. Direct next/previous input
        // should display the same full-width barrier as reaching both column ends.
        val boundarySegment = plan.segments.indexOfFirst {
            it.boundaryAfter?.sourceIndex == sourceIndex
        }
        if (boundarySegment >= 0) {
            if (boundarySegment != segmentIndex || boundaryNotified) {
                host.hideBoundary()
                segmentIndex = boundarySegment
                bindSegment()
            }
            if (!boundaryNotified) {
                val boundary = plan.segments.getOrNull(segmentIndex)?.boundaryAfter
                boundaryNotified = true
                onSelection(sourceIndex)
                onBoundaryReady(boundary)
            }
            return true
        }

        val targetSegment = plan.segmentIndexForSourceIndex(sourceIndex)
        if (targetSegment < 0) return false
        if (targetSegment != segmentIndex || boundaryNotified) {
            // A direct navigation request can move back from a displayed chapter barrier.
            host.hideBoundary()
            segmentIndex = targetSegment
            bindSegment()
        }
        val position = plan.columnPositionForSourceIndex(sourceIndex) ?: return false
        val recycler = when (position.first) {
            PagerSplitScrollPlan.Column.LEFT -> host.leftRecycler
            PagerSplitScrollPlan.Column.RIGHT -> host.rightRecycler
            PagerSplitScrollPlan.Column.FULL_WIDTH -> return false
        }
        (recycler.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(position.second, 0)
        onSelection(sourceIndex)
        return true
    }

    fun pageAt(column: PagerSplitScrollPlan.Column, position: Int) =
        when (column) {
            PagerSplitScrollPlan.Column.LEFT -> leftAdapter?.pageAt(position)
            PagerSplitScrollPlan.Column.RIGHT -> rightAdapter?.pageAt(position)
            PagerSplitScrollPlan.Column.FULL_WIDTH -> null
        }

    /** Return the currently attached native page holder for activity callbacks. */
    fun holderFor(page: ReaderPage): PagerPageHolder? {
        val leftPosition = leftAdapter?.let { adapter ->
            (0 until adapter.itemCount).firstOrNull { adapter.pageAt(it) === page }
        }
        if (leftPosition != null) {
            val holder = host.leftRecycler.findViewHolderForAdapterPosition(leftPosition)
                ?.let { it as? PagerSplitScrollColumnAdapter.PageHolder }
            return holder?.pageHolder
        }
        val rightPosition = rightAdapter?.let { adapter ->
            (0 until adapter.itemCount).firstOrNull { adapter.pageAt(it) === page }
        } ?: return null
        val holder = host.rightRecycler.findViewHolderForAdapterPosition(rightPosition)
            ?.let { it as? PagerSplitScrollColumnAdapter.PageHolder }
        return holder?.pageHolder
    }

    fun advancePastBoundary(): Boolean {
        if (disposed || !boundaryNotified) return false
        val next = segmentIndex + 1
        if (next !in plan.segments.indices) return false
        host.hideBoundary()
        segmentIndex = next
        bindSegment()
        return true
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        host.leftRecycler.removeOnScrollListener(leftListener)
        host.rightRecycler.removeOnScrollListener(rightListener)
        host.clearAdapters()
        leftAdapter = null
        rightAdapter = null
    }

    private fun bindSegment(checkInitialViewport: Boolean = true) {
        if (disposed) return
        boundaryNotified = false
        leftAtEnd = false
        rightAtEnd = false
        val segment = plan.segments.getOrNull(segmentIndex) ?: return
        leftAdapter = PagerSplitScrollColumnAdapter(viewer, segment.left)
        rightAdapter = PagerSplitScrollColumnAdapter(viewer, segment.right)
        host.setColumnAdapters(leftAdapter, rightAdapter)
        // A newly entered chapter/segment becomes the active progress anchor immediately,
        // even if RecyclerView's initial layout does not dispatch an onScrolled callback.
        (segment.left + segment.right)
            .minByOrNull { it.sourceIndex }
            ?.sourceIndex
            ?.let(onSelection)
        leftAtEnd = segment.left.isEmpty()
        rightAtEnd = segment.right.isEmpty()
        if (leftAtEnd && rightAtEnd) notifyBoundary(segment.boundaryAfter)
        if (checkInitialViewport) {
            host.leftRecycler.post { checkInitialEnd(PagerSplitScrollPlan.Column.LEFT) }
            host.rightRecycler.post { checkInitialEnd(PagerSplitScrollPlan.Column.RIGHT) }
        }
    }

    private fun checkInitialEnd(column: PagerSplitScrollPlan.Column) {
        if (disposed || boundaryNotified) return
        val recycler = recyclerFor(column)
        val adapter = adapterFor(column) ?: return
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return
        val lastCompletelyVisible = manager.findLastCompletelyVisibleItemPosition()
        if (adapter.itemCount == 0 || lastCompletelyVisible >= adapter.itemCount - 1) {
            markAtEnd(column)
        }
    }

    private fun createScrollListener(column: PagerSplitScrollPlan.Column) =
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (disposed || boundaryNotified) return
                val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val position = manager.findLastVisibleItemPosition()
                val lastCompletelyVisible = manager.findLastCompletelyVisibleItemPosition()
                val adapter = adapterFor(column) ?: return
                if (position == RecyclerView.NO_POSITION) return
                adapter.sourceIndexAt(position)?.let(onSelection)
                if (lastCompletelyVisible >= adapter.itemCount - 1) markAtEnd(column)
                else markNotAtEnd(column)
            }
        }

    private fun markAtEnd(column: PagerSplitScrollPlan.Column) {
        when (column) {
            PagerSplitScrollPlan.Column.LEFT -> leftAtEnd = true
            PagerSplitScrollPlan.Column.RIGHT -> rightAtEnd = true
            PagerSplitScrollPlan.Column.FULL_WIDTH -> return
        }
        if (leftAtEnd && rightAtEnd && !boundaryNotified) {
            notifyBoundary(plan.segments.getOrNull(segmentIndex)?.boundaryAfter)
        }
    }

    private fun markNotAtEnd(column: PagerSplitScrollPlan.Column) {
        when (column) {
            PagerSplitScrollPlan.Column.LEFT -> leftAtEnd = false
            PagerSplitScrollPlan.Column.RIGHT -> rightAtEnd = false
            PagerSplitScrollPlan.Column.FULL_WIDTH -> Unit
        }
    }

    private fun notifyBoundary(boundary: PagerSplitScrollPlan.Placement<Any>?) {
        if (boundaryNotified) return
        boundaryNotified = true
        (boundary?.item as? ChapterTransition)?.let {
            onSelection(boundary.sourceIndex)
        }
        onBoundaryReady(boundary)
    }

    private fun recyclerFor(column: PagerSplitScrollPlan.Column) =
        if (column == PagerSplitScrollPlan.Column.LEFT) host.leftRecycler else host.rightRecycler

    private fun adapterFor(column: PagerSplitScrollPlan.Column) =
        if (column == PagerSplitScrollPlan.Column.LEFT) leftAdapter else rightAdapter
}
