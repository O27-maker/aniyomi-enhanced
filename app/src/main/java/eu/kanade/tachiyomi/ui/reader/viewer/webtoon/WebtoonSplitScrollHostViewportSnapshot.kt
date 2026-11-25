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
            val segment = plan.segments.getOrNull(segmentIndex) ?: return null
            return WebtoonSplitScrollHostViewportSnapshot(
                segmentIndex = segmentIndex,
                leftItems = segment.left.map { it.item },
                rightItems = segment.right.map { it.item },
                left = state.stateFor(WebtoonSplitScrollHostState.Column.LEFT).toViewport(),
                right = state.stateFor(WebtoonSplitScrollHostState.Column.RIGHT).toViewport(),
            )
        }

        /**
         * Restore only to the same segment index with exactly the same page
         * identities and order. Generation tokens are advanced and attachments
         * cleared so callbacks from the old host cannot mutate the new one.
         */
        fun <T : Any> restore(
            plan: WebtoonSplitScrollPlan<T>,
            snapshot: WebtoonSplitScrollHostViewportSnapshot<T>,
        ): WebtoonSplitScrollHostState? {
            val segment = plan.segments.getOrNull(snapshot.segmentIndex) ?: return null
            if (!segment.left.map { it.item }.sameIdentitySequence(snapshot.leftItems)) return null
            if (!segment.right.map { it.item }.sameIdentitySequence(snapshot.rightItems)) return null

            return WebtoonSplitScrollHostState(
                left = snapshot.left.toColumnState(
                    previousGeneration = snapshot.left.generation,
                ),
                right = snapshot.right.toColumnState(
                    previousGeneration = snapshot.right.generation,
                ),
            )
        }

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
