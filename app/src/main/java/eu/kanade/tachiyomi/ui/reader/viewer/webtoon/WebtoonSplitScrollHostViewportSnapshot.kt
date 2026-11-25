package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Viewport state captured for one exact Split Scroll segment.
 *
 * Scroll offsets are meaningful only while the segment's two ordered page
 * sequences remain identical. This snapshot therefore carries identity lists
 * and refuses to restore into a segment whose contents or order changed.
 */
internal data class WebtoonSplitScrollHostViewportSnapshot<T : Any>(
    val segmentIndex: Int,
    val leftItems: List<T>,
    val rightItems: List<T>,
    val boundaryBefore: T?,
    val boundaryAfter: T?,
    val left: Viewport,
    val right: Viewport,
) {
    internal data class Viewport(
        val widthPercent: Int,
        val zoom: Float,
        val scrollOffsetPx: Int,
        val generation: Long,
    )

    companion object {
        fun <T : Any> capture(
            plan: WebtoonSplitScrollPlan<T>,
            segmentIndex: Int,
            state: WebtoonSplitScrollHostState,
        ): WebtoonSplitScrollHostViewportSnapshot<T>? {
            if (!plan.hasValidProjection()) return null
            val segment = plan.segments.getOrNull(segmentIndex) ?: return null
            return WebtoonSplitScrollHostViewportSnapshot(
                segmentIndex = segmentIndex,
                leftItems = segment.left.map { it.item },
                rightItems = segment.right.map { it.item },
                boundaryBefore = plan.segments.getOrNull(segmentIndex - 1)?.boundaryAfter?.item,
                boundaryAfter = segment.boundaryAfter?.item,
                left = state.stateFor(WebtoonSplitScrollHostState.Column.LEFT).toViewport(),
                right = state.stateFor(WebtoonSplitScrollHostState.Column.RIGHT).toViewport(),
            )
        }

        /**
         * Resolve a segment by its exact page identities and adjacent chapter
         * boundary identities. The segment may move when earlier chapters gain
         * or lose pages/segments, but offsets are never carried across a changed
         * segment or a replaced boundary.
         */
        fun <T : Any> resolveSegmentIndex(
            plan: WebtoonSplitScrollPlan<T>,
            snapshot: WebtoonSplitScrollHostViewportSnapshot<T>,
        ): Int? {
            if (!plan.hasValidProjection()) return null
            val matches = plan.segments.mapIndexedNotNull { index, segment ->
                val boundaryBefore = plan.segments.getOrNull(index - 1)?.boundaryAfter?.item
                val boundaryAfter = segment.boundaryAfter?.item
                if (
                    boundaryBefore === snapshot.boundaryBefore &&
                    boundaryAfter === snapshot.boundaryAfter &&
                    segment.left.map { it.item }.sameIdentitySequence(snapshot.leftItems) &&
                    segment.right.map { it.item }.sameIdentitySequence(snapshot.rightItems)
                ) index else null
            }
            return matches.singleOrNull()
        }

        /**
         * Restore only when one uniquely matching segment is found. Generation
         * tokens advance and attachments clear so old-host callbacks are rejected.
         */
        fun <T : Any> restore(
            plan: WebtoonSplitScrollPlan<T>,
            snapshot: WebtoonSplitScrollHostViewportSnapshot<T>,
        ): WebtoonSplitScrollHostState? {
            resolveSegmentIndex(plan, snapshot) ?: return null
            return WebtoonSplitScrollHostState(
                left = snapshot.left.toColumnState(snapshot.left.generation),
                right = snapshot.right.toColumnState(snapshot.right.generation),
            )
        }
        private fun <T : Any> WebtoonSplitScrollPlan<T>.hasValidProjection(): Boolean =
            matchesSource(inSourceOrder().map { it.item })

        private fun WebtoonSplitScrollHostState.ColumnState.toViewport() =
            Viewport(widthPercent, zoom, scrollOffsetPx, generation)

        private fun Viewport.toColumnState(previousGeneration: Long) =
            WebtoonSplitScrollHostState.ColumnState(
                widthPercent = widthPercent.coerceIn(
                    WebtoonSplitScrollHostState.MIN_WIDTH_PERCENT,
                    WebtoonSplitScrollHostState.MAX_WIDTH_PERCENT,
                ),
                zoom = zoom.coerceIn(
                    WebtoonSplitScrollHostState.MIN_ZOOM,
                    WebtoonSplitScrollHostState.MAX_ZOOM,
                ),
                scrollOffsetPx = scrollOffsetPx.coerceAtLeast(0),
                generation = previousGeneration + 1,
                attached = false,
            )

        private fun <T : Any> List<T>.sameIdentitySequence(other: List<T>): Boolean =
            size == other.size && indices.all { this[it] === other[it] }
    }
}
