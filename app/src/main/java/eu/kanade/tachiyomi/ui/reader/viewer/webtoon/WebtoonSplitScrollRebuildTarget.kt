package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Identity-based restoration target for rebuilding a Split Scroll host after the
 * authoritative WebtoonAdapter.items sequence changes.
 *
 * A source index from the old plan is deliberately not accepted: insertions and
 * removals can shift every later index. The owner must resolve the currently
 * selected item against the newly-created plan.
 */
internal data class WebtoonSplitScrollRebuildTarget<T>(
    val segmentIndex: Int,
    val sourceIndex: Int,
    val item: T,
    val column: WebtoonSplitScrollPlan.Column,
    val columnPosition: Int?,
) {
    val isBoundary: Boolean
        get() = column == WebtoonSplitScrollPlan.Column.FULL_WIDTH
}

internal object WebtoonSplitScrollRebuildTargetResolver {

    /**
     * Restore only when the exact selected item instance survives in the new plan.
     * If it was removed/replaced, return null so the owner can use the reader's
     * established fallback rather than guessing from a stale index.
     */
    fun <T : Any> resolve(
        plan: WebtoonSplitScrollPlan<T>,
        selectedItem: T?,
    ): WebtoonSplitScrollRebuildTarget<T>? {
        selectedItem ?: return null

        plan.segments.forEachIndexed { segmentIndex, segment ->
            segment.left.forEachIndexed { columnPosition, placement ->
                if (placement.item === selectedItem) {
                    return WebtoonSplitScrollRebuildTarget(
                        segmentIndex = segmentIndex,
                        sourceIndex = placement.originalIndex,
                        item = placement.item,
                        column = WebtoonSplitScrollPlan.Column.LEFT,
                        columnPosition = columnPosition,
                    )
                }
            }
            segment.right.forEachIndexed { columnPosition, placement ->
                if (placement.item === selectedItem) {
                    return WebtoonSplitScrollRebuildTarget(
                        segmentIndex = segmentIndex,
                        sourceIndex = placement.originalIndex,
                        item = placement.item,
                        column = WebtoonSplitScrollPlan.Column.RIGHT,
                        columnPosition = columnPosition,
                    )
                }
            }
            val boundary = segment.boundaryAfter
            if (boundary?.item === selectedItem) {
                return WebtoonSplitScrollRebuildTarget(
                    segmentIndex = segmentIndex,
                    sourceIndex = boundary.originalIndex,
                    item = boundary.item,
                    column = WebtoonSplitScrollPlan.Column.FULL_WIDTH,
                    columnPosition = null,
                )
            }
        }
        return null
    }
}
