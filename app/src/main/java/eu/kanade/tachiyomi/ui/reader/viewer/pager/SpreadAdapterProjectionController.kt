package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/**
 * Owns the derived Spread projection for an unchanged effective pager sequence.
 *
 * The controller never mutates the pager adapter. It can safely be refreshed when
 * decoded image dimensions arrive, while retaining the active source item by identity.
 * A source-sequence change invalidates the snapshot and requires an explicit rebuild.
 */
internal class SpreadAdapterProjectionController {

    private var projection: SpreadAdapterProjection<Any>? = null
    private var sourceSnapshot: List<Any> = emptyList()

    val current: SpreadAdapterProjection<Any>?
        get() = projection

    fun rebuild(
        adapterItems: List<Any>,
        selectedItem: Any?,
        isPortraitPage: (ReaderPage) -> Boolean?,
        firstPageSingle: Boolean = false,
    ): SpreadAdapterProjection<Any> {
        val next = SpreadAdapterProjection.buildReaderPagesAnchoredToItem(
            adapterItems = adapterItems,
            selectedItem = selectedItem,
            isPortraitPage = isPortraitPage,
            firstPageSingle = firstPageSingle,
        )
        sourceSnapshot = adapterItems.toList()
        projection = next
        return next
    }

    /**
     * Refresh only if the effective adapter sequence is still the one this controller
     * projected. This prevents a late image-decode callback from reviving stale pages
     * after a chapter or split-page update.
     */
    fun refreshIfSourceMatches(
        adapterItems: List<Any>,
        selectedItem: Any?,
        isPortraitPage: (ReaderPage) -> Boolean?,
        firstPageSingle: Boolean = false,
    ): SpreadAdapterProjection<Any>? {
        if (!matchesSource(adapterItems)) return null
        return rebuild(adapterItems, selectedItem, isPortraitPage, firstPageSingle)
    }

    fun matchesSource(adapterItems: List<Any>): Boolean =
        adapterItems.size == sourceSnapshot.size &&
            adapterItems.indices.all { adapterItems[it] === sourceSnapshot[it] }

    fun clear() {
        projection = null
        sourceSnapshot = emptyList()
    }
}
