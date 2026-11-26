package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

/**
 * Owns a derived Spread snapshot for the effective continuous-reader sequence.
 *
 * Orientation can become known after a page holder decodes its image. Refreshes are
 * accepted only while the authoritative source list still contains the exact same
 * objects in the same order; late callbacks after chapter/split-page changes are ignored.
 */
internal class WebtoonSpreadPlanController<T> {

    private var sourceSnapshot: List<T> = emptyList()
    private var plan: WebtoonSpreadPlan<T>? = null

    val current: WebtoonSpreadPlan<T>?
        get() = plan

    fun rebuild(
        items: List<T>,
        isBoundary: (T) -> Boolean,
        isPortrait: (T) -> Boolean?,
        canPair: (T) -> Boolean = { true },
        firstPageSingle: Boolean = false,
        firstPageIndex: Int = 0,
    ): WebtoonSpreadPlan<T> {
        val next = WebtoonSpreadPlan.create(
            items = items,
            isBoundary = isBoundary,
            isPortrait = isPortrait,
            canPair = canPair,
            firstPageSingle = firstPageSingle,
            firstPageIndex = firstPageIndex,
        )
        sourceSnapshot = items.toList()
        plan = next
        return next
    }

    fun refreshIfSourceMatches(
        items: List<T>,
        isBoundary: (T) -> Boolean,
        isPortrait: (T) -> Boolean?,
        canPair: (T) -> Boolean = { true },
        firstPageSingle: Boolean = false,
        firstPageIndex: Int = 0,
    ): WebtoonSpreadPlan<T>? {
        if (!matchesSource(items)) return null
        return rebuild(items, isBoundary, isPortrait, canPair, firstPageSingle, firstPageIndex)
    }

    fun matchesSource(items: List<T>): Boolean =
        items.size == sourceSnapshot.size &&
            items.indices.all { items[it] === sourceSnapshot[it] }

    fun clear() {
        plan = null
        sourceSnapshot = emptyList()
    }
}
