package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/**
 * Immutable derived snapshot for a spread-capable adapter.
 *
 * This projection deliberately does not own Android views or mutate the source adapter sequence.
 * It can be rebuilt after chapter, split-page, or cleanup updates.
 */
internal data class SpreadAdapterProjection<T>(
    val units: List<SpreadAdapterItemMapper.Unit<T>>,
    val selectedAnchor: SpreadAdapterNavigationMap.Anchor?,
    private val sourceSnapshot: List<T>,
) {
    /** Identity-based guard against reusing a projection after adapter mutation. */
    fun matchesSource(adapterItems: List<T>): Boolean =
        adapterItems.size == sourceSnapshot.size &&
            adapterItems.indices.all { adapterItems[it] === sourceSnapshot[it] }

    companion object {
        /**
         * Build a reader-aware projection. Chapter transitions and inserted split pages
         * are always standalone. Only adjacent regular pages with known portrait geometry
         * may be paired; unknown dimensions remain standalone.
         */
        fun buildReaderPages(
            adapterItems: List<Any>,
            selectedAdapterIndex: Int,
            isPortraitPage: (ReaderPage) -> Boolean?,
            firstPageSingle: Boolean = false,
            firstPageIndex: Int? = null,
        ): SpreadAdapterProjection<Any> = build(
            adapterItems = adapterItems,
            selectedAdapterIndex = selectedAdapterIndex,
            isBoundary = { it is ChapterTransition },
            canPair = { item -> item is ReaderPage && item !is InsertPage && isPortraitPage(item) == true },
            canPairTogether = { first, second ->
                val firstPage = first as? ReaderPage
                val secondPage = second as? ReaderPage
                firstPage != null && secondPage != null && sameChapterIfAvailable(firstPage, secondPage)
            },
            firstPageSingle = firstPageSingle,
            // A missing chapter cover must not fall back to source index zero: that
            // position can be a previous-chapter page or a transition barrier.
            firstPageIndex = firstPageIndex ?: if (firstPageSingle) -1 else null,
        )

        /**
         * Rebuild a reader projection around the active source item rather than a display
         * position. This is intended for orientation-driven refreshes: pairing may change
         * the display index, but the selected ReaderPage identity remains stable.
         */
        fun buildReaderPagesAnchoredToItem(
            adapterItems: List<Any>,
            selectedItem: Any?,
            isPortraitPage: (ReaderPage) -> Boolean?,
            firstPageSingle: Boolean = false,
            firstPageIndex: Int? = null,
        ): SpreadAdapterProjection<Any> {
            val selectedIndex = if (selectedItem == null) {
                -1
            } else {
                adapterItems.indexOfFirst { it === selectedItem }
            }
            return buildReaderPages(
                adapterItems = adapterItems,
                selectedAdapterIndex = selectedIndex,
                isPortraitPage = isPortraitPage,
                firstPageSingle = firstPageSingle,
                firstPageIndex = firstPageIndex,
            )
        }

        private fun sameChapterIfAvailable(first: ReaderPage, second: ReaderPage): Boolean {
            val firstChapter = runCatching { first.chapter }.getOrNull()
            val secondChapter = runCatching { second.chapter }.getOrNull()
            // ReaderPage instances created without a chapter occur in projection-only tests.
            // Live reader pages always have chapters, and must never pair across chapters.
            return firstChapter == null || secondChapter == null || firstChapter == secondChapter
        }

        fun <T> build(
            adapterItems: List<T>,
            selectedAdapterIndex: Int,
            isBoundary: (T) -> Boolean = { false },
            canPair: (T) -> Boolean = { true },
            canPairTogether: (T, T) -> Boolean = { _, _ -> true },
            firstPageSingle: Boolean = false,
            firstPageIndex: Int? = null,
        ): SpreadAdapterProjection<T> {
            val units = SpreadAdapterItemMapper.map(
                items = adapterItems,
                isBoundary = isBoundary,
                canPair = canPair,
                canPairTogether = canPairTogether,
                firstPageSingle = firstPageSingle,
                firstPageIndex = firstPageIndex,
            )
            return SpreadAdapterProjection(
                units = units.toList(),
                selectedAnchor = SpreadAdapterNavigationMap.anchorForAdapter(units, selectedAdapterIndex),
                sourceSnapshot = adapterItems.toList(),
            )
        }
    }
}
