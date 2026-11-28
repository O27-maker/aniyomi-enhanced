package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Atomic, view-independent snapshot of the selected item and the viewport segment
 * that contained it. Selection and offsets are restored together only when the
 * same item and exact segment identity survive a rebuild.
 */
internal data class WebtoonSplitScrollHostRebuildSnapshot<T : Any>(
    val selectedItem: T,
    val viewport: WebtoonSplitScrollHostViewportSnapshot<T>,
) {
    internal data class Restore<T : Any>(
        val selection: WebtoonSplitScrollHostRestore<T>,
        val viewportState: WebtoonSplitScrollHostState,
    )

    companion object {
        /**
         * Capture only when selection resolves into a real page or boundary in
         * the plan. A removed/unknown selection must use the reader's fallback.
         */
        fun <T : Any> capture(
            plan: WebtoonSplitScrollPlan<T>,
            selectedItem: T?,
            state: WebtoonSplitScrollHostState,
        ): WebtoonSplitScrollHostRebuildSnapshot<T>? {
            selectedItem ?: return null
            val target = WebtoonSplitScrollRebuildTargetResolver.resolve(plan, selectedItem) ?: return null
            val viewport = WebtoonSplitScrollHostViewportSnapshot.capture(plan, target.segmentIndex, state)
                ?: return null
            return WebtoonSplitScrollHostRebuildSnapshot(selectedItem, viewport)
        }

        /**
         * Resolve both pieces against one fresh rebuild. If the selected identity
         * or its containing segment changed, reject the pair rather than applying
         * a valid-looking selection with unrelated/stale column offsets.
         */
        fun <T : Any> restore(
            rebuild: WebtoonSplitScrollRebuildTargetResolver.Rebuild<T>,
            snapshot: WebtoonSplitScrollHostRebuildSnapshot<T>,
        ): Restore<T>? {
            if (rebuild.target?.item !== snapshot.selectedItem) return null
            val selection = WebtoonSplitScrollHostRestore.from(rebuild) ?: return null
            val viewportSegment = WebtoonSplitScrollHostViewportSnapshot.resolveSegmentIndex(
                rebuild.plan,
                snapshot.viewport,
            ) ?: return null
            if (selection.segmentIndex != viewportSegment) return null
            val viewportState = WebtoonSplitScrollHostViewportSnapshot.restore(
                rebuild.plan,
                snapshot.viewport,
            ) ?: return null
            return Restore(selection, viewportState)
        }
    }
}
