package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Owns the immutable source snapshot used by the paged Split Scroll host.
 *
 * The controller never changes the reader adapter's source sequence. It resolves
 * source identities to segment/column coordinates and rejects stale snapshots,
 * allowing the host to rebuild after chapter or inserted-page mutations.
 */
internal class PagerSplitScrollController<T>(
    private val isBoundary: (T) -> Boolean,
    private val firstColumn: PagerSplitScrollPlan.Column = PagerSplitScrollPlan.Column.LEFT,
) {
    data class SegmentSnapshot<T>(
        val index: Int,
        val left: List<PagerSplitScrollPlan.Placement<T>>,
        val right: List<PagerSplitScrollPlan.Placement<T>>,
        val boundaryAfter: PagerSplitScrollPlan.Placement<T>?,
    )

    private var sourceSnapshot: List<T> = emptyList()
    private var plan: PagerSplitScrollPlan<T>? = null

    val isActive: Boolean
        get() = plan != null

    val segmentCount: Int
        get() = plan?.segments?.size ?: 0

    fun rebuild(source: List<T>): Boolean {
        val nextSource = source.toList()
        val nextPlan = PagerSplitScrollPlan.create(
            items = nextSource,
            isBoundary = isBoundary,
            firstColumn = firstColumn,
        )
        if (!nextPlan.matchesSource(nextSource)) return false
        sourceSnapshot = nextSource
        plan = nextPlan
        return true
    }

    fun clear() {
        sourceSnapshot = emptyList()
        plan = null
    }

    fun matchesSource(source: List<T>): Boolean =
        sourceSnapshot.size == source.size &&
            sourceSnapshot.indices.all { sourceSnapshot[it] === source[it] } &&
            plan?.matchesSource(source) == true

    fun segmentAtSourceIndex(sourceIndex: Int): SegmentSnapshot<T>? {
        val current = plan ?: return null
        val segmentIndex = current.segmentIndexForSourceIndex(sourceIndex)
        if (segmentIndex < 0) return null
        val segment = current.segments.getOrNull(segmentIndex) ?: return null
        return SegmentSnapshot(
            index = segmentIndex,
            left = segment.left,
            right = segment.right,
            boundaryAfter = segment.boundaryAfter,
        )
    }

    fun columnPositionForSourceIndex(sourceIndex: Int): Pair<PagerSplitScrollPlan.Column, Int>? =
        plan?.columnPositionForSourceIndex(sourceIndex)

    fun sourceItemAt(sourceIndex: Int): T? =
        sourceSnapshot.getOrNull(sourceIndex)

    fun sourceIndexOf(item: T): Int =
        sourceSnapshot.indexOfFirst { it === item }

    fun boundaryAfterSegment(segmentIndex: Int): PagerSplitScrollPlan.Placement<T>? =
        plan?.segments?.getOrNull(segmentIndex)?.boundaryAfter

    fun nextSegmentIndex(segmentIndex: Int): Int? =
        (segmentIndex + 1).takeIf { plan?.segments?.indices?.contains(it) == true }

    fun previousSegmentIndex(segmentIndex: Int): Int? =
        (segmentIndex - 1).takeIf { plan?.segments?.indices?.contains(it) == true }

    fun sourceSize(): Int = sourceSnapshot.size
}
