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
            isPortraitPage: (ReaderPage) -> Boolean,
            firstPageSingle: Boolean = false,
        ): SpreadAdapterProjection<Any> = build(
            adapterItems = adapterItems,
            selectedAdapterIndex = selectedAdapterIndex,
            isBoundary = { it is ChapterTransition },
            canPair = { item -> item is ReaderPage && item !is InsertPage && isPortraitPage(item) },
            firstPageSingle = firstPageSingle,
        )

        fun <T> build(
            adapterItems: List<T>,
            selectedAdapterIndex: Int,
            isBoundary: (T) -> Boolean = { false },
            canPair: (T) -> Boolean = { true },
            firstPageSingle: Boolean = false,
        ): SpreadAdapterProjection<T> {
            val units = SpreadAdapterItemMapper.map(
                items = adapterItems,
                isBoundary = isBoundary,
                canPair = canPair,
                firstPageSingle = firstPageSingle,
            )
            return SpreadAdapterProjection(
                units = units.toList(),
                selectedAnchor = SpreadAdapterNavigationMap.anchorForAdapter(units, selectedAdapterIndex),
                sourceSnapshot = adapterItems.toList(),
            )
        }
    }
}
