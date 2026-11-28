package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * A validated, view-agnostic instruction for restoring selection after a Split
 * Scroll host rebuild. Android view operations remain the host owner's job.
 */
internal data class WebtoonSplitScrollHostRestore<T : Any>(
    val segmentIndex: Int,
    val sourceIndex: Int,
    val item: T,
    val destination: Destination,
) {
    internal sealed class Destination {
        data class Column(
            val column: WebtoonSplitScrollPlan.Column,
            val position: Int,
        ) : Destination()

        object FullWidthBoundary : Destination()
    }

    companion object {
        /**
         * Convert a rebuild result into an actionable destination only when its
         * target still belongs to that exact plan by index and object identity.
         * A null result means the owner must use the reader's normal fallback.
         */
        fun <T : Any> from(
            rebuild: WebtoonSplitScrollRebuildTargetResolver.Rebuild<T>,
        ): WebtoonSplitScrollHostRestore<T>? {
            val target = rebuild.target ?: return null
            val placement = rebuild.plan.placementForSourceIndex(target.sourceIndex) ?: return null
            if (placement.item !== target.item || placement.column != target.column) return null

            val destination = when (target.column) {
                WebtoonSplitScrollPlan.Column.LEFT,
                WebtoonSplitScrollPlan.Column.RIGHT,
                -> {
                    val position = target.columnPosition ?: return null
                    val segment = rebuild.plan.segments.getOrNull(target.segmentIndex) ?: return null
                    val columnItems = if (target.column == WebtoonSplitScrollPlan.Column.LEFT) {
                        segment.left
                    } else {
                        segment.right
                    }
                    if (columnItems.getOrNull(position)?.item !== target.item) return null
                    Destination.Column(target.column, position)
                }
                WebtoonSplitScrollPlan.Column.FULL_WIDTH -> {
                    if (!target.isBoundary || target.columnPosition != null) return null
                    val segment = rebuild.plan.segments.getOrNull(target.segmentIndex) ?: return null
                    if (segment.boundaryAfter?.item !== target.item) return null
                    Destination.FullWidthBoundary
                }
            }

            return WebtoonSplitScrollHostRestore(
                segmentIndex = target.segmentIndex,
                sourceIndex = target.sourceIndex,
                item = target.item,
                destination = destination,
            )
        }
    }
}
